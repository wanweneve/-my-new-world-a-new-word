package com.arknights.stamina;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** 自管轮询闹钟：周期触发 SyncAlarmReceiver 做静默同步（间隔可在 App 内调整，默认 15 分钟）。 */
public final class SyncScheduler {

    private static final int REQ = 0x5A71;

    private SyncScheduler() {
    }

    public static void schedule(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long interval = Store.get(app).getIntervalMs();
        Intent i = new Intent(app, SyncAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                app, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval, interval, pi);
    }

    public static void cancel(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(app, SyncAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                app, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        pi.cancel();
    }
}
