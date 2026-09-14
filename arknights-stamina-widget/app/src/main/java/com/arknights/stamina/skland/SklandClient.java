package com.arknights.stamina.skland;

import android.content.Context;

import com.arknights.stamina.StaminaSnapshot;
import com.arknights.stamina.Store;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 森空岛(Skland) 接口客户端 —— 协议移植自 skland-kit@0.3.5（MIT, author enpitsulin），
 * 详见 THIRD_PARTY_NOTICES.md 与本文件头注释。
 *
 * 凭证链：phone+短信码 → 鹰角 token → OAuth code → 森空岛 cred(+token)。
 * 签名：sign = md5( hmac_sha256(token, pathname+query+body+timestamp+JSON{platform,timestamp,dId,vName}) )
 *       HMAC 密钥是 signIn 返回的 token（不是 cred），cred 只进 header。
 * 体力：GET /api/v1/game/player/info?uid=… → data.status.ap{current,max?,completeRecoveryTime}。
 */
public final class SklandClient {

    public static final String UA_SK =
            "Mozilla/5.0 (Linux; Android 12; SM-A5560 Build/V417IR; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Version/4.0 Chrome/101.0.4951.61 Safari/537.36; SKLand/1.52.1";
    public static final String UA_WEB =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/129.0.0.0 Safari/537.36";
    private static final String HG_BASE = "https://as.hypergryph.com";
    private static final String ZONAI_BASE = "https://zonai.skland.com";
    private static final String APP_CODE = "4ca99fa6b56cc2ba";
    private static final long TS_OFFSET_MS = 2000L;
    private static final String PLATFORM = "3";
    private static final String VNAME = "1.0.0";

    private SklandClient() {
    }

    // ================= 对外入口 =================

    /** 发送登录验证码（鹰角账号手机号）。 */
    public static void requestSmsCode(Context ctx, String phone) throws SklandException {
        JSONObject body = new JSONObject();
        try {
            body.put("phone", phone);
            body.put("type", 2);
        } catch (JSONException ignored) {
        }
        Map<String, String> h = hgHeaders(ctx);
        JSONObject j = postJson(HG_BASE + "/general/v1/send_phone_code", body, h);
        int status = j.optInt("status", 0);
        if (status != 0) {
            throw new SklandException("发送失败：" + msg(j, "状态码 " + status));
        }
    }

    /** 短信码登录：完整走到森空岛 cred 并绑定默认方舟角色。 */
    public static void loginByPhoneCode(Context ctx, String phone, String code) throws SklandException {
        String token = tokenByPhoneCode(ctx, phone, code);
        finishBindByHgToken(ctx, token);
    }

    /** 账号+密码登录：密码仅在此一次性换取鹰角 token，之后不保存、不落盘。 */
    public static void loginByPhonePassword(Context ctx, String phone, String password) throws SklandException {
        String token = tokenByPhonePassword(ctx, phone, password);
        finishBindByHgToken(ctx, token);
    }

    /** 粘贴凭证绑定：接受「鹰角 token」或网页登录态 JSON（web-api 返回的 {data:{content}}）。 */
    public static void bindWithCredential(Context ctx, String raw) throws SklandException {
        String token = parseOAuthToken(raw);
        if (token.isEmpty()) {
            throw new SklandException("未识别到有效凭证。请粘贴鹰角 token（网页登录后抓包 web-api 的 data.content，见 README）。");
        }
        finishBindByHgToken(ctx, token);
    }

