package com.arknights.stamina;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** 桌面 2×2 体力小组件。 */
public class StaminaWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // 先用缓存立即渲染，再异步拉最新数据
        WidgetRenderer.updateAll(context);
        SyncEngine.fireAndForget(context);
    }

    @Override
    public void onEnabled(Context context) {
        SyncScheduler.schedule(context);
        SyncEngine.fireAndForget(context);
    }

    @Override
    public void onDisabled(Context context) {
        SyncScheduler.cancel(context);
    }
}
