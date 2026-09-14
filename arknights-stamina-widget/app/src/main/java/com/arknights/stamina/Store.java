package com.arknights.stamina;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 本地存储：
 *  - token（森空岛签名密钥）/ cred（取权凭证）用 Android Keystore(AES/GCM) 加密落盘；
 *  - 设备指纹 dId、角色绑定信息、体力快照为明文（不含敏感信息）。
 * 纯框架 API，零第三方依赖。
 */
public final class Store {

    private static final String PREFS = "stamina_store";
    private static final String KEY_ALIAS = "com.arknights.stamina.cred.v1";
    private static final String P_BOUND = "bound";
    private static final String P_TOKEN_ENC = "token_enc";
    private static final String P_CRED_ENC = "cred_enc";
    private static final String P_UID = "uid";
    private static final String P_CHANNEL = "channel";
    private static final String P_NICK = "nick";
    private static final String P_SNAPSHOT = "snapshot_json";
    private static final String P_LAST_ERROR = "last_error";
    private static final String P_INTERVAL_MS = "interval_ms";
    private static final String P_DID = "did";

    private static final long DEFAULT_INTERVAL_MS = 15 * 60 * 1000L;

    private final SharedPreferences sp;

    private Store(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Store inst;

    public static synchronized Store get(Context c) {
        if (inst == null) inst = new Store(c.getApplicationContext());
        return inst;
    }

    /** 绑定需要 token（签名密钥）与 cred（取权凭证）同时存在。 */
    public boolean isBound() {
        return sp.getBoolean(P_BOUND, false)
                && !getCred().isEmpty()
                && !getToken().isEmpty();
    }

    /** 先存 token/cred，角色信息(binding)拿到后可再次调用补全 uid 等。 */
    public void bind(String token, String cred, String uid, String channel, String nick) {
        SharedPreferences.Editor e = sp.edit();
        e.putBoolean(P_BOUND, true);
        e.putString(P_TOKEN_ENC, encrypt(token));
        e.putString(P_CRED_ENC, encrypt(cred));
        e.putString(P_UID, uid == null ? "" : uid);
        e.putString(P_CHANNEL, channel == null ? "" : channel);
        e.putString(P_NICK, nick == null ? "" : nick);
        e.remove(P_SNAPSHOT);
        e.remove(P_LAST_ERROR);
        e.apply();
    }

    public void unbind() {
        SharedPreferences.Editor e = sp.edit();
        e.clear();
        e.apply();
    }

    public String getToken() {
        return decrypt(sp.getString(P_TOKEN_ENC, null));
    }

    public String getCred() {
        return decrypt(sp.getString(P_CRED_ENC, null));
    }

    public String getUid() {
        return sp.getString(P_UID, "");
    }

    public String getChannel() {
        return sp.getString(P_CHANNEL, "");
    }

    public String getNick() {
        return sp.getString(P_NICK, "");
    }

    public long getIntervalMs() {
        return sp.getLong(P_INTERVAL_MS, DEFAULT_INTERVAL_MS);
    }

    public void setIntervalMs(long ms) {
        sp.edit().putLong(P_INTERVAL_MS, ms).apply();
    }

    public String getDid() {
        return sp.getString(P_DID, "");
    }

    public void setDid(String did) {
        sp.edit().putString(P_DID, did).apply();
    }

    public void setSnapshot(StaminaSnapshot s) {
        sp.edit().putString(P_SNAPSHOT, s.toJson().toString()).apply();
    }

    public StaminaSnapshot snapshot() {
        return StaminaSnapshot.fromJson(sp.getString(P_SNAPSHOT, null));
    }

    public void setLastError(String msg) {
        SharedPreferences.Editor e = sp.edit();
        if (msg == null) e.remove(P_LAST_ERROR);
        else e.putString(P_LAST_ERROR, msg);
        e.apply();
    }

    public String lastError() {
        return sp.getString(P_LAST_ERROR, null);
    }

    // ---------------- Android Keystore AES/GCM ----------------

    private static SecretKey ensureKey() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            return kg.generateKey();
        } catch (Exception e) {
            return null;
        }
    }

    private static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        try {
            SecretKey key = ensureKey();
            if (key == null) return "";
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key);
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] iv = c.getIV();
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    private static String decrypt(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        try {
            SecretKey key = ensureKey();
            if (key == null) return "";
            byte[] all = Base64.decode(b64, Base64.NO_WRAP);
            byte[] iv = new byte[12];
            System.arraycopy(all, 0, iv, 0, iv.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] plain = c.doFinal(all, iv.length, all.length - iv.length);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
