package com.shiguangju;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * JS 桥：cordova.plugins.FloatingWindow
 *   canShow(success)       是否已获得"显示在其他应用上层"权限
 *   requestShow(success)   启动悬浮窗服务（未授权则跳转设置页引导）
 *   hide(success)          停止并隐藏悬浮窗
 */
public class FloatingWindowPlugin extends CordovaPlugin {

    // ===== 新增：悬浮窗按钮 → JS 回调 =====
    private static CallbackContext toggleCallback;

    public static void notifyToggle() {
        if (toggleCallback != null) {
            try {
                JSONObject o = new JSONObject();
                o.put("action", "toggle");
                toggleCallback.success(o); // JS 端会收到 onToggle
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext cb) {
        switch (action) {
            case "canShow":
                cb.success(Settings.canDrawOverlays(cordova.getActivity()) ? 1 : 0);
                return true;
            case "requestShow": {
                if (Settings.canDrawOverlays(cordova.getActivity())) {
                    startService();
                    cb.success(1);
                } else {
                    // 跳转系统设置页引导用户开启
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + cordova.getActivity().getPackageName()));
                    cordova.getActivity().startActivity(i);
                    cb.success(0);
                }
                return true;
            }
            case "hide":
                stopService();
                cb.success(1);
                return true;
            case "openOverlaySettings": {
                // 跳转"显示在其他应用上层"设置页，供用户开启（红米需选「始终允许」）
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + cordova.getActivity().getPackageName()));
                try {
                    cordova.getActivity().startActivity(i);
                    cb.success(1);
                } catch (Exception e) {
                    cb.error("无法打开悬浮窗设置页");
                }
                return true;
            }
            // ===== 新增两个 action =====
            case "onToggle": {
                // JS 注册回调监听：保存回调，不立即返回，供原生按钮触发
                toggleCallback = cb;
                return true;
            }
            case "updateTime": {
                // JS 主动推数字给悬浮窗：time 字符串如 "00:00:03"，running 布尔
                String time = args.optString(0, "");
                boolean running = args.optBoolean(1, true);
                FloatingWindowService.updateTime(cordova.getActivity(), time, running);
                cb.success(1);
                return true;
            }
        }
        return false;
    }

    private void startService() {
        Intent i = new Intent(cordova.getActivity(), FloatingWindowService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            cordova.getActivity().startForegroundService(i);
        } else {
            cordova.getActivity().startService(i);
        }
    }

    private void stopService() {
        Intent i = new Intent(cordova.getActivity(), FloatingWindowService.class);
        cordova.getActivity().stopService(i);
    }
}
