package com.shiguangju;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Cordova 插件中的资源（R.layout / R.id / R.drawable）在构建时会合并到「应用」的 R 类，
// 应用包名为 com.shiguangju.app，因此必须 import 这个 R，而非 com.shiguangju.R。
import com.shiguangju.app.R;

    /** 获取启动当前 App 的 Intent（不硬编码 MainActivity，避免插件引用不到宿主 Activity） */
    private Intent getLaunchIntent() {
        Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (i == null) {
            // fallback：手动构造（极少走到）
            i = new Intent(Intent.ACTION_MAIN);
            i.setPackage(getPackageName());
        }
        return i;
    }

/**
 * 全局悬浮窗服务（跨应用置顶）。
 * - 使用 TYPE_APPLICATION_OVERLAY（Android 8.0+ 唯一合法跨应用悬浮窗类型）
 * - 绑定前台服务，降低被系统回收概率
 * - 与 App 计时器共享状态：通过 SharedPreferences 读写当前计时/类别
 */
public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindowService";
    private static final String CHANNEL_ID = "shiguangju_float";
    private static final String PREFS = "shiguangju_float";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_START = "start_ms";
    private static final String KEY_ELAPSED = "elapsed_ms";
    private static final String KEY_CAT = "category";
    private static final String KEY_CATS = "categories";

    private WindowManager wm;
    private View floatView;
    private View expandedView;
    private View collapsedView;
    private TextView tvTimer;
    private TextView tvCategory;
    private Button btnPlay;
    private boolean isCollapsed = false;
    private boolean attached = false;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        // Android 14+ 必须在 startForeground 时指定 foregroundServiceType，否则抛异常崩溃
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                android.app.ForegroundServiceType type = android.app.ForegroundServiceType.DATA_SYNC;
                startForegroundNotification(type);
            } catch (Throwable t) {
                Log.e(TAG, "startForeground(14+) failed", t);
                startForegroundNotificationLegacy();
            }
        } else {
            startForegroundNotificationLegacy();
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted");
            return;
        }
        buildFloatView();
    }

    private void startForegroundNotificationLegacy() {
        startForegroundNotification();
    }

    @SuppressWarnings("deprecation")
    private void startForegroundNotification(Object typeHint) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "拾光橘悬浮窗", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("保持悬浮窗与计时服务运行");
            ch.enableLights(false); ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        }
        Intent ni = getLaunchIntent();
        PendingIntent pi = PendingIntent.getActivity(this, 0, ni, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("拾光橘运行中")
                .setContentText("悬浮窗已开启，可跨应用计时")
                .setSmallIcon(getAppIconRes())
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && typeHint instanceof android.app.ForegroundServiceType) {
            startForeground(1001, n, (android.app.ForegroundServiceType) typeHint);
        } else {
            startForeground(1001, n);
        }
    }

    private void startForegroundNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "拾光橘悬浮窗", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("保持悬浮窗与计时服务运行");
            ch.enableLights(false); ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        }
        Intent ni = getLaunchIntent();
        PendingIntent pi = PendingIntent.getActivity(this, 0, ni, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("拾光橘运行中")
                .setContentText("悬浮窗已开启，可跨应用计时")
                .setSmallIcon(getAppIconRes())
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(1001, n);
    }

    private int getAppIconRes() {
        // 回退到系统默认图标，避免资源缺失导致崩溃
        return android.R.drawable.ic_dialog_info;
    }

    private void buildFloatView() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatView = View.inflate(this, R.layout.floating_window, null);

        expandedView = floatView.findViewById(R.id.expanded);
        collapsedView = floatView.findViewById(R.id.collapsed);
        tvTimer = floatView.findViewById(R.id.tvTimer);
        tvCategory = floatView.findViewById(R.id.tvCategory);
        btnPlay = floatView.findViewById(R.id.btnPlay);

        // 拖拽
        View dragHandle = floatView.findViewById(R.id.dragHandle);
        if (dragHandle != null) {
            dragHandle.setOnTouchListener(new DragTouchListener());
        }

        // 播放/暂停
        btnPlay.setOnClickListener(v -> toggleTimer());
        // 保存
        Button btnSave = floatView.findViewById(R.id.btnSave);
        if (btnSave != null) btnSave.setOnClickListener(v -> saveRecord());
        // 切换类别
        Button btnCat = floatView.findViewById(R.id.btnCategory);
        if (btnCat != null) btnCat.setOnClickListener(v -> cycleCategory());
        // 折叠
        Button btnCollapse = floatView.findViewById(R.id.btnCollapse);
        if (btnCollapse != null) btnCollapse.setOnClickListener(v -> collapse(true));
        // 展开（折叠胶囊点击）
        View cap = floatView.findViewById(R.id.collapsedCapsule);
        if (cap != null) cap.setOnClickListener(v -> collapse(false));
        // 隐藏
        Button btnHide = floatView.findViewById(R.id.btnHide);
        if (btnHide != null) btnHide.setOnClickListener(v -> hideFloat());

        // 恢复位置
        int x = getPrefs().getInt("pos_x", 0);
        int y = getPrefs().getInt("pos_y", 120);
        int w = getResources().getDisplayMetrics().widthPixels;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Math.max(0, Math.min(x, w - 80));
        lp.y = Math.max(0, y);
        lp.alpha = 0.96f;

        try {
            wm.addView(floatView, lp);
            attached = true;
        } catch (Exception e) {
            Log.e(TAG, "addView failed", e);
            Toast.makeText(this, "悬浮窗添加失败，请确认已开启「显示在其他应用上层」与「后台弹出界面」权限", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        refreshUi();
        startTicker();
    }

    /* ---------- 计时逻辑（与 App 通过 SharedPreferences 共享） ---------- */

    private android.content.SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void toggleTimer() {
        boolean running = getPrefs().getBoolean(KEY_RUNNING, false);
        long now = System.currentTimeMillis();
        if (running) {
            long start = getPrefs().getLong(KEY_START, now);
            long elapsed = getPrefs().getLong(KEY_ELAPSED, 0) + (now - start);
            getPrefs().edit().putBoolean(KEY_RUNNING, false).putLong(KEY_ELAPSED, elapsed).apply();
            btnPlay.setText("▶");
        } else {
            getPrefs().edit().putBoolean(KEY_RUNNING, true).putLong(KEY_START, now).apply();
            btnPlay.setText("⏸");
        }
    }

    private void saveRecord() {
        long elapsed = getPrefs().getLong(KEY_ELAPSED, 0);
        boolean running = getPrefs().getBoolean(KEY_RUNNING, false);
        long now = System.currentTimeMillis();
        if (running) {
            long start = getPrefs().getLong(KEY_START, now);
            elapsed = elapsed + (now - start);
            // 暂停后再保存
            getPrefs().edit().putBoolean(KEY_RUNNING, false).putLong(KEY_START, now).apply();
        }
        if (elapsed < 1000) {
            Toast.makeText(this, "时长太短，未保存", Toast.LENGTH_SHORT).show();
            return;
        }
        String cat = getPrefs().getString(KEY_CAT, "工作");
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        try {
            JSONArray arr = new JSONArray(getPrefs().getString("records_cache", "[]"));
            JSONObject r = new JSONObject();
            r.put("date", date);
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            r.put("start", iso.format(new Date(now - elapsed)));
            r.put("end", iso.format(new Date(now)));
            r.put("duration", elapsed);
            r.put("category", cat);
            arr.put(r);
            getPrefs().edit().putString("records_cache", arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "save record", e);
        }
        // 重置计时器
        getPrefs().edit().putBoolean(KEY_RUNNING, false).putLong(KEY_ELAPSED, 0).putLong(KEY_START, now).apply();
        btnPlay.setText("▶");
        Toast.makeText(this, "已保存 " + formatDuration(elapsed) + "（" + cat + "）", Toast.LENGTH_SHORT).show();
    }

    private void cycleCategory() {
        String[] cats = getCategoryList();
        String cur = getPrefs().getString(KEY_CAT, cats[0]);
        int idx = 0;
        for (int i = 0; i < cats.length; i++) if (cats[i].equals(cur)) { idx = i; break; }
        String next = cats[(idx + 1) % cats.length];
        getPrefs().edit().putString(KEY_CAT, next).apply();
        tvCategory.setText(next);
    }

    private String[] getCategoryList() {
        String def = "工作,学习,运动,休息,阅读,创作,冥想,其他";
        String raw = getPrefs().getString(KEY_CATS, def);
        return raw.split(",");
    }

    /* ---------- UI 刷新 ---------- */

    private void refreshUi() {
        boolean running = getPrefs().getBoolean(KEY_RUNNING, false);
        btnPlay.setText(running ? "⏸" : "▶");
        tvCategory.setText(getPrefs().getString(KEY_CAT, "工作"));
    }

    private void startTicker() {
        new Thread(() -> {
            while (attached) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) { break; }
                long elapsed = getPrefs().getLong(KEY_ELAPSED, 0);
                boolean running = getPrefs().getBoolean(KEY_RUNNING, false);
                if (running) elapsed += (System.currentTimeMillis() - getPrefs().getLong(KEY_START, System.currentTimeMillis()));
                final long fEl = elapsed;
                if (floatView != null) {
                    floatView.post(() -> tvTimer.setText(formatDuration(fEl)));
                }
            }
        }, "fw-ticker").start();
    }

    private String formatDuration(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, ss = s % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, ss);
    }

    /* ---------- 折叠 / 隐藏 ---------- */

    private void collapse(boolean c) {
        isCollapsed = c;
        if (expandedView != null) expandedView.setVisibility(c ? View.GONE : View.VISIBLE);
        if (collapsedView != null) collapsedView.setVisibility(c ? View.VISIBLE : View.GONE);
    }

    private void hideFloat() {
        try { if (attached && floatView != null) wm.removeView(floatView); } catch (Exception ignored) {}
        attached = false;
        stopSelf();
    }

    /* ---------- 拖拽 ---------- */

    private class DragTouchListener implements View.OnTouchListener {
        private float dx, dy;
        private int startX, startY;
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (!(floatView.getLayoutParams() instanceof WindowManager.LayoutParams)) return false;
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) floatView.getLayoutParams();
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dx = e.getRawX() - lp.x; dy = e.getRawY() - lp.y;
                    startX = lp.x; startY = lp.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.x = (int)(e.getRawX() - dx); lp.y = (int)(e.getRawY() - dy);
                    wm.updateViewLayout(floatView, lp);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (Math.abs(lp.x - startX) < 6 && Math.abs(lp.y - startY) < 6) {
                        // 视为点击，不处理
                    } else {
                        getPrefs().edit().putInt("pos_x", lp.x).putInt("pos_y", lp.y).apply();
                    }
                    return true;
            }
            return false;
        }
    }

    @Override
    public void onDestroy() {
        attached = false;
        try { if (floatView != null) wm.removeView(floatView); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
