package com.arknights.stamina;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.arknights.stamina.skland.SklandClient;
import com.arknights.stamina.skland.SklandException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ak-main-io");
        t.setDaemon(true);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final StringBuilder countdown = new StringBuilder();

    private TextView tvStatus, tvResult;
    private EditText etPhone, etCode, etPassword, etPaste;
    private Button btnSendCode, btnBind, btnPwdBind, btnUsePaste, btnRefresh, btnUnbind;
    private boolean opRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvStatus = findViewById(R.id.tvStatus);
        tvResult = findViewById(R.id.tvResult);
        etPhone = findViewById(R.id.etPhone);
        etCode = findViewById(R.id.etCode);
        etPassword = findViewById(R.id.etPassword);
        etPaste = findViewById(R.id.etPaste);
        btnSendCode = findViewById(R.id.btnSendCode);
        btnBind = findViewById(R.id.btnBind);
        btnPwdBind = findViewById(R.id.btnPwdBind);
        btnUsePaste = findViewById(R.id.btnUsePaste);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnUnbind = findViewById(R.id.btnUnbind);

        btnSendCode.setOnClickListener(v -> sendSms());
        btnBind.setOnClickListener(v -> bindBySms());
        btnPwdBind.setOnClickListener(v -> bindByPassword());
        btnUsePaste.setOnClickListener(v -> bindByPaste());
        btnRefresh.setOnClickListener(v -> refreshNow());
        btnUnbind.setOnClickListener(v -> unbind());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
        Store s = Store.get(this);
        if (s.isBound()) {
            // 进入即静默刷新（内部有 20s 节流）
            SyncEngine.run(this, (ok, msg) -> {
                if (ok) refreshUi();
            });
        }
    }

    // ---------------- UI ----------------

    private void refreshUi() {
        Store s = Store.get(this);
        if (s.isBound()) {
            String nick = s.getNick();
            tvStatus.setText("已绑定：" + (nick.isEmpty() ? s.getUid() : nick)
                    + "（" + s.getUid() + "）\n凭证仅加密存于本机");
            StaminaSnapshot snap = s.snapshot();
            if (snap.fetchedAt > 0) {
                long now = System.currentTimeMillis() / 1000L;
                int cur = snap.currentAt(now);
                StringBuilder sb = new StringBuilder("体力 ").append(cur).append('/').append(snap.displayMax());
                if (snap.apRecoveryTs > 0) {
                    long remain = snap.apRecoveryTs - now;
                    sb.append(remain > 0
                            ? "，回满还需 " + StaminaSnapshot.formatRemaining(remain)
                            : "，已回满");
                }
                sb.append("　同步于 ").append(StaminaSnapshot.formatAgeSec(Math.max(0, now - snap.fetchedAt)));
                tvResult.setText(sb.toString());
            } else {
                tvResult.setText("尚无数据，点「立即刷新体力」拉取。");
            }
            btnRefresh.setEnabled(true);
            btnUnbind.setEnabled(true);
            btnSendCode.setEnabled(false);
            btnBind.setEnabled(false);
            btnPwdBind.setEnabled(false);
            btnUsePaste.setEnabled(false);
            etPhone.setEnabled(false);
            etCode.setEnabled(false);
            etPassword.setEnabled(false);
            etPaste.setEnabled(false);
        } else {
            tvStatus.setText(getString(R.string.not_bound));
            tvResult.setText("");
            btnRefresh.setEnabled(false);
            btnUnbind.setEnabled(false);
            btnSendCode.setEnabled(true);
            btnBind.setEnabled(true);
            btnPwdBind.setEnabled(true);
            btnUsePaste.setEnabled(true);
            etPhone.setEnabled(true);
            etCode.setEnabled(true);
            etPassword.setEnabled(true);
            etPaste.setEnabled(true);
        }
    }

    private void refreshNow() {
        tvResult.setText("同步中…");
        SyncEngine.run(this, (ok, msg) -> {
            if (!ok) {
                tvResult.setText("同步失败：" + msg);
                refreshUi();
                return;
            }
            refreshUi();
            toast("已刷新");
        });
    }

    private void unbind() {
        Store.get(this).unbind();
        WidgetRenderer.updateAll(this);
        refreshUi();
        toast("已解绑，本地凭证已清除");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void setOpRunning(boolean running) {
        opRunning = running;
        boolean bound = Store.get(this).isBound();
        btnBind.setEnabled(!running && !bound);
        btnSendCode.setEnabled(!running && !bound);
        btnPwdBind.setEnabled(!running && !bound);
        btnUsePaste.setEnabled(!running && !bound);
    }

    private void post(Runnable r) {
        main.post(r);
    }

    // ---------------- 登录/绑定 ----------------

    private void sendSms() {
        if (opRunning) return;
        String phone = etPhone.getText().toString().trim();
        if (!phone.matches("1\\d{10}")) {
            toast("请输入 11 位大陆手机号");
            return;
        }
        setOpRunning(true);
        btnSendCode.setText("发送中…");
        IO.execute(() -> {
            try {
                SklandClient.requestSmsCode(MainActivity.this, phone);
                post(() -> {
                    toast("验证码已发送，请注意查收");
                    countdown(60);
                });
            } catch (SklandException e) {
                post(() -> toast("发送失败：" + e.getMessage()));
            } finally {
                post(() -> {
                    setOpRunning(false);
                    if (!Store.get(this).isBound()) btnSendCode.setText("获取验证码");
                });
            }
        });
    }

    private void countdown(int sec) {
        countdown.setLength(0);
        countdown.append(sec);
        tickCountdown();
    }

    private void tickCountdown() {
        if (countdown.length() == 0) return;
        int s = Integer.parseInt(countdown.toString());
        if (s <= 0) {
            btnSendCode.setText("获取验证码");
            btnSendCode.setEnabled(true);
            countdown.setLength(0);
            return;
        }
        btnSendCode.setText("重新获取(" + s + "s)");
        btnSendCode.setEnabled(false);
        countdown.setLength(0);
        countdown.append(s - 1);
        main.postDelayed(this::tickCountdown, 1000);
    }

    private void bindBySms() {
        if (opRunning) return;
        String phone = etPhone.getText().toString().trim();
        String code = etCode.getText().toString().trim();
        if (!phone.matches("1\\d{10}") || code.length() < 4) {
            toast("请填写手机号与短信验证码");
            return;
        }
        setOpRunning(true);
        btnBind.setText("绑定中…");
        IO.execute(() -> {
            try {
                SklandClient.loginByPhoneCode(this, phone, code);
                post(() -> {
                    toast("绑定成功");
                    SyncScheduler.schedule(this);
                    refreshUi();
                    refreshNow();
                });
            } catch (SklandException e) {
                post(() -> toast("绑定失败：" + e.getMessage()));
            } finally {
                post(() -> {
                    setOpRunning(false);
                    btnBind.setText("登录并绑定");
                });
            }
        });
    }

    private void bindByPassword() {
        if (opRunning) return;
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (!phone.matches("1\\d{10}") || password.isEmpty()) {
            toast("请填写手机号与登录密码");
            return;
        }
        setOpRunning(true);
        btnPwdBind.setText("登录中…");
        IO.execute(() -> {
            try {
                SklandClient.loginByPhonePassword(this, phone, password);
                post(() -> {
                    toast("绑定成功（密码未保存）");
                    SyncScheduler.schedule(this);
                    refreshUi();
                    refreshNow();
                });
            } catch (SklandException e) {
                post(() -> toast("登录失败：" + e.getMessage()));
            } finally {
                post(() -> {
                    etPassword.setText("");
                    setOpRunning(false);
                    btnPwdBind.setText("密码登录并绑定");
                });
            }
        });
    }

    private void bindByPaste() {
        if (opRunning) return;
        final String input = etPaste.getText().toString().trim();
        if (input.isEmpty()) {
            toast("请先粘贴凭证");
            return;
        }
        setOpRunning(true);
        btnUsePaste.setText("校验中…");
        IO.execute(() -> {
            try {
                SklandClient.bindWithCredential(this, input);
                post(() -> {
                    toast("绑定成功");
                    SyncScheduler.schedule(this);
                    refreshUi();
                    refreshNow();
                });
            } catch (SklandException e) {
                post(() -> toast("绑定失败：" + e.getMessage()));
            } finally {
                post(() -> {
                    setOpRunning(false);
                    btnUsePaste.setText("使用此凭证");
                });
            }
        });
    }
}
