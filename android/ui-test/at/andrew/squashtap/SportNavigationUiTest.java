package at.andrew.squashtap;

import android.app.*;import android.content.*;import android.os.Looper;import android.view.*;import android.widget.*;
import androidx.wear.widget.SwipeDismissFrameLayout;
import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.ActivityController;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowDialog;import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class,qualifiers="w233dp-h233dp-round") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.LEGACY)
public class SportNavigationUiTest {
    private ActivityController<MainActivity> controller;private MainActivity activity;private SharedPreferences prefs;
    @Before public void setup(){
        prefs=RuntimeEnvironment.getApplication().getSharedPreferences("squash",0);MatchBook book=new MatchBook(new MatchState("active",3));book.add(0);book.add(1);
        book.importResult(new MatchResult("history","2026-11-10",1,3,new int[][]{{11,4},{11,6}}));OpponentDirectory names=new OpponentDirectory();String jane=names.add("Jane");names.assign("active",jane);names.assign("history",jane);
        prefs.edit().clear().putString("match_book",book.encode()).putString("opponent_book",names.encode()).putString(MatchDisplayPolicy.ACTIVE_MATCH,"active").putBoolean("asked_match_notification",true).commit();
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();activity.action(TouchTargets.MENU);
    }
    @After public void finish(){Dialog d=ShadowDialog.getLatestDialog();if(d!=null)d.dismiss();controller.pause().stop().destroy();}
    private List<View> views(View root){List<View> out=new ArrayList<>();out.add(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)out.addAll(views(((ViewGroup)root).getChildAt(i)));return out;}
    private List<View> views(){return views(ShadowDialog.getLatestDialog().getWindow().getDecorView());}
    private Button find(String label){for(View v:views())if(v instanceof Button&&((Button)v).getText().toString().equals(label))return (Button)v;return null;}
    private Button button(String label){Button b=find(label);assertNotNull("Missing "+label,b);return b;}
    private void click(String label){button(label).performClick();shadowOf(Looper.getMainLooper()).idle();}
    private String text(){StringBuilder s=new StringBuilder();for(View v:views())if(v instanceof TextView)s.append(((TextView)v).getText()).append('\n');return s.toString();}
    private MatchBook book(){return MatchBook.decode(prefs.getString("match_book",""));}
    private void back(){ShadowDialog.getLatestDialog().cancel();shadowOf(Looper.getMainLooper()).idle();}
    private void cancelCurrent(){click("Current match");click("Cancel match");click("Discard match");}
    private void newMatch(String sport){cancelCurrent();click("Change sport");click(sport);click("New "+sport+" match");}
    private void swipe(){for(View v:views())if(v instanceof SwipeDismissFrameLayout){ReflectionHelpers.callInstanceMethod(v,"performDismissFinishedCallbacks");shadowOf(Looper.getMainLooper()).idle();return;}fail("Missing native swipe container");}
    @Test public void activeAndPausedMatchesLockEveryOtherSport(){
        String before=book().encode();click("Change sport");for(String sport:MatchFormat.SPORTS)assertEquals(sport.equals("squash"),button(MatchFormat.name(sport)).isEnabled());assertEquals(before,book().encode());
        click("Squash");click("Current match");click("Pause match");activity.action(TouchTargets.MENU);click("Change sport");assertFalse(button("Tennis").isEnabled());assertTrue(text().contains("Paused matches stay locked"));assertEquals(before,book().encode());
    }
    @Test public void cancelledMatchUnlocksSportSelectionAndTheNewSportHasNoOldScoreShortcut(){
        cancelCurrent();click("Change sport");click("Tennis");assertNotNull(button("Back to sports"));assertNull(find("Back to score"));click("Statistics");assertNull(find("Scoreboard"));back();back();assertTrue(button("Tennis").isEnabled());assertNotNull(button("Squash"));
    }
    @Test public void sharedPlayersAndSettingsReturnToSportsByButtonAndSystemBack(){
        click("Change sport");click("Players");assertTrue(text().contains("ALL SPORTS"));click("Back to sports");assertNotNull(button("Table tennis"));click("Settings");assertTrue(text().contains("ALL SPORTS"));back();assertNotNull(button("Table tennis"));assertNotNull(button("Close app"));
    }
    @Test public void sportContextAppearsInMenusSetupRulesAndHistory(){
        cancelCurrent();click("Change sport");click("Badminton");boolean found=false;for(View v:views())if("Current sport: Badminton".contentEquals(v.getContentDescription()==null?"":v.getContentDescription())){found=true;assertEquals(SportIcons.accent("badminton"),((TextView)v).getCurrentTextColor());}assertTrue(found);
        click("New Badminton match");assertTrue(text().contains("Badminton"));click("Rules: 21 points · cap 30");assertTrue(text().contains("Badminton"));assertNotNull(button("✓ 21 points · cap 30"));
    }
    @Test public void rulePickerCancellationLeavesTheDraftAndRunningMatchUntouched(){
        newMatch("Badminton");String before=book().encode();click("First server: Not set");click("Me");click("Rules: 21 points · cap 30");assertNotNull(button("15 points · cap 21"));back();assertNotNull(button("First server: Me"));assertNotNull(button("Rules: 21 points · cap 30"));assertEquals(before,book().encode());
    }
    @Test public void modePickerListsBothChoicesAndSwipePreservesDraftNames(){
        newMatch("Tennis");click("Opponent: No name (optional)");click("Jane");click("Mode: Singles");assertNotNull(button("✓ Singles"));assertNotNull(button("Doubles"));swipe();assertNotNull(button("Opponent: Jane (optional)"));assertNotNull(button("Mode: Singles"));
    }
    @Test public void reselectingTheCurrentRulePreservesInitialServingChoices(){
        newMatch("Badminton");click("First server: Not set");click("Me");click("Rules: 21 points · cap 30");click("✓ 21 points · cap 30");assertNotNull(button("✓ 21 points · cap 30"));click("Cancel");assertNotNull(button("First server: Me"));click("Mode: Singles");click("✓ Singles");assertNotNull(button("✓ Singles"));click("Cancel");assertNotNull(button("First server: Me"));
    }
    @Test public void tennisDeciderAndDeuceUseListsRatherThanCycling(){
        newMatch("Tennis");click("Deuce: Advantage");assertNotNull(button("✓ Advantage"));click("No-Ad");click("Decider: Full set");assertNotNull(button("✓ Full set"));click("Match tie-break to 10");click("Create match");assertEquals("no-ad",book().current().format().deuce);assertTrue(book().current().format().finalTie);
    }
    @Test public void keepPlayingAndSwipeOutOfDiscardPreserveSessionAndAllData(){
        String before=book().encode(),names=prefs.getString("opponent_book","");click("Current match");click("Cancel match");assertTrue(text().contains("No result, win/loss"));click("Keep playing");click("Cancel match");swipe();assertEquals(before,book().encode());assertEquals(names,prefs.getString("opponent_book",""));assertEquals("active",prefs.getString(MatchDisplayPolicy.ACTIVE_MATCH,""));assertNotNull(button("Pause match"));
    }
    @Test public void confirmedDiscardStopsSessionKeepsHistoryAndCannotBeUndoneOrResumed(){
        click("Current match");click("Cancel match");int color=button("Discard match").getBackgroundTintList().getDefaultColor();assertTrue(android.graphics.Color.red(color)>android.graphics.Color.blue(color));click("Discard match");
        MatchBook book=book();assertNotEquals("active",book.current().id());assertFalse(book.current().hasPoints());assertFalse(book.undo());assertNull(book.result("active"));assertNotNull(book.result("history"));assertEquals(1,book.count());assertFalse(prefs.contains(MatchDisplayPolicy.ACTIVE_MATCH));assertFalse(prefs.getBoolean("match_paused",true));assertFalse(prefs.contains("match_ready_id"));
        OpponentDirectory names=OpponentDirectory.decode(prefs.getString("opponent_book",""));assertEquals("",names.nameFor("active"));assertEquals("Jane",names.nameFor("history"));assertEquals(1,names.list(false).size());assertNotNull(button("New Squash match"));assertNull(find("Current match"));click("Back to sports");assertNull(find("Resume Squash"));
    }
    @Test public void anOldDiscardCallbackCannotDiscardAChangedMatch(){
        click("Current match");click("Cancel match");Button stale=button("Discard match");MatchBook inMemory=ReflectionHelpers.getField(activity,"book");inMemory.add(0);stale.performClick();assertTrue(text().contains("Match changed"));assertEquals("active",book().current().id());assertNotNull(button("Back"));
    }
    @Test public void completedMatchesOfferResultsAndDoNotOfferDiscard(){
        MatchBook inMemory=ReflectionHelpers.getField(activity,"book");inMemory.retire(1);ReflectionHelpers.callInstanceMethod(activity,"afterMatchChange");click("Current match");assertNull(find("Cancel match"));assertNotNull(button("View result"));
    }
    @Test public void screenSettingHasExplicitChoicesAndBackKeepsTheSelection(){
        click("Settings");click("Keep screen on: ON");assertNotNull(button("✓ Keep screen on"));click("Back to Settings");assertFalse(prefs.contains("match_keep_awake_v3"));click("Keep screen on: ON");click("Allow screen to sleep");assertFalse(prefs.getBoolean("match_keep_awake_v3",true));assertNotNull(button("✓ Allow screen to sleep"));click("Back to Settings");assertNotNull(button("Keep screen on: OFF"));
    }
    @Test public void completedMatchUnlocksOtherSports(){
        MatchBook inMemory=ReflectionHelpers.getField(activity,"book");inMemory.retire(1);ReflectionHelpers.callInstanceMethod(activity,"afterMatchChange");click("Change sport");assertTrue(button("Tennis").isEnabled());click("Tennis");assertNotNull(button("New Tennis match"));
    }
    @Test public void evenAPreviouslyOpenedSportButtonCannotSwitchAfterPlayStarts(){
        cancelCurrent();click("Change sport");Button stale=button("Tennis");assertTrue(stale.isEnabled());MatchBook inMemory=ReflectionHelpers.getField(activity,"book");inMemory.add(0);ReflectionHelpers.callInstanceMethod(activity,"afterMatchChange");String before=book().encode();stale.performClick();assertTrue(text().contains("Match still running"));assertEquals(before,book().encode());assertEquals("squash",ReflectionHelpers.getField(activity,"selectedSport"));
    }
    @Test public void staleNewSportCreationCannotReplaceAMatchThatHasSinceStarted(){
        newMatch("Tennis");Button stale=button("Create match");MatchBook inMemory=ReflectionHelpers.getField(activity,"book");inMemory.add(0);ReflectionHelpers.callInstanceMethod(activity,"afterMatchChange");String before=book().encode();stale.performClick();assertTrue(text().contains("Match still running"));assertEquals(before,book().encode());assertEquals("squash",book().current().format().sport);
    }
    @Test public void aCreatedUnplayedMatchAlsoStaysLockedAcrossRelaunch(){
        newMatch("Tennis");click("Create match");assertFalse(book().current().hasPoints());activity.action(TouchTargets.MENU);click("Change sport");assertFalse(button("Squash").isEnabled());ShadowDialog.getLatestDialog().dismiss();controller.pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity.class,new Intent(Intent.ACTION_MAIN)).setup();activity=controller.get();assertFalse(button("Badminton").isEnabled());assertTrue(button("Tennis").isEnabled());
    }

}
