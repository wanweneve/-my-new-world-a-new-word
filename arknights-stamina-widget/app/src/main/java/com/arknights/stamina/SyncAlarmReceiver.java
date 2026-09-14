package com.arknights.stamina;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 周期闹钟目标：静默同步一次体力并刷新小组件。 */
public class SyncAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SyncEngine.fireAndForget(context);
    }
}
