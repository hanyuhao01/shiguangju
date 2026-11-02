package com.shiguangju;
import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;
import java.util.*;
import java.text.SimpleDateFormat;

/** Android is the single timer owner; saved records stay in a journal until WebView acknowledgement. */
public final class TimerStore {
    static JSONObject idle(String cat) throws JSONException {return new JSONObject().put("schema",2).put("id",UUID.randomUUID().toString()).put("state","idle").put("category",cat).put("segments",new JSONArray()).put("revision",0);}
    static JSONObject read(Context c) throws JSONException {String raw=c.getSharedPreferences("sg_timer_v2",0).getString("state",null);return raw==null?idle("工作"):new JSONObject(raw);}
    static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    static long activeDuration(Context c,JSONObject a){if(a==null)return 0;if(a.optInt("boot",-2)==boot(c)&&SystemClock.elapsedRealtime()>=a.optLong("mono",Long.MAX_VALUE))return SystemClock.elapsedRealtime()-a.optLong("mono");return Math.max(0,System.currentTimeMillis()-a.optLong("start",System.currentTimeMillis()));}
    public static synchronized JSONObject snapshot(Context c) throws JSONException {JSONObject s=read(c);return s.put("activeDuration",activeDuration(c,s.optJSONObject("active"))).put("observedAt",System.currentTimeMillis()).put("pending",new JSONArray(c.getSharedPreferences("sg_timer_v2",0).getString("pending","[]")));}
    static JSONObject active(Context c,String cat,long now) throws JSONException {return new JSONObject().put("start",now).put("mono",SystemClock.elapsedRealtime()).put("boot",boot(c)).put("category",cat);}
    public static synchronized JSONObject command(Context c,String op,String cat) throws JSONException {
        if(!Arrays.asList("start","pause","save","reset","category").contains(op))throw new JSONException("未知计时操作");
        JSONObject s=read(c);long now=System.currentTimeMillis();boolean running="running".equals(s.optString("state"));
        if((op.equals("pause")||op.equals("save")||op.equals("category"))&&running){JSONObject a=s.getJSONObject("active");long dur=activeDuration(c,a),start=a.getLong("start");s.getJSONArray("segments").put(new JSONObject().put("start",start).put("end",start+dur).put("duration",dur).put("category",a.getString("category")));s.remove("active");s.put("state","paused");}
        if(op.equals("start")&&!running)s.put("active",active(c,s.getString("category"),now)).put("state","running");
        if(op.equals("category")){s.put("category",cat);if(running)s.put("active",active(c,cat,now)).put("state","running");}
        JSONArray pending=new JSONArray(c.getSharedPreferences("sg_timer_v2",0).getString("pending","[]"));
        if(op.equals("save")){
            JSONArray segs=s.getJSONArray("segments");SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);iso.setTimeZone(TimeZone.getTimeZone("UTC"));SimpleDateFormat day=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
            for(int i=0;i<segs.length();i++){JSONObject x=segs.getJSONObject(i);if(x.getLong("duration")<=0)continue;pending.put(new JSONObject().put("id",s.getString("id")+":"+i).put("sessionId",s.getString("id")).put("category",x.getString("category")).put("duration",x.getLong("duration")).put("start",iso.format(new Date(x.getLong("start")))).put("end",iso.format(new Date(x.getLong("end")))).put("date",day.format(new Date(x.getLong("start")))).put("segments",new JSONArray().put(x)));}
            s=idle(s.getString("category"));
        }else if(op.equals("reset"))s=idle(s.getString("category"));
        s.put("revision",s.optLong("revision")+1);
        if(!c.getSharedPreferences("sg_timer_v2",0).edit().putString("pending",pending.toString()).putString("state",s.toString()).commit())throw new IllegalStateException("无法持久化计时，请重试");
        ReminderReceiver.schedule(c);return snapshot(c);
    }
    public static synchronized void acknowledge(Context c,JSONArray ids) throws JSONException {JSONArray p=new JSONArray(c.getSharedPreferences("sg_timer_v2",0).getString("pending","[]")),keep=new JSONArray();Set<String> ack=new HashSet<>();for(int i=0;i<ids.length();i++)ack.add(ids.getString(i));for(int i=0;i<p.length();i++)if(!ack.contains(p.getJSONObject(i).getString("id")))keep.put(p.getJSONObject(i));if(!c.getSharedPreferences("sg_timer_v2",0).edit().putString("pending",keep.toString()).commit())throw new IllegalStateException("确认保存失败");}
}
