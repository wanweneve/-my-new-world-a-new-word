package com.arknights.stamina;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.arknights.stamina.skland.SklandClient;
import com.arknights.stamina.skland.SklandException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 体力同步编排：后台线程拉取 → 持久化 → 刷新小组件 → 回调主线程。 */
public final class SyncEngine {

    public interface Callback {
        /** ok=true 同步成功；ok=false 时 msg 为失败原因。 */
        void onDone(boolean ok, String msg);
    }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stamina-sync");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final long MIN_INTERVAL_MS = 20_000L; // 手动刷新最小间隔
    private static volatile long lastRunAt = 0;

    private SyncEngine() {
    }

    /** 静默同步（闹钟/小组件触发），不回调 UI。 */
    public static void fireAndForget(Context c) {
        run(c, null);
    }

    /** 显式同步，完成回调主线程（onDone ok=false 时 msg 为错误信息）。 */
    public static void run(Context c, Callback cb) {
        Context app = c.getApplicationContext();
        long now = System.currentTimeMillis();
        if (!BUSY.compareAndSet(false, true)) return;
        if (now - lastRunAt < MIN_INTERVAL_MS) {
            BUSY.set(false);
            if (cb != null) post(app, cb, true, "刚刚才同步过，稍等片刻");
            return;
        }
        lastRunAt = now;
        EXEC.execute(() -> {
            String err = null;
            boolean ok = true;
            try {
                Store store = Store.get(app);
                if (!store.isBound()) {
                    err = "尚未绑定账号";
                    ok = false;
                } else {
                    StaminaSnapshot snap = SklandClient.refresh(app);
                    store.setSnapshot(snap);
                    store.setLastError(null);
                    WidgetRenderer.updateAll(app);
                }
            } catch (SklandException e) {
                ok = false;
                err = e.getMessage();
                Store.get(app).setLastError(err);
                WidgetRenderer.updateAll(app);
            } catch (Exception e) {
                ok = false;
                err = "未知错误：" + e;
                Store.get(app).setLastError(err);
                WidgetRenderer.updateAll(app);
            } finally {
                BUSY.set(false);
            }
            final boolean fOk = ok;
            final String fErr = err;
            if (cb != null) {
                post(app, cb, fOk, fErr == null ? null : fErr);
            }
        });
    }

    private static void post(Context app, Callback cb, boolean ok, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                cb.onDone(ok, msg);
            } catch (Exception ignored) {
            }
        });
    }
}
