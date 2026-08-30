package com.shiguangju;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.graphics.PixelFormat;

/**
 * 全局悬浮窗服务。
 * 注意：R 类由构建系统生成在应用包 com.shiguangju.app 下，
 * 因此这里一律使用全限定名 com.shiguangju.app.R，不要 import。
 */
public class FloatingWindowService extends Service {

    private WindowManager wm;
    private View floatView;
    private WindowManager.LayoutParams params;
    private float initialX, initialY;
    private float initialTouchX, initialTouchY;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        LayoutInflater inflater = LayoutInflater.from(this);
        // ✅ 关键：用应用包名的 R，而不是本包 com.shiguangju.R
        floatView = inflater.inflate(com.shiguangju.app.R.layout.floating_window, null);

        params = new WindowManager.LayoutParams(
                320,
                180,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        floatView.findViewById(com.shiguangju.app.R.id.fw_root).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = (int) (initialX + (event.getRawX() - initialTouchX));
                        params.y = (int) (initialY + (event.getRawY() - initialTouchY));
                        wm.updateViewLayout(floatView, params);
                        return true;
                }
                return false;
            }
        });

        wm.addView(floatView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatView != null) {
            wm.removeView(floatView);
            floatView = null;
        }
    }
}
