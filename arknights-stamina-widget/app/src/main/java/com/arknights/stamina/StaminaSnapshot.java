package com.arknights.stamina;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 一次体力同步的快照（不含任何账号敏感信息，可明文持久化）。
 * 字段语义：
 *  - apRecoveryTs：森空岛接口返回的「理智回满时刻」Unix 秒；<=0 表示未知。
 *  - fetchedAt：本机拉取到该快照的时刻 Unix 秒。
 *  - 理智恢复速率固定 1 点 / 360 秒。
 */
public final class StaminaSnapshot {

    public static final long RECOVERY_INTERVAL_SEC = 360L; // 6 分钟 1 点

    public String doctorName = "";
    public int level;
    public int apCurrent;
    public int apMax;
    public long apRecoveryTs;   // 回满时间戳(秒)，0 表示未知
    public long fetchedAt;      // 拉取时间戳(秒)

    public StaminaSnapshot() {
    }

    /** 依据快照与当前时刻推算指定时间 t(秒) 的理智值。 */
    public int currentAt(long tSec) {
        if (apMax > 0 && apRecoveryTs > 0 && tSec >= apRecoveryTs) {
            return apMax;
        }
        int est = apCurrent;
        if (fetchedAt > 0 && tSec > fetchedAt) {
            est = apCurrent + (int) ((tSec - fetchedAt) / RECOVERY_INTERVAL_SEC);
        }
        if (apRecoveryTs > 0 && apMax > 0) {
            // 用回满时间做上限钳制，二者取保守值
            long need = (apRecoveryTs - tSec + RECOVERY_INTERVAL_SEC - 1) / RECOVERY_INTERVAL_SEC;
            int est2 = apMax - (int) Math.max(0, need);
            if (est2 < est) est = est2;
        }
        if (apMax > 0 && est > apMax) est = apMax;
        if (est < 0) est = 0;
        return est;
    }

    public int displayMax() {
        return apMax > 0 ? apMax : 135;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("name", doctorName);
            o.put("level", level);
            o.put("cur", apCurrent);
            o.put("max", apMax);
            o.put("rec", apRecoveryTs);
            o.put("at", fetchedAt);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static StaminaSnapshot fromJson(String s) {
        StaminaSnapshot r = new StaminaSnapshot();
        if (s == null || s.isEmpty()) return r;
        try {
            JSONObject o = new JSONObject(s);
            r.doctorName = o.optString("name");
            r.level = o.optInt("level");
            r.apCurrent = o.optInt("cur");
            r.apMax = o.optInt("max");
            r.apRecoveryTs = o.optLong("rec");
            r.fetchedAt = o.optLong("at");
        } catch (JSONException ignored) {
        }
        return r;
    }

    /** 把秒数格式化为“x 小时 y 分 / y 分 / 已回满”。 */
    public static String formatRemaining(long remainSec) {
        if (remainSec <= 0) return "已回满";
        long min = (remainSec + 59) / 60;
        if (min < 1) min = 1;
        long h = min / 60;
        long m = min % 60;
        if (h > 0) return h + " 小时 " + m + " 分";
        return m + " 分钟";
    }

    /** 将过去时长格式化为“已同步 x 分钟前”。 */
    public static String formatAgeSec(long sec) {
        if (sec < 60) return "刚刚";
        long min = sec / 60;
        if (min < 60) return min + " 分钟前";
        long h = min / 60;
        if (h < 24) return h + " 小时前";
        return (h / 24) + " 天前";
    }
}
