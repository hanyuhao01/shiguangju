package com.shiguangju;
import android.Manifest;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.apache.cordova.*;
import org.json.*;

public class FloatingWindowPlugin extends CordovaPlugin {
    private static CallbackContext listener;
    public static void changed(Context c){if(listener!=null)try{PluginResult r=new PluginResult(PluginResult.Status.OK,TimerStore.snapshot(c));r.setKeepCallback(true);listener.sendPluginResult(r);}catch(Exception e){android.util.Log.e("SGTimer","callback",e);}}
    @Override public void onReset(){listener=null;}
    @Override public void onDestroy(){listener=null;}
    @Override public boolean execute(String op,JSONArray args,CallbackContext cb){
        Context c=cordova.getActivity();try{switch(op){
            case "state":cb.success(TimerStore.snapshot(c));return true;
            case "command":cb.success(TimerStore.command(c,args.getString(0),args.optString(1,"")));changed(c);return true;
            case "ack":TimerStore.acknowledge(c,args.getJSONArray(0));cb.success();return true;
            case "subscribe":listener=cb;changed(c);return true;
            case "configure":if(!c.getSharedPreferences("sg_timer_v2",0).edit().putString("categories",args.getJSONArray(0).toString()).putString("reminders",args.getJSONObject(1).toString()).commit())throw new IllegalStateException("设置保存失败");ReminderReceiver.schedule(c);cb.success();return true;
            case "notificationPermission":if(Build.VERSION.SDK_INT>=33&&!cordova.hasPermission(Manifest.permission.POST_NOTIFICATIONS))cordova.requestPermission(this,81,Manifest.permission.POST_NOTIFICATIONS);cb.success(Build.VERSION.SDK_INT<33||cordova.hasPermission(Manifest.permission.POST_NOTIFICATIONS)?1:0);return true;
            case "canShow":cb.success(Settings.canDrawOverlays(c)?1:0);return true;
            case "openOverlaySettings":cordova.getActivity().startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+c.getPackageName())));cb.success(0);return true;
            case "requestShow":if(!Settings.canDrawOverlays(c)){cb.success(0);return true;}cordova.getActivity().runOnUiThread(()->{try{Intent i=new Intent(c,FloatingWindowService.class);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);cb.success(1);}catch(Exception e){cb.error(e.getMessage());}});return true;
            case "hide":c.stopService(new Intent(c,FloatingWindowService.class));cb.success(1);return true;
            default:return false;
        }}catch(Exception e){cb.error(e.getMessage());return true;}
    }
}
