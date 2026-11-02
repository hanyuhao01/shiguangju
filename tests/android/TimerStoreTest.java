package com.shiguangju;

import android.content.Context;
import android.os.SystemClock;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** JVM Android tests exercise the actual Java authority, not a JavaScript reimplementation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class TimerStoreTest {
    private Context c;
    @Before public void reset(){c=RuntimeEnvironment.getApplication();c.getSharedPreferences("sg_timer_v2",0).edit().clear().commit();}
    @Test public void pausesAndResumeExcludeIdleTime() throws Exception {
        TimerStore.command(c,"start","");SystemClock.sleep(60000);TimerStore.command(c,"pause","");SystemClock.sleep(180000);TimerStore.command(c,"start","");SystemClock.sleep(120000);
        JSONObject s=TimerStore.command(c,"save","");JSONArray p=s.getJSONArray("pending");assertEquals(2,p.length());assertEquals(180000,p.getJSONObject(0).getLong("duration")+p.getJSONObject(1).getLong("duration"));assertEquals("idle",s.getString("state"));
    }
    @Test public void repeatedActionsDoNotDuplicateSavedRecords() throws Exception {
        TimerStore.command(c,"start","");SystemClock.sleep(1000);TimerStore.command(c,"start","");TimerStore.command(c,"pause","");TimerStore.command(c,"pause","");
        JSONObject s=TimerStore.command(c,"save","");assertEquals(1,s.getJSONArray("pending").length());assertEquals(1000,s.getJSONArray("pending").getJSONObject(0).getLong("duration"));assertEquals(1,TimerStore.command(c,"save","").getJSONArray("pending").length());
    }
    @Test public void pendingJournalSurvivesReReadAndAcknowledgement() throws Exception {
        TimerStore.command(c,"start","");SystemClock.sleep(60000);TimerStore.command(c,"save","");JSONObject s=TimerStore.snapshot(c);String id=s.getJSONArray("pending").getJSONObject(0).getString("id");
        assertEquals(1,TimerStore.snapshot(c).getJSONArray("pending").length());TimerStore.acknowledge(c,new JSONArray().put(id));assertEquals(0,TimerStore.snapshot(c).getJSONArray("pending").length());
    }
    @Test public void categorySwitchPreservesBothActivities() throws Exception {
        TimerStore.command(c,"start","");SystemClock.sleep(1000);TimerStore.command(c,"category","阅读");SystemClock.sleep(2000);JSONArray p=TimerStore.command(c,"save","").getJSONArray("pending");
        assertEquals("工作",p.getJSONObject(0).getString("category"));assertEquals("阅读",p.getJSONObject(1).getString("category"));assertEquals(1000,p.getJSONObject(0).getLong("duration"));assertEquals(2000,p.getJSONObject(1).getLong("duration"));
    }
    @Test public void timerRecoversWithoutRefreshTicks() throws Exception {
        JSONObject start=TimerStore.command(c,"start","");SystemClock.sleep(7200000);JSONObject restored=TimerStore.snapshot(c);
        assertEquals(start.getString("id"),restored.getString("id"));assertEquals(7200000,restored.getLong("activeDuration"));
    }
    @Test public void everyNativeControlResourceExists() {
        for(String id:new String[]{"expanded","collapsed","dragHandle","tvTimer","tvTimerCollapsed","tvCategory","btnPlay","btnSave","btnCategory","btnCollapse","btnHide"})assertTrue(id,c.getResources().getIdentifier(id,"id",c.getPackageName())!=0);
        assertTrue(c.getResources().getIdentifier("floating_window","layout",c.getPackageName())!=0);
    }
    @Test public void noReminderWithoutExplicitActivitySelection() throws Exception {
        JSONObject cfg=new JSONObject("{\"enabled\":true,\"system\":true,\"categories\":{},\"types\":{\"work\":{\"enabled\":true,\"minutes\":50}}}");
        assertEquals(0,ReminderReceiver.interval(cfg,TimerStore.snapshot(c)));cfg.getJSONObject("categories").put("工作","work");assertEquals(50*60000L,ReminderReceiver.interval(cfg,TimerStore.snapshot(c)));
        cfg.getJSONObject("types").getJSONObject("work").put("enabled",false);assertEquals(0,ReminderReceiver.interval(cfg,TimerStore.snapshot(c)));
    }
}
