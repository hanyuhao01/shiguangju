package com.shiguangju;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * 悬浮窗服务：带 播放/暂停 + 关闭 按钮，数字实时刷新，主线程保护，单例防重入
 */
public class FloatingWindowService extends Service {

    private static final String CHANNEL_ID = "floating_window_channel";
    public static final String ACTION_UPDATE = "com.shiguangju.floatingwindow.UPDATE";
    public static final String EXTRA_TIME = "time";   // 格式 "00:00:03"
    public static final String EXTRA_RUNNING = "running"; // true/false

    private static volatile boolean sRunning = false; // 防重复创建

    private WindowManager wm;
    private View floatView;
    private TextView tvTime;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        createFloatView(); // 创建即显示
        sRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 接收主界面发来的计时更新
        if (intent != null && ACTION_UPDATE.equals(intent.getAction())) {
            String time = intent.getStringExtra(EXTRA_TIME);
            boolean running = intent.getBooleanExtra(EXTRA_RUNNING, true);
            updateTimeOnUI(time, running);
        }
        return START_STICKY; // 被杀后自动重启，解决"退出再进打不开"
    }

    // ===== 悬浮窗 UI =====
    private void createFloatView() {
        mainHandler.post(() -> {
            if (floatView != null) return; // 已存在，防重复

            floatView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);

            tvTime = floatView.findViewById(R.id.tv_time);

            // 播放/暂停 按钮
            floatView.findViewById(R.id.btn_toggle).setOnClickListener(v -> {
                // 回调给 JS（通过 Plugin 单例）
                FloatingWindowPlugin.notifyToggle();
            });
            // 关闭 按钮
            floatView.findViewById(R.id.btn_close).setOnClickListener(v -> stopSelf());

            // 拖动（只拖动头部，不影响按钮点击）
            View dragArea = floatView.findViewById(R.id.drag_area);
            dragArea.setOnTouchListener(new View.OnTouchListener() {
                private float downX, downY, paramX, paramY;
                private WindowManager.LayoutParams params;
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (params == null) params = (WindowManager.LayoutParams) floatView.getLayoutParams();
                    switch (e.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = e.getRawX(); downY = e.getRawY();
                            paramX = params.x; paramY = params.y;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = (int) (paramX + (e.getRawX() - downX));
                            params.y = (int) (paramY + (e.getRawY() - downY));
                            wm.updateViewLayout(floatView, params);
                            return true;
                    }
                    return false;
                }
            });

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 100; params.y = 200;

            try {
                wm.addView(floatView, params);
            } catch (Exception e) {
                e.printStackTrace(); // 防闪退：捕获 BadToken 等
            }
        });
    }

    // ===== 数字刷新（主线程 + 防空）=====
    private void updateTimeOnUI(String time, boolean running) {
        mainHandler.post(() -> {
            if (tvTime != null && time != null) {
                tvTime.setText(time);
            }
        });
    }

    // ===== 提供给 Plugin 调用的静态入口（让 JS 能随时更新数字）=====
    public static void updateTime(Context ctx, String time, boolean running) {
        if (!sRunning) return;
        Intent i = new Intent(ctx, FloatingWindowService.class);
        i.setAction(ACTION_UPDATE);
        i.putExtra(EXTRA_TIME, time);
        i.putExtra(EXTRA_RUNNING, running);
        ctx.startService(i);
    }

    public static boolean isRunning() { return sRunning; }

    // ===== 前台通知（Android 8+ 必需）=====
    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "悬浮窗", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher) // 用你自己的图标
                .setContentTitle("拾光橘悬浮窗运行中")
                .build();
        startForeground(1001, n);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sRunning = false;
        // 清理 View，防 WebView 销毁后 NPE 闪退
        if (floatView != null && wm != null) {
            mainHandler.post(() -> {
                try { wm.removeView(floatView); } catch (Exception ignored) {}
                floatView = null;
            });
        }
    }
}
