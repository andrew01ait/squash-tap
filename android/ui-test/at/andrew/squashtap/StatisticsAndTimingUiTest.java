package at.andrew.squashtap;

import android.app.Application;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class,qualifiers="w233dp-h233dp-round")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class StatisticsAndTimingUiTest {
    private ActivityController<MainActivity> controller;private MainActivity activity;private SharedPreferences preferences;
    @Before public void prepare(){
        preferences=RuntimeEnvironment.getApplication().getSharedPreferences("squash",0);save(history(false));launch();
    }
    private MatchBook history(boolean overnight){
        MatchBook b=new MatchBook(new MatchState("current",3));b.add(0);b.add(1);
        long start=Instant.parse(overnight?"2026-11-07T22:50:00Z":"2026-11-07T18:30:00Z").toEpochMilli();
        long end=Instant.parse(overnight?"2026-11-07T23:20:00Z":"2026-11-07T19:15:00Z").toEpochMilli();
        b.importResult(new MatchResult("won","2026-11-07",1,3,new int[][]{{11,4},{11,6}},start,end,"Europe/Vienna"));b.importResult(new MatchResult("lost","2026-11-06",1,5,new int[][]{{0,11},{2,11},{3,11}}));return b;
    }
    private void save(MatchBook b){OpponentDirectory names=new OpponentDirectory();String name=names.add("Martin");names.assign("won",name);names.assign("lost",name);preferences.edit().clear().putString("match_book",b.encode()).putString("opponent_book",names.encode()).putBoolean("asked_match_notification",true).commit();}
    private void launch(){controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();}
    private void close(){Dialog d=ShadowDialog.getLatestDialog();if(d!=null)d.dismiss();if(controller!=null){controller.pause().stop().destroy();controller=null;}}
    @After public void finish(){close();}
    private List<View> views(View v){List<View> all=new ArrayList<>();all.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)all.addAll(views(((ViewGroup)v).getChildAt(i)));return all;}
    private List<View> views(){Dialog d=ShadowDialog.getLatestDialog();assertNotNull(d);assertTrue(d.isShowing());return views(d.getWindow().getDecorView());}
    private Button button(String text){for(View v:views())if(v instanceof Button&&((Button)v).getText().toString().equals(text))return (Button)v;throw new AssertionError("Missing button: "+text);}
    private void click(String text){button(text).performClick();}
    private String text(){StringBuilder out=new StringBuilder();for(View v:views())if(v instanceof TextView)out.append(((TextView)v).getText()).append('\n');return out.toString();}
    private void stats(){activity.action(TouchTargets.MENU);click("Statistics");}
    private void openWon(String prefix){activity.action(TouchTargets.MENU);click("Results (2)");for(View v:views())if(v.isClickable()&&v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith(prefix)){v.performClick();return;}throw new AssertionError("Missing saved result");}
    @Test public void statisticsShowOverallMatchesSetsAndPointsWithoutChangingTheMatch(){
        String saved=preferences.getString("match_book",""),names=preferences.getString("opponent_book","");shadowOf(RuntimeEnvironment.getApplication()).clearStartedServices();stats();String text=text();
        assertTrue(text.contains("Won: 1 · Lost: 1"));assertTrue(text.contains("Win rate: 50%"));assertTrue(text.contains("Sets (games)"));assertTrue(text.contains("Won: 2 · Lost: 3"));assertTrue(text.contains("For: 27 · Against: 43"));assertTrue(text.contains("Balance: -16"));assertTrue(text.contains("Played: 70"));
        assertEquals(saved,preferences.getString("match_book",""));assertEquals(names,preferences.getString("opponent_book",""));assertFalse(preferences.contains(MatchDisplayPolicy.ACTIVE_MATCH));assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());assertNull(button("Back to Menu").getBackgroundTintList());click("Scoreboard");assertFalse(ShadowDialog.getLatestDialog().isShowing());
    }
    @Test public void emptyStatisticsHaveHelpfulTextAndNoMisleadingWinRate(){
        close();save(new MatchBook(new MatchState("empty",3)));launch();stats();assertTrue(text().contains("No saved matches yet"));assertTrue(text().contains("Played: 0"));assertTrue(text().contains("Win rate: —"));assertFalse(text().contains("NaN"));
    }
    @Test public void existingResultFiltersDoNotNarrowOverallStatisticsOrGetReset(){
        activity.action(TouchTargets.MENU);click("Results (2)");click("Filters");click("Opponent: All");click("Unnamed");click("Back to Results");assertTrue(text().contains("No matches fit"));click("Back to Menu");click("Statistics");assertTrue(text().contains("Played: 2"));assertTrue(text().contains("result filters do not apply"));click("Results (2)");assertTrue(text().contains("No matches fit"));
    }
    @Test public void deletingAResultUpdatesTheNextStatisticsView(){
        openWon("07.11.2026 · 19:30");click("Delete this result");click("Delete result");click("Back to Menu");click("Statistics");assertTrue(text().contains("Won: 0 · Lost: 1"));assertTrue(text().contains("Win rate: 0%"));assertTrue(text().contains("For: 5 · Against: 33"));
    }
    @Test public void completedDetailDisplaysTheDateOnceAndKeepsTimesDurationAndScores(){
        openWon("07.11.2026 · 19:30");String text=text();assertEquals(1,text.split("07\\.11\\.2026",-1).length-1);assertTrue(text.contains("Start: 19:30\nEnd: 20:15"));assertTrue(text.contains("Duration: 45 min 0 sec"));assertTrue(text.contains("Set 1: 11–4\nSet 2: 11–6"));assertTrue(text.contains("Match 1"));assertFalse(text.contains("CET"));
    }
    @Test public void overnightDetailDisplaysBothDatesWithoutRepeatingTheStartDate(){
        close();save(history(true));launch();openWon("07.11.2026 · 23:50");String text=text();assertEquals(1,text.split("07\\.11\\.2026",-1).length-1);assertTrue(text.contains("Start: 23:50\nEnd: 08.11.2026 · 00:20"));
    }
    @Test public void legacyDetailDoesNotInventTimes(){
        activity.action(TouchTargets.MENU);click("Results (2)");for(View v:views())if(v.isClickable()&&v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith("06.11.2026")){v.performClick();break;}assertTrue(text().contains("Date: 06.11.2026\nStart: Not recorded\nEnd: Not recorded"));assertTrue(text().contains("Duration: Not recorded"));
    }
}
