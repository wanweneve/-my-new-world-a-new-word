package com.arknights.stamina;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** 由快照渲染 2×2 小组件视图（纯框架 API）。 */
public final class WidgetRenderer {

    private WidgetRenderer() {
    }

    public static RemoteViews build(Context c) {
        Context app = c.getApplicationContext();
        Store store = Store.get(app);
        boolean bound = store.isBound();
        StaminaSnapshot snap = store.snapshot();
        long nowSec = System.currentTimeMillis() / 1000L;

        RemoteViews rv = new RemoteViews(app.getPackageName(), R.layout.widget_stamina);

        String title = store.getNick();
        if (title == null || title.isEmpty()) {
            title = app.getString(R.string.widget_label);
        }
        rv.setTextViewText(R.id.tv_title, title);

        int max = snap.displayMax();
        int cur;
        if (!bound || snap.fetchedAt <= 0) {
            cur = -1;
        } else {
            cur = snap.currentAt(nowSec);
        }

        if (cur >= 0) {
            rv.setTextViewText(R.id.tv_current, String.valueOf(cur));
            rv.setTextViewText(R.id.tv_max, "/" + snap.displayMax());
            rv.setProgressBar(R.id.pb, max, cur, false);
        } else {
            rv.setTextViewText(R.id.tv_current, "--");
            rv.setTextViewText(R.id.tv_max, "");
            rv.setProgressBar(R.id.pb, max, 0, false);
        }

        rv.setTextViewText(R.id.tv_status, statusLine(app, store, snap, nowSec, cur));

        // 点击整卡 → 打开应用（进入即自动刷新）
        Intent open = new Intent(app, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                app, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widget_root, pi);
        return rv;
    }

    private static String statusLine(Context app, Store store, StaminaSnapshot snap, long nowSec, int cur) {
        String err = store.lastError();
        if (!store.isBound()) {
            return app.getString(R.string.not_bound);
        }
        if (snap.fetchedAt <= 0) {
            return err != null ? "同步失败：" + err : app.getString(R.string.syncing);
        }
        long ageSec = nowSec - snap.fetchedAt;
        if (snap.apRecoveryTs > 0) {
            long remain = snap.apRecoveryTs - nowSec;
            if (remain > 0) {
                return "回满还需 " + StaminaSnapshot.formatRemaining(remain) + " · " + StaminaSnapshot.formatAgeSec(ageSec);
            }
            return "已回满 · " + StaminaSnapshot.formatAgeSec(ageSec);
        }
        return "理智 " + cur + " · " + StaminaSnapshot.formatAgeSec(ageSec);
    }

    public static void updateAll(Context c) {
        Context app = c.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(app, StaminaWidgetProvider.class));
        if (ids.length == 0) return;
        RemoteViews rv = build(app);
        for (int id : ids) {
            mgr.updateAppWidget(id, rv);
        }
    }
}
