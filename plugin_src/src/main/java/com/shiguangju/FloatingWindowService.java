package com.shiguangju;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.graphics.PixelFormat;

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

        // ✅ 关键：inflate 你的漂亮布局，而不是裸 TextView
        LayoutInflater inflater = LayoutInflater.from(this);
        floatView = inflater.inflate(R.layout.floating_window, null);

        params = new WindowManager.LayoutParams(
                320,  // width (dp 量级，可按需调)
                120,  // height
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        // 拖动逻辑
        floatView.findViewById(R.id.fw_root).setOnTouchListener(new View.OnTouchListener() {
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
