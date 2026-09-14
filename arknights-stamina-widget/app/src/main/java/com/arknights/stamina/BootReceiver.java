package com.arknights.stamina;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机后重排同步闹钟（若已绑定账号则顺带同步一次）。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Store store = Store.get(context);
        if (!store.isBound()) return;
        SyncScheduler.schedule(context);
        SyncEngine.fireAndForget(context);
    }
}
