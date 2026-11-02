package com.shiguangju;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import org.json.*;
import java.util.Calendar;

/** Inexact alarms: no exact-alarm privilege, no extra background service. */
public class ReminderReceiver extends BroadcastReceiver {
    static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,42,new Intent(c,ReminderReceiver.class).setAction("check"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static JSONObject settings(Context c)throws JSONException{return new JSONObject(c.getSharedPreferences("sg_timer_v2",0).getString("reminders","{}"));}
    static long interval(JSONObject cfg,JSONObject s){JSONObject rule=cfg.optJSONObject("types");String type=cfg.optJSONObject("categories")==null?"none":cfg.optJSONObject("categories").optString(s.optString("category"),"none");rule=rule==null?null:rule.optJSONObject(type);return cfg.optBoolean("enabled")&&cfg.optBoolean("system")&&rule!=null&&rule.optBoolean("enabled")?Math.max(1,Math.min(240,rule.optInt("minutes",60)))*60000L:0;}
    public static void schedule(Context c){try{AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);am.cancel(alarm(c));JSONObject s=TimerStore.snapshot(c),cfg=settings(c);long gap=interval(cfg,s);if(gap==0||!"running".equals(s.optString("state")))return;long active=s.optLong("activeDuration"),mark=c.getSharedPreferences("sg_timer_v2",0).getLong("reminderMark",0),now=SystemClock.elapsedRealtime();long delay=Math.max(1000,Math.max(gap-active,mark+gap-now));am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,now+delay,alarm(c));}catch(Exception e){android.util.Log.e("SGReminder","schedule",e);}}
    private static int minutes(String s){return Integer.parseInt(s.substring(0,2))*60+Integer.parseInt(s.substring(3));}
    private static boolean quiet(JSONObject cfg){Calendar d=Calendar.getInstance();int t=d.get(Calendar.HOUR_OF_DAY)*60+d.get(Calendar.MINUTE),a=minutes(cfg.optString("dndStart","22:00")),b=minutes(cfg.optString("dndEnd","08:00"));return a==b?false:a<b?t>=a&&t<b:t>=a||t<b;}
    @Override public void onReceive(Context c,Intent intent){try{
        NotificationManager nm=c.getSystemService(NotificationManager.class);String action=intent.getAction();
        if("snooze".equals(action)||"dismiss".equals(action)){nm.cancel(1002);c.getSharedPreferences("sg_timer_v2",0).edit().putLong("reminderMark",SystemClock.elapsedRealtime()+("snooze".equals(action)?5*60000:0)).apply();schedule(c);return;}
        JSONObject s=TimerStore.snapshot(c),cfg=settings(c);long gap=interval(cfg,s);
        if(gap>0&&"running".equals(s.optString("state"))&&s.optLong("activeDuration")>=gap&&!quiet(cfg)){
            c.getSharedPreferences("sg_timer_v2",0).edit().putLong("reminderMark",SystemClock.elapsedRealtime()).apply();
            if(Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED){
                String ch=cfg.optBoolean("sound")?"sg_health_sound":"sg_health_silent";NotificationChannel channel=new NotificationChannel(ch,"健康休息提醒",NotificationManager.IMPORTANCE_DEFAULT);if(!cfg.optBoolean("sound"))channel.setSound(null,null);nm.createNotificationChannel(channel);
                String type=cfg.getJSONObject("categories").optString(s.getString("category"),"none");String text=type.equals("screen")?"可以看看约6米外的地方，保持20秒。":type.equals("paper")?"可以暂停阅读，活动身体，看看远处。":"可以起身活动3—5分钟，放松肩颈。";
                Notification.Builder b=new Notification.Builder(c,ch).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("休息片刻").setContentText(text).setAutoCancel(true);
                Intent launch=c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());b.setContentIntent(PendingIntent.getActivity(c,0,launch,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
                for(String act:new String[]{"snooze","dismiss"})b.addAction(new Notification.Action.Builder(null,act.equals("snooze")?"稍后提醒":"关闭本次",PendingIntent.getBroadcast(c,act.equals("snooze")?43:44,new Intent(c,ReminderReceiver.class).setAction(act),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)).build());
                nm.notify(1002,b.build());
            }
        }else c.getSharedPreferences("sg_timer_v2",0).edit().putLong("reminderMark",SystemClock.elapsedRealtime()).apply();
        schedule(c);
    }catch(Exception e){android.util.Log.e("SGReminder","receive",e);}}
}
