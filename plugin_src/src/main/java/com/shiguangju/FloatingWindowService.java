package com.shiguangju;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.Locale;

/** Visible user-started overlay: no wake lock or dataSync service. */
public class FloatingWindowService extends Service {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager wm;private View view;private WindowManager.LayoutParams params;
    private final Runnable tick=new Runnable(){public void run(){render();handler.postDelayed(this,1000);}};
    private int id(String name,String type){return getResources().getIdentifier(name,type,getPackageName());}
    private View v(String name){return view.findViewById(id(name,"id"));}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){super.onCreate();try{
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("sg_overlay","悬浮计时",NotificationManager.IMPORTANCE_LOW));
        Intent launch=getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent pi=PendingIntent.getActivity(this,0,launch,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"sg_overlay").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("拾光橘 · 悬浮计时").setContentText("点击返回应用；关闭悬浮窗不会丢失计时").setContentIntent(pi).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(1001,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(1001,n);
        if(!Settings.canDrawOverlays(this)){stopSelf();return;}
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);view=LayoutInflater.from(this).inflate(id("floating_window","layout"),null);
        params=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.START;
        params.x=getSharedPreferences("sg_timer_v2",0).getInt("x",0);params.y=getSharedPreferences("sg_timer_v2",0).getInt("y",150);wm.addView(view,params);
        v("btnPlay").setOnClickListener(w->{try{act("running".equals(TimerStore.snapshot(this).getString("state"))?"pause":"start","");}catch(Exception e){error(e);}});
        v("btnSave").setOnClickListener(w->act("save",""));v("btnHide").setOnClickListener(w->stopSelf());
        v("btnCategory").setOnClickListener(w->{try{JSONArray cats=new JSONArray(getSharedPreferences("sg_timer_v2",0).getString("categories","[\"工作\"]"));JSONObject s=TimerStore.snapshot(this);int next=0;for(int i=0;i<cats.length();i++)if(cats.getString(i).equals(s.getString("category")))next=(i+1)%cats.length();if(cats.length()>0)act("category",cats.getString(next));}catch(Exception e){error(e);}});
        v("btnCollapse").setOnClickListener(w->{v("expanded").setVisibility(View.GONE);v("collapsed").setVisibility(View.VISIBLE);});
        v("collapsed").setOnClickListener(w->{v("expanded").setVisibility(View.VISIBLE);v("collapsed").setVisibility(View.GONE);});
        v("tvTimer").setOnClickListener(w->startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        View.OnTouchListener drag=new View.OnTouchListener(){float x,y;int px,py;public boolean onTouch(View w,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();px=params.x;py=params.y;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){params.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-view.getWidth(),px+(int)(e.getRawX()-x)));params.y=Math.max(0,Math.min(getResources().getDisplayMetrics().heightPixels-view.getHeight(),py+(int)(e.getRawY()-y)));try{wm.updateViewLayout(view,params);}catch(IllegalArgumentException ignored){}return true;}if(e.getAction()==MotionEvent.ACTION_UP){getSharedPreferences("sg_timer_v2",0).edit().putInt("x",params.x).putInt("y",params.y).apply();return true;}return false;}};
        v("dragHandle").setOnTouchListener(drag);v("tvTimerCollapsed").setOnTouchListener(drag);handler.post(tick);
    }catch(Exception e){error(e);stopSelf();}}
    private void error(Exception e){android.util.Log.e("SGOverlay","overlay error",e);Toast.makeText(this,"悬浮窗暂不可用，请返回应用查看计时",Toast.LENGTH_LONG).show();}
    private void act(String op,String cat){try{TimerStore.command(this,op,cat);FloatingWindowPlugin.changed(this);render();}catch(Exception e){error(e);}}
    static void applyCategoryColor(Context context,TextView badge,String category) throws JSONException {
        JSONObject colors=new JSONObject(context.getSharedPreferences("sg_timer_v2",0).getString("categoryColors","{}"));
        String hex=colors.optString(category,"#4fc3f7");if(!hex.matches("#[0-9a-fA-F]{6}"))hex="#4fc3f7";
        badge.setBackgroundColor(Color.parseColor(hex));badge.setTextColor(Color.parseColor("#101628"));
    }
    private void render(){if(view==null)return;try{JSONObject s=TimerStore.snapshot(this);long ms=s.optLong("activeDuration");JSONArray a=s.getJSONArray("segments");for(int i=0;i<a.length();i++)ms+=a.getJSONObject(i).getLong("duration");long sec=ms/1000;String text=String.format(Locale.US,"%02d:%02d:%02d",sec/3600,sec/60%60,sec%60);((TextView)v("tvTimer")).setText(text);((TextView)v("tvTimerCollapsed")).setText(text);((TextView)v("tvCategory")).setText(s.getString("category"));applyCategoryColor(this,(TextView)v("tvCategory"),s.getString("category"));((Button)v("btnPlay")).setText("running".equals(s.getString("state"))?"Ⅱ":"▶");}catch(Exception e){android.util.Log.e("SGOverlay","render",e);}}
    @Override public int onStartCommand(Intent i,int flags,int start){return START_NOT_STICKY;}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(view!=null&&wm!=null)try{wm.removeView(view);}catch(IllegalArgumentException ignored){}view=null;super.onDestroy();}
}