    /** 用已存凭证同步最新体力快照。 */
    public static StaminaSnapshot refresh(Context ctx) throws SklandException {
        Store s = Store.get(ctx);
        if (!s.isBound()) throw new SklandException("尚未绑定账号");
        String uid = s.getUid();
        if (uid.isEmpty()) throw new SklandException("绑定信息不完整，请重新绑定");

        Map<String, String> q = new LinkedHashMap<>();
        q.put("uid", uid);
        JSONObject data = doSigned(ctx, "/api/v1/game/player/info", q, null);

        JSONObject status = data.optJSONObject("status");
        if (status == null) throw new SklandException("接口响应缺少 status 字段");
        JSONObject ap = status.optJSONObject("ap");
        if (ap == null) throw new SklandException("接口响应缺少体力字段(ap)，可能角色/接口已变化");

        int cur = ap.optInt("current", -1);
        if (cur < 0) throw new SklandException("接口未返回体力数值");
        int max = ap.optInt("max", 0);
        String name = status.optString("name", "");
        int level = status.optInt("level", 0);
        if (max <= 0) {
            // 上限未返回时按资料站规则估算：82 + 等级，封顶 135
            max = level > 0 ? Math.min(135, 82 + level) : 0;
        }
        if (max > 0 && cur > max) cur = max;

        StaminaSnapshot snap = new StaminaSnapshot();
        snap.doctorName = name;
        snap.level = level;
        snap.apCurrent = cur;
        snap.apMax = max;
        long rec = ap.optLong("completeRecoveryTime", 0);
        snap.apRecoveryTs = rec > 0 ? rec : 0;
        snap.fetchedAt = System.currentTimeMillis() / 1000L;

        // 昵称回填（billing 与 info 的 name 一致）
        if (!name.isEmpty() && s.getNick().isEmpty()) {
            s.bind(s.getToken(), s.getCred(), s.getUid(), s.getChannel(), name);
        }
        return snap;
    }

    // ================= 凭证链 =================

