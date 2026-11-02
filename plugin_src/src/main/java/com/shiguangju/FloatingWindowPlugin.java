package com.shiguangju;
import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.apache.cordova.*;
import org.json.*;

public class FloatingWindowPlugin extends CordovaPlugin {
    private static CallbackContext listener;
    private CallbackContext fileCallback;
    private String exportText;
    public static boolean hasListener(){return listener!=null;}
    @Override public void onPause(boolean multitasking){cordova.getActivity().getSharedPreferences("sg_timer_v2",0).edit().putBoolean("uiForeground",false).apply();}
    @Override public void onResume(boolean multitasking){cordova.getActivity().getSharedPreferences("sg_timer_v2",0).edit().putBoolean("uiForeground",true).apply();}
    public static void changed(Context c){if(listener!=null)try{PluginResult r=new PluginResult(PluginResult.Status.OK,TimerStore.snapshot(c));r.setKeepCallback(true);listener.sendPluginResult(r);}catch(Exception e){android.util.Log.e("SGTimer","callback",e);}}
    @Override public void onReset(){listener=null;}
    @Override public void onDestroy(){listener=null;}
    @Override public boolean execute(String op,JSONArray args,CallbackContext cb){
        Context c=cordova.getActivity();try{switch(op){
            case "exportFile":
            case "importFile":{
                if(fileCallback!=null){cb.error("已有文件操作正在进行");return true;}
                fileCallback=cb;exportText=op.equals("exportFile")?args.getString(0):null;
                Intent picker=new Intent(exportText==null?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);
                if(exportText!=null)picker.putExtra(Intent.EXTRA_TITLE,args.optString(1,"shiguangju-backup.json"));
                cordova.startActivityForResult(this,picker,82);return true;
            }
            case "state":c.getSharedPreferences("sg_timer_v2",0).edit().putBoolean("uiForeground",cordova.getActivity().hasWindowFocus()).apply();cb.success(TimerStore.snapshot(c));return true;
            case "reminderShown":c.getSharedPreferences("sg_timer_v2",0).edit().putLong("reminderMark",args.getLong(0)).remove("reminderDueOverride").apply();ReminderReceiver.schedule(c);cb.success();return true;
            case "reminderSnooze":c.getSharedPreferences("sg_timer_v2",0).edit().putLong("reminderDueOverride",args.getLong(0)+5*60000).apply();ReminderReceiver.schedule(c);cb.success();return true;
            case "command":cb.success(TimerStore.command(c,args.getString(0),args.optString(1,"")));changed(c);return true;
            case "ack":TimerStore.acknowledge(c,args.getJSONArray(0));cb.success();return true;
            case "subscribe":listener=cb;changed(c);return true;
            case "configure":if(!c.getSharedPreferences("sg_timer_v2",0).edit().putString("categories",args.getJSONArray(0).toString()).putString("reminders",args.getJSONObject(1).toString()).putString("categoryColors",args.optJSONObject(2)==null?"{}":args.getJSONObject(2).toString()).commit())throw new IllegalStateException("设置保存失败");ReminderReceiver.schedule(c);cb.success();return true;
            case "notificationPermission":if(Build.VERSION.SDK_INT>=33&&!cordova.hasPermission(Manifest.permission.POST_NOTIFICATIONS))cordova.requestPermission(this,81,Manifest.permission.POST_NOTIFICATIONS);cb.success(Build.VERSION.SDK_INT<33||cordova.hasPermission(Manifest.permission.POST_NOTIFICATIONS)?1:0);return true;
            case "canShow":cb.success(Settings.canDrawOverlays(c)?1:0);return true;
            case "openOverlaySettings":cordova.getActivity().startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+c.getPackageName())));cb.success(0);return true;
            case "requestShow":if(!Settings.canDrawOverlays(c)){cb.success(0);return true;}cordova.getActivity().runOnUiThread(()->{try{Intent i=new Intent(c,FloatingWindowService.class);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);cb.success(1);}catch(Exception e){cb.error(e.getMessage());}});return true;
            case "hide":c.stopService(new Intent(c,FloatingWindowService.class));cb.success(1);return true;
            default:return false;
        }}catch(Exception e){fileCallback=null;exportText=null;cb.error(e.getMessage());return true;}
    }
    @Override public void onActivityResult(int request,int result,Intent intent){
        if(request!=82||fileCallback==null)return;CallbackContext cb=fileCallback;fileCallback=null;String text=exportText;exportText=null;
        if(result!=Activity.RESULT_OK||intent==null||intent.getData()==null){cb.error("已取消文件操作");return;}
        cordova.getThreadPool().execute(()->{try{
            if(text!=null){try(java.io.OutputStream out=cordova.getActivity().getContentResolver().openOutputStream(intent.getData())){out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}cb.success(1);}
            else {try(java.io.InputStream in=cordova.getActivity().getContentResolver().openInputStream(intent.getData());java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>50*1024*1024)throw new java.io.IOException("备份超过50MB");out.write(buf,0,n);}cb.success(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));}}
        }catch(Exception e){cb.error("文件操作失败："+e.getMessage());}});
    }
}
