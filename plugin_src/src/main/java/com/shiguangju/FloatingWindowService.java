package com.shiguangju;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.WindowManager;
import android.view.Gravity;
import android.widget.TextView;

public class FloatingWindowService extends Service {
    private WindowManager wm;
    private TextView tv;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        tv = new TextView(this);
        tv.setText("拾光橘");
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            200, 100,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = 0;
        p.y = 100;
        wm.addView(tv, p);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (tv != null) wm.removeView(tv);
    }
}