    private static void finishBindByHgToken(Context ctx, String hgToken) throws SklandException {
        // token → OAuth code
        JSONObject grantBody = new JSONObject();
        try {
            grantBody.put("appCode", APP_CODE);
            grantBody.put("token", hgToken);
            grantBody.put("type", 0);
        } catch (JSONException ignored) {
        }
        JSONObject grant = postJson(HG_BASE + "/user/oauth2/v2/grant", grantBody, hgHeaders(ctx));
        JSONObject data0 = ensureHgOk(grant, "OAuth 授权");
        String code = data0.optString("code", "");
        if (code.isEmpty()) throw new SklandException("OAuth 授权未返回 code");

        // OAuth code → 森空岛 cred+token
        JSONObject credBody = new JSONObject();
        try {
            credBody.put("code", code);
            credBody.put("kind", 1);
        } catch (JSONException ignored) {
        }
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/json");
        h.put("user-agent", UA_WEB);
        h.put("referer", "https://www.skland.com/");
        h.put("origin", "https://www.skland.com");
        h.put("dId", DeviceProfile.getDid(ctx));
        h.put("platform", PLATFORM);
        h.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000L));
        h.put("vName", VNAME);
        JSONObject signIn = postJson(ZONAI_BASE + "/web/v1/user/auth/generate_cred_by_code", credBody, h);
        if (signIn.optInt("code", -1) != 0) {
            throw new SklandException("换取凭证失败：" + msg(signIn, "code " + signIn.optInt("code")));
        }
        JSONObject credData = signIn.optJSONObject("data");
        if (credData == null) throw new SklandException("换取凭证失败：响应缺少 data");
        String cred = credData.optString("cred", "");
        String token = credData.optString("token", "");
        if (cred.isEmpty() || token.isEmpty()) throw new SklandException("换取凭证失败：cred/token 为空");

        Store s = Store.get(ctx);
        s.bind(token, cred, "", "", "");

        // 取方舟角色绑定（默认官服角色）
        JSONObject charInfo = pickArkChar(bindingData(ctx));
        s.bind(token, cred, charInfo.optString("uid"), charInfo.optString("channelMasterId"), charInfo.optString("nickName"));
    }

    private static String tokenByPhoneCode(Context ctx, String phone, String code) throws SklandException {
        JSONObject body = new JSONObject();
        try {
            body.put("phone", phone);
            body.put("code", code);
        } catch (JSONException ignored) {
        }
        JSONObject j = postJson(HG_BASE + "/user/auth/v2/token_by_phone_code", body, hgHeaders(ctx));
        JSONObject data = ensureHgOk(j, "验证码登录");
        String token = data.optString("token", "");
        if (token.isEmpty()) throw new SklandException("验证码登录未返回 token");
        return token;
    }

    private static String tokenByPhonePassword(Context ctx, String phone, String password) throws SklandException {
        JSONObject body = new JSONObject();
        try {
            body.put("phone", phone);
            body.put("password", password);
        } catch (JSONException ignored) {
        }
        JSONObject j = postJson(HG_BASE + "/user/auth/v1/token_by_phone_password", body, hgHeaders(ctx));
        JSONObject data = ensureHgOk(j, "密码登录");
        String token = data.optString("token", "");
        if (token.isEmpty()) throw new SklandException("密码登录未返回 token");
        return token;
    }

    private static JSONObject bindingData(Context ctx) throws SklandException {
        return doSigned(ctx, "/api/v1/game/player/binding", null, null);
    }

    private static JSONObject pickArkChar(JSONObject data) throws SklandException {
        JSONArray list = data.optJSONArray("list");
        if (list == null) throw new SklandException("绑定列表为空");
        for (int i = 0; i < list.length(); i++) {
            JSONObject app = list.optJSONObject(i);
            if (app == null || !"arknights".equals(app.optString("appCode"))) continue;
            JSONArray binds = app.optJSONArray("bindingList");
            if (binds == null || binds.length() == 0) continue;
            JSONObject best = null;
            for (int j = 0; j < binds.length(); j++) {
                JSONObject b = binds.optJSONObject(j);
                if (b == null || b.optBoolean("isDelete", false)) continue;
                if (b.optBoolean("isDefault", false)) {
                    best = b;
                    break;
                }
                if (best == null) best = b;
            }
            if (best == null) throw new SklandException("该账号未绑定《明日方舟》角色");
            JSONObject out = new JSONObject();
            try {
                out.put("uid", best.optString("uid"));
                out.put("channelMasterId", best.optString("channelMasterId"));
                out.put("nickName", best.optString("nickName"));
            } catch (JSONException ignored) {
            }
            return out;
        }
        throw new SklandException("该账号未绑定《明日方舟》角色");
    }

    private static JSONObject ensureHgOk(JSONObject j, String what) throws SklandException {
        int status = j.optInt("status", 0);
        if (status != 0) {
            throw new SklandException(what + "失败：" + msg(j, "状态码 " + status));
        }
        JSONObject data = j.optJSONObject("data");
        if (data == null) throw new SklandException(what + "失败：响应缺少 data");
        return data;
    }

    private static String msg(JSONObject j, String fallback) {
        String m = j.optString("msg", "");
        if (m.isEmpty()) m = j.optString("message", "");
        return m.isEmpty() ? fallback : m;
    }

    private static String parseOAuthToken(String raw) {
        String token = raw == null ? "" : raw.trim();
        if (token.isEmpty()) return "";
        if (!token.startsWith("{")) return token;
        try {
            JSONObject j = new JSONObject(token);
            JSONObject data = j.optJSONObject("data");
            if (data != null) {
                String content = data.optString("content", "");
                if (!content.isEmpty()) return content;
            }
            String t = j.optString("token", "");
            if (!t.isEmpty()) return t;
        } catch (JSONException ignored) {
        }
        return token;
    }

    private static Map<String, String> hgHeaders(Context ctx) throws SklandException {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("user-agent", UA_SK);
        h.put("dId", DeviceProfile.getDid(ctx));
        h.put("x-requested-with", "com.hypergryph.skland");
        return h;
    }

    // ================= 签名请求 =================

    private static JSONObject doSigned(Context ctx, String path, Map<String, String> query, JSONObject jsonBody)
            throws SklandException {
        Store s = Store.get(ctx);
        String cred = s.getCred();
        String token = s.getToken();
        if (cred.isEmpty() || token.isEmpty()) {
            throw new SklandException("凭证缺失或已失效，请在 App 内重新绑定");
        }
        String did = DeviceProfile.getDid(ctx);
        String ts = String.valueOf((System.currentTimeMillis() - TS_OFFSET_MS) / 1000L);
        String queryStr = encodeQuery(query);
        String bodySeg = jsonBody == null ? "" : jsonBody.toString();
        String sigJson = "{\"platform\":\"" + PLATFORM
                + "\",\"timestamp\":\"" + ts
                + "\",\"dId\":\"" + did
                + "\",\"vName\":\"" + VNAME + "\"}";
        String message = path + queryStr + bodySeg + ts + sigJson;
        String hmac = hmacSha256Hex(token, message);
        String sign = DeviceProfile.md5Hex(hmac);

        Map<String, String> h = new LinkedHashMap<>();
        h.put("user-agent", UA_SK);
        h.put("connection", "close");
        h.put("x-requested-with", "com.hypergryph.skland");
        h.put("platform", PLATFORM);
        h.put("timestamp", ts);
        h.put("dId", did);
        h.put("vName", VNAME);
        h.put("sign", sign);
        h.put("cred", cred);

        String url = ZONAI_BASE + path + (queryStr.isEmpty() ? "" : "?" + queryStr);
        JSONObject j = jsonBody == null
                ? getJson(url, h)
                : postJson(url, jsonBody, h);
        int code = j.optInt("code", -999);
        if (code != 0) {
            String m = j.optString("message", "");
            String extra = (m.contains("登录") || m.contains("token") || m.contains("凭证")
                    || m.contains("cred") || m.contains("过期") || m.contains("无效"))
                    ? "（凭证可能已失效，请在 App 内重新绑定）" : "";
            throw new SklandException("接口错误 code=" + code
                    + (m.isEmpty() ? "" : "：" + m) + extra);
        }
        JSONObject data = j.optJSONObject("data");
        if (data == null) throw new SklandException("接口响应缺少 data");
        return data;
    }

    private static String encodeQuery(Map<String, String> query) throws SklandException {
        if (query == null || query.isEmpty()) return "";
        try {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : query.entrySet()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                        .append('=')
                        .append(URLEncoder.encode(e.getValue(), "UTF-8"));
            }
            return sb.toString();
        } catch (IOException e) {
            throw new SklandException("参数编码失败");
        }
    }

    private static String hmacSha256Hex(String key, String data) throws SklandException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format(java.util.Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new SklandException("签名初始化失败");
        }
    }

    // ================= HTTP =================

    public static JSONObject postJson(String url, JSONObject body, Map<String, String> headers)
            throws SklandException {
        String raw = httpRaw(url, "POST", body.toString(), headers);
        return parseJson(raw);
    }

    private static JSONObject getJson(String url, Map<String, String> headers) throws SklandException {
        String raw = httpRaw(url, "GET", null, headers);
        return parseJson(raw);
    }

    private static JSONObject parseJson(String raw) throws SklandException {
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            throw new SklandException("服务返回了无法解析的数据");
        }
    }

    private static String httpRaw(String url, String method, String bodyJson, Map<String, String> headers)
            throws SklandException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        c.setRequestProperty(e.getKey(), e.getValue());
                    }
                }
            }
            if (bodyJson != null) {
                boolean hasType = false;
                if (headers != null) {
                    for (String k : headers.keySet()) {
                        if (k != null && k.equalsIgnoreCase("Content-Type")) {
                            hasType = true;
                            break;
                        }
                    }
                }
                if (!hasType) c.setRequestProperty("Content-Type", "application/json");
                byte[] b = bodyJson.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(b.length);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(b);
                }
            }
            int httpCode = c.getResponseCode();
            if (httpCode < 200 || httpCode >= 300) {
                InputStream es = c.getErrorStream();
                String snippet = es == null ? "" : readAll(es, 200);
                throw new SklandException("网络响应异常 HTTP " + httpCode
                        + (snippet.isEmpty() ? "" : "：" + snippet));
            }
            String enc = c.getContentEncoding();
            try (InputStream in = c.getInputStream()) {
                InputStream is = in;
                if (enc != null && enc.contains("gzip")) {
                    is = new GZIPInputStream(in);
                }
                return readAll(is, 0);
            }
        } catch (SklandException e) {
            throw e;
        } catch (Exception e) {
            throw new SklandException("网络错误：" + e.getMessage());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readAll(InputStream in, int cap) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
            if (cap > 0 && bos.size() > cap) break;
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
