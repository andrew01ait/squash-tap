package at.andrew.squashtap;

import android.app.Application;
import android.app.Dialog;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

/** Exercises actual screen flags, persisted sessions and service restart guards. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class,qualifiers="w233dp-h233dp-round")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class SessionPowerUiTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private SharedPreferences preferences;
    @Before public void prepare(){
        preferences=RuntimeEnvironment.getApplication().getSharedPreferences("squash",0);
        preferences.edit().clear().putBoolean("asked_match_notification",true).commit();
    }
    private void launch(){controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();}
    private void closeActivity(){Dialog dialog=ShadowDialog.getLatestDialog();if(dialog!=null)dialog.dismiss();if(controller!=null){controller.pause().stop().destroy();controller=null;}}
    @After public void finish(){closeActivity();}
    private boolean awake(){return (activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;}
    private MatchBook saved(){return MatchBook.decode(preferences.getString("match_book",""));}
    private boolean active(){return MatchDisplayPolicy.ongoing(saved().current(),preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""),preferences.getBoolean("match_paused",false));}
    private Button findButton(View view,String text){if(view instanceof Button&&((Button)view).getText().toString().split("\n",2)[0].equals(text))return (Button)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Button found=findButton(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}return null;}
    private void click(String text){Dialog dialog=ShadowDialog.getLatestDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());Button found=findButton(dialog.getWindow().getDecorView(),text);assertNotNull("Missing "+text,found);found.performClick();}
    private boolean containsText(View view,String text){if(view instanceof TextView&&((TextView)view).getText().toString().contains(text))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(containsText(((ViewGroup)view).getChildAt(i),text))return true;return false;}
    private void matchMenu(){activity.action(TouchTargets.MENU);click("Current match");}
    private void assertNoServiceStart(){assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());}
    private void clearServiceEvents(){shadowOf(RuntimeEnvironment.getApplication()).clearStartedServices();while(shadowOf(RuntimeEnvironment.getApplication()).getNextStoppedService()!=null){}}
    private void assertServiceStopped(){Intent stopped=shadowOf(RuntimeEnvironment.getApplication()).getNextStoppedService();assertNotNull(stopped);assertEquals(MatchSessionService.class.getName(),stopped.getComponent().getClassName());}
    private MatchBook almostFinished(){MatchBook book=new MatchBook(new MatchState("final",3));for(int i=0;i<11;i++)book.add(0);book.nextGame();for(int i=0;i<10;i++)book.add(0);return book;}

    @Test public void freshLaunchIsIdleAndDoesNotCheckForUpdatesOverTime(){
        launch();assertFalse(awake());assertFalse(active());assertNoServiceStart();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10));
        assertFalse(preferences.contains("last_check"));assertFalse(awake());assertNoServiceStart();
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).getLatestWakeLock());
    }
    @Test public void firstPointStartsSessionAndUndoToZeroKeepsThatExplicitSession(){
        launch();activity.action(TouchTargets.BLUE);assertTrue(active());assertTrue(awake());assertEquals(1,saved().current().game().blue());
        Intent start=shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();assertNotNull(start);assertEquals(MatchSessionService.class.getName(),start.getComponent().getClassName());
        activity.action(TouchTargets.UNDO);assertEquals(0,saved().current().game().blue());assertTrue(active());assertTrue(awake());
    }
    @Test public void startBeforeFirstPointIsExplicitAndDoesNotStartMatchTiming(){
        launch();matchMenu();click("Start match");assertTrue(active());assertTrue(awake());assertFalse(saved().current().hasPoints());assertEquals("Starts with first point",saved().currentStartedText());
        preferences.edit().remove(MatchDisplayPolicy.ACTIVE_MATCH).commit();activity.action(TouchTargets.MENU);click("Change sport");Button tennis=findButton(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"Tennis");assertNotNull(tennis);assertFalse(tennis.isEnabled());assertEquals(0,saved().currentStartedAt());
    }
    @Test public void pauseReleasesScreenAndServiceAndReopeningRequiresResume(){
        launch();activity.action(TouchTargets.BLUE);matchMenu();clearServiceEvents();click("Pause match");
        assertFalse(active());assertFalse(awake());assertServiceStopped();long start=saved().currentStartedAt();
        closeActivity();clearServiceEvents();launch();assertFalse(active());assertFalse(awake());assertNoServiceStart();
        activity.action(TouchTargets.RED);assertEquals(0,saved().current().game().red());click("Resume match");
        assertTrue(active());assertTrue(awake());assertEquals(start,saved().currentStartedAt());assertEquals(1,saved().current().game().blue());
    }
    @Test public void oldUnfinishedScoreWaitsForResumeAndRetainsUndoAndTiming(){
        MatchBook book=new MatchBook(new MatchState("old",3));book.add(0);book.add(1);preferences.edit().putString("match_book",book.encode()).commit();
        launch();assertFalse(active());assertFalse(awake());assertNoServiceStart();activity.action(TouchTargets.BLUE);assertEquals(1,saved().current().game().blue());click("Resume match");
        assertTrue(active());assertTrue(awake());activity.action(TouchTargets.UNDO);assertEquals(0,saved().current().game().red());
    }
    @Test public void completionStopsSessionAndHistoryDoesNotRestartIt(){
        MatchBook book=almostFinished();preferences.edit().putString("match_book",book.encode()).putString(MatchDisplayPolicy.ACTIVE_MATCH,"final").commit();
        launch();assertTrue(awake());clearServiceEvents();activity.action(TouchTargets.BLUE);
        assertTrue(saved().current().complete());assertFalse(active());assertFalse(awake());assertServiceStopped();assertFalse(preferences.contains(MatchDisplayPolicy.ACTIVE_MATCH));
        click("Back to Results");clearServiceEvents();click("Back to Menu");click("Settings");click("Back to Menu");click("Back to score");assertFalse(awake());assertNoServiceStart();
    }
    @Test public void undoOfCompletedResultWaitsForResume(){
        MatchBook book=almostFinished();book.add(0);preferences.edit().putString("match_book",book.encode()).commit();launch();activity.action(TouchTargets.UNDO);
        assertFalse(saved().current().complete());assertFalse(active());assertFalse(awake());assertNoServiceStart();matchMenu();click("Resume match");assertTrue(awake());
    }
    @Test public void newMatchSetupDoesNotKeepAnOldSessionRunning(){
        launch();activity.action(TouchTargets.BLUE);activity.action(TouchTargets.NEW);click("Match length: Best of 3");click("Best of 5 · first to 3");click("Create match");clearServiceEvents();click("Replace & create match");
        assertEquals(5,saved().current().bestOf());assertFalse(saved().current().hasPoints());assertFalse(active());assertFalse(awake());assertServiceStopped();assertNoServiceStart();
    }
    @Test public void backgroundReleasesScreenWithoutEndingARealMatch(){
        launch();activity.action(TouchTargets.BLUE);controller.pause().stop();assertFalse(awake());assertTrue(active());controller.start().resume();assertTrue(awake());assertTrue(active());
    }
    @Test public void resultsAndSettingsReleaseScreenAndBackRestoresOnlyActiveSession(){
        launch();activity.action(TouchTargets.BLUE);activity.action(TouchTargets.MENU);assertFalse(awake());click("Results (0)");assertFalse(awake());click("Back to Menu");click("Settings");assertFalse(awake());click("Scoreboard");assertTrue(awake());assertTrue(active());
    }
    @Test public void staleServiceRestartCannotReviveIdleDraftOrOldScore(){
        MatchBook book=new MatchBook(new MatchState("idle",3));book.add(0);preferences.edit().putString("match_book",book.encode()).commit();
        org.robolectric.android.controller.ServiceController<MatchSessionService> service=Robolectric.buildService(MatchSessionService.class).create();
        assertEquals(Service.START_NOT_STICKY,service.get().onStartCommand(null,0,1));assertTrue(shadowOf(service.get()).isStoppedBySelf());assertNull(shadowOf(service.get()).getLastForegroundNotification());service.destroy();
    }
    @Test public void dismissedTaskClearsSessionAndStickyRestartStops(){
        MatchBook book=new MatchBook(new MatchState("dismissed",3));book.add(0);preferences.edit().putString("match_book",book.encode()).putString(MatchDisplayPolicy.ACTIVE_MATCH,"dismissed").commit();
        org.robolectric.android.controller.ServiceController<MatchSessionService> service=Robolectric.buildService(MatchSessionService.class).create();
        assertEquals(Service.START_STICKY,service.get().onStartCommand(new Intent(),0,1));assertNotNull(shadowOf(service.get()).getLastForegroundNotification());
        service.get().onTaskRemoved(new Intent());assertFalse(preferences.contains(MatchDisplayPolicy.ACTIVE_MATCH));assertTrue(preferences.getBoolean("match_paused",false));assertTrue(shadowOf(service.get()).isForegroundStopped());
        assertEquals(Service.START_NOT_STICKY,service.get().onStartCommand(null,0,2));service.destroy();
    }
    @Test public void diagnosticsUseOnDemandWholeWatchBatteryReadingAndSessionSnapshots(){
        BatteryManager battery=RuntimeEnvironment.getApplication().getSystemService(BatteryManager.class);shadowOf(battery).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY,73);shadowOf(battery).setIsCharging(false);
        launch();activity.action(TouchTargets.BLUE);assertTrue(preferences.getString("lifecycle_trace","").contains("Match activated; battery 73%"));
        shadowOf(battery).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY,71);matchMenu();click("Pause match");assertTrue(preferences.getString("lifecycle_trace","").contains("Match paused; battery 71%"));
        activity.action(TouchTargets.MENU);click("Settings");click("Diagnostics");assertTrue(containsText(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"Battery (whole watch): 71%"));
    }
    @Test public void unsupportedBatteryReadingIsNotPresentedAsARealPercentage(){
        BatteryManager battery=RuntimeEnvironment.getApplication().getSystemService(BatteryManager.class);shadowOf(battery).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY,Integer.MIN_VALUE);shadowOf(battery).setIsCharging(true);
        assertEquals("level unavailable · charging",BatterySnapshot.describe(RuntimeEnvironment.getApplication()));
    }
    @Test public void qrKeepAwakeExpiresAndReleasesImmediatelyWhenBackgrounded(){
        launch();activity.action(TouchTargets.MENU);click("Settings");click("Diagnostics");click("Send to phone");Dialog qr=ShadowDialog.getLatestDialog();
        assertTrue((qr.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));assertEquals(0,qr.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        qr.dismiss();activity.action(TouchTargets.MENU);click("Settings");click("Diagnostics");click("Send to phone");qr=ShadowDialog.getLatestDialog();
        controller.pause().stop();assertEquals(0,qr.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);assertFalse(active());assertNoServiceStart();controller.start().resume();
    }
}
