package com.arknights.stamina.skland;

import android.util.Base64;

import com.arknights.stamina.Store;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 「数美」设备指纹 → dId（"B"+deviceId）。
 * 逐字符移植自 skland-kit@0.3.5（MIT, author enpitsulin）的 getDid 流程，
 * 用于生成森空岛请求签名头 dId。见 THIRD_PARTY_NOTICES.md。
 */
public final class DeviceProfile {

    private static final String ORG = "UWXspnCCJN4sfYlNfqps";
    private static final String APP_ID = "default";
    private static final String SM_PUBLIC_KEY_B64 =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCmxMNr7n8ZeT0tE1R9j/mPixoinPkeM+k4VGIn/s0k7N5rJAfnZ0eMER+QhwFvshzo0LNmeUkpR8uIlU/GEVr8mN28sKmwd2gpygqj0ePnBmOW4v0ZVwbSYK+izkhVFk2V/doLoMbWy6b+UnA8mkjvg0iYWRByfRsK2gdl7llqCwIDAQAB";
    private static final String SM_API = "https://fp-it.portal101.cn/deviceprofile/v4";
    private static final byte[] AES_IV = "0102030405060708".getBytes(StandardCharsets.US_ASCII);

    // DES_RULE：原字段名 -> (是否加密, DES 密钥, 混淆名)
    private static final Map<String, String[]> RULES = new LinkedHashMap<>();

    static {
        RULES.put("appId", new String[]{"1", "uy7mzc4h", "xx"});
        RULES.put("box", new String[]{"0", "", "jf"});
        RULES.put("canvas", new String[]{"1", "snrn887t", "yk"});
        RULES.put("clientSize", new String[]{"1", "cpmjjgsu", "zx"});
        RULES.put("organization", new String[]{"1", "78moqjfc", "dp"});
        RULES.put("os", new String[]{"1", "je6vk6t4", "pj"});
        RULES.put("platform", new String[]{"1", "pakxhcd2", "gm"});
        RULES.put("plugins", new String[]{"1", "v51m3pzl", "kq"});
        RULES.put("pmf", new String[]{"1", "2mdeslu3", "vw"});
        RULES.put("protocol", new String[]{"0", "", "protocol"});
        RULES.put("referer", new String[]{"1", "y7bmrjlc", "ab"});
        RULES.put("res", new String[]{"1", "whxqm2a7", "hf"});
        RULES.put("rtype", new String[]{"1", "x8o2h2bl", "lo"});
        RULES.put("sdkver", new String[]{"1", "9q3dcxp2", "sc"});
        RULES.put("status", new String[]{"1", "2jbrxxw4", "an"});
        RULES.put("subVersion", new String[]{"1", "eo3i2puh", "ns"});
        RULES.put("svm", new String[]{"1", "fzj3kaeh", "qr"});
        RULES.put("time", new String[]{"1", "q2t3odsk", "nb"});
        RULES.put("timezone", new String[]{"1", "1uv05lj5", "as"});
        RULES.put("tn", new String[]{"1", "x9nzj1bp", "py"});
        RULES.put("trees", new String[]{"1", "acfs0xo4", "pi"});
        RULES.put("ua", new String[]{"1", "k92crp1t", "bj"});
        RULES.put("url", new String[]{"1", "y95hjkoo", "cf"});
        RULES.put("version", new String[]{"0", "", "version"});
        RULES.put("vpw", new String[]{"1", "r9924ab5", "ca"});
    }

    private DeviceProfile() {
    }

    public static synchronized String getDid(android.content.Context ctx) throws SklandException {
        Store store = Store.get(ctx);
        String cached = store.getDid();
        if (cached != null && !cached.isEmpty()) return cached;

        try {
            String uid = UUID.randomUUID().toString();
            String priId = md5Hex(uid).substring(0, 16);
            String ep = rsaPkcs1B64(uid, Base64.decode(SM_PUBLIC_KEY_B64, Base64.DEFAULT));

            LinkedHashMap<String, Object> target = new LinkedHashMap<>();
            // BROWSER_ENV（skland-kit 内固定浏览器画像）
            target.put("plugins", "MicrosoftEdgePDFPluginPortableDocumentFormatinternal-pdf-viewer1,MicrosoftEdgePDFViewermhjfbmdgcfjbbpaeojofohoefgiehjai1");
            target.put("ua", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0");
            target.put("canvas", "259ffe69");
            target.put("timezone", -480);
            target.put("platform", "Win32");
            target.put("url", "https://www.skland.com/");
            target.put("referer", "");
            target.put("res", "1920_1080_24_1.25");
            target.put("clientSize", "0_0_1080_1920_1920_1080_1920_1080");
            target.put("status", "0011");
            // 动态字段
            target.put("vpw", UUID.randomUUID().toString());
            long now = System.currentTimeMillis();
            target.put("svm", now);
            target.put("trees", UUID.randomUUID().toString());
            target.put("pmf", now);
            target.put("protocol", 102);
            target.put("organization", ORG);
            target.put("appId", APP_ID);
            target.put("os", "web");
            target.put("version", "3.0.0");
            target.put("sdkver", "3.0.0");
            target.put("box", "");
            target.put("rtype", "all");
            target.put("smid", smId());
            target.put("subVersion", "1.0.0");
            target.put("time", 0);
            target.put("tn", md5Hex(getTn(target)));

            // 逐键按 DES_RULE 改名/加密
            LinkedHashMap<String, Object> bodyData = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : target.entrySet()) {
                String[] rule = RULES.get(e.getKey());
                String name = e.getKey();
                Object val = e.getValue();
                if (rule != null) {
                    name = rule[2];
                    if ("1".equals(rule[0])) {
                        val = desEcbB64(String.valueOf(val), rule[1]);
                    }
                }
                bodyData.put(name, val);
            }

            String pretty = prettyJson(bodyData);
            String gzB64 = gzipThenB64(pretty);
            String dataHex = aesCbcHex(gzB64, priId);

            LinkedHashMap<String, Object> outer = new LinkedHashMap<>();
            outer.put("appId", APP_ID);
            outer.put("compress", 2);
            outer.put("data", dataHex);
            outer.put("encode", 5);
            outer.put("ep", ep);
            outer.put("organization", ORG);
            outer.put("os", "web");

            org.json.JSONObject resp = SklandClient.postJson(SM_API, new org.json.JSONObject(outer), null);
            if (resp.optInt("code") != 1100) {
                throw new SklandException("设备指纹服务返回异常：" + resp.optString("message", String.valueOf(resp.optInt("code"))));
            }
            String deviceId = resp.optJSONObject("detail").optString("deviceId", "");
            if (deviceId.isEmpty()) throw new SklandException("设备指纹服务未返回 deviceId");
            String did = "B" + deviceId;
            store.setDid(did);
            return did;
        } catch (SklandException e) {
            throw e;
        } catch (Exception e) {
            throw new SklandException("获取设备指纹失败：" + e + "（需联网访问 fp-it.portal101.cn）");
        }
    }

    private static String smId() throws SklandException {
        String now = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date());
        String v = now + md5Hex(UUID.randomUUID().toString()) + "00";
        return v + md5Hex("smsk_web_" + v).substring(0, 14) + "0";
    }

    private static String getTn(Object obj) {
        if (obj instanceof Number) {
            long n = ((Number) obj).longValue();
            return String.valueOf(n * 10000L);
        }
        if (obj instanceof Map) {
            TreeMap<String, Object> sorted = new TreeMap<>((Map<String, Object>) obj);
            StringBuilder sb = new StringBuilder();
            for (Object v : sorted.values()) {
                sb.append(getTn(v));
            }
            return sb.toString();
        }
        return String.valueOf(obj);
    }

    private static String prettyJson(Object o) {
        String s = new org.json.JSONObject((Map<String, Object>) o).toString();
        return s.replace("\":\"", "\": \"").replace("\",\"", "\", \"");
    }

    private static String gzipThenB64(String s) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream gz = new GZIPOutputStream(bos);
        gz.write(s.getBytes(StandardCharsets.UTF_8));
        gz.close();
        byte[] out = bos.toByteArray();
        if (out.length > 9) out[9] = 19; // 对齐 JS CompressionStream 的 gzip OS 字节
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    private static String aesCbcHex(String data, String key) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.US_ASCII), "AES"),
                new IvParameterSpec(AES_IV));
        byte[] ct = c.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : ct) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }

    private static String desEcbB64(String message, String key) throws Exception {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        int pad = 8 - data.length % 8;
        byte[] padded = new byte[data.length + pad];
        System.arraycopy(data, 0, padded, 0, data.length);
        Cipher c = Cipher.getInstance("DES/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.US_ASCII), "DES"));
        return Base64.encodeToString(c.doFinal(padded), Base64.NO_WRAP);
    }

    private static String rsaPkcs1B64(String message, byte[] spkiDer) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        java.security.PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(spkiDer));
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        c.init(Cipher.ENCRYPT_MODE, pub);
        return Base64.encodeToString(c.doFinal(message.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }

    static String md5Hex(String s) throws SklandException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new SklandException("加密初始化失败");
        }
    }
}
