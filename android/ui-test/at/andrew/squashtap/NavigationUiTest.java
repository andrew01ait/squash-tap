package at.andrew.squashtap;

import android.app.Application;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;
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
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Real Android Activity/Dialogs/Buttons, using the same AAPT resources as the APK. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class,qualifiers="w233dp-h233dp-round")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class NavigationUiTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private static final class TestClock extends java.time.Clock {
        java.time.Instant now=java.time.Instant.parse("2026-11-03T09:00:00Z");
        @Override public java.time.ZoneId getZone(){return java.time.ZoneId.of("Europe/Vienna");}
        @Override public java.time.Clock withZone(java.time.ZoneId zone){return java.time.Clock.fixed(now,zone);}
        @Override public java.time.Instant instant(){return now;}
    }
    @Before public void launch(){
        TestClock clock=new TestClock();MatchBook book=new MatchBook(new MatchState("saved",3),clock);
        while(!book.current().complete()){
            for(int i=0;i<11;i++)book.add(0,LocalDate.of(2026,11,3));
            if(!book.current().complete()){clock.now=clock.now.plusSeconds(1500);book.nextGame();}
        }
        book.newMatch("active",3);book.add(0,LocalDate.of(2026,11,3));book.add(0,LocalDate.of(2026,11,3));book.add(1,LocalDate.of(2026,11,3));
        RuntimeEnvironment.getApplication().getSharedPreferences("squash",0).edit().clear().putString("match_book",book.encode()).putString(MatchDisplayPolicy.ACTIVE_MATCH,"active")
            .putBoolean("asked_match_notification",true).putLong("last_check",System.currentTimeMillis()).commit();
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();
        activity.action(TouchTargets.MENU);
    }
    @After public void finish(){
        Dialog dialog=ShadowDialog.getLatestDialog();if(dialog!=null)dialog.dismiss();
        if(controller!=null)controller.pause().stop().destroy();
    }
    private View root(){Dialog dialog=ShadowDialog.getLatestDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());return dialog.getWindow().getDecorView();}
    private List<View> descendants(View view){List<View> all=new ArrayList<>();all.add(view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)all.addAll(descendants(((ViewGroup)view).getChildAt(i)));return all;}
    private Button button(String text){for(View view:descendants(root()))if(view instanceof Button&&((Button)view).getText().toString().split("\n",2)[0].equals(text))return (Button)view;throw new AssertionError("Missing button: "+text);}
    private boolean hasButton(String text){for(View view:descendants(root()))if(view instanceof Button&&((Button)view).getText().toString().split("\n",2)[0].equals(text))return true;return false;}
    private boolean hasText(String text){for(View view:descendants(root()))if(view instanceof TextView&&((TextView)view).getText().toString().split("\n",2)[0].equals(text))return true;return false;}
    private boolean textContains(String text){for(View view:descendants(root()))if(view instanceof TextView&&((TextView)view).getText().toString().contains(text))return true;return false;}
    private int colour(Button button){assertNotNull(button.getBackgroundTintList());return button.getBackgroundTintList().getColorForState(button.getDrawableState(),0);}
    private void grey(Button button){int c=colour(button);assertEquals(Color.red(c),Color.green(c));assertEquals(Color.green(c),Color.blue(c));assertTrue(Color.alpha(c)>0);}
    private void blue(Button button){int c=button.getBackgroundTintList()==null?button.getCurrentTextColor():colour(button);assertTrue(Color.blue(c)>Color.red(c));}
    private void red(Button button){int c=colour(button);assertTrue(Color.red(c)>Color.blue(c));}
    private void outline(Button button){assertNull(button.getBackgroundTintList());GradientDrawable shape=(GradientDrawable)((RippleDrawable)button.getBackground()).getDrawable(0);assertEquals(0,Color.alpha(shape.getColor().getColorForState(button.getDrawableState(),Color.WHITE)));}
    private MatchBook saved(){return MatchBook.decode(activity.getSharedPreferences("squash",0).getString("match_book",""));}
    private void openResults(){button("Results (1)").performClick();}
    private void openSavedResult(){openResults();for(View view:descendants(root()))if(view instanceof LinearLayout&&view.getContentDescription()!=null&&view.getContentDescription().toString().contains("Open match result")){view.performClick();return;}throw new AssertionError("Missing result card");}

    @Test public void rootSeparatesMatchResultsAndGreySettings(){
        assertTrue(hasText("Menu"));assertFalse(hasText("Match menu"));blue(button("Current match"));blue(button("Results (1)"));grey(button("Settings"));outline(button("Back to score"));
        assertFalse(hasButton("Check for updates"));assertFalse(hasButton("Diagnostics"));assertFalse(hasButton("New match"));
    }
    @Test public void matchControlsContainNoAppSettings(){
        button("Current match").performClick();assertTrue(hasText("Current match"));blue(button("Match options"));blue(button("Enter set score"));blue(button("Pause match"));assertFalse(hasButton("New match"));outline(button("Back to Menu"));assertFalse(hasButton("Settings"));assertFalse(hasButton("Check for updates"));
    }
    @Test public void settingsStayNeutralGreyInPressedAndDisabledStates(){
        button("Settings").performClick();assertTrue(hasText("Settings"));Button updates=button("Check for updates");grey(updates);updates.setPressed(true);grey(updates);updates.setPressed(false);updates.setEnabled(false);grey(updates);
        grey(button("Keep screen on: ON"));grey(button("Diagnostics"));outline(button("Back to Menu"));outline(button("Scoreboard"));assertFalse(hasButton("New match"));assertFalse(hasButton("Results (1)"));
    }
    @Test public void updatePageUsesGreyControlsAndOutlinedBack(){
        button("Settings").performClick();ReflectionHelpers.setField(activity,"checking",true);button("Check for updates").performClick();assertTrue(hasText("App updates"));grey(button("Checking…"));grey(button("Phone install help"));outline(button("Back to Settings"));
    }
    @Test public void formatSelectorKeepsPointsThroughActualButtonNavigation(){
        button("Current match").performClick();button("Match options").performClick();button("Match length: Best of 3").performClick();blue(button("Best of 5 · first to 3"));button("Best of 5 · first to 3").performClick();MatchBook book=saved();assertEquals(5,book.current().bestOf());assertEquals(2,book.current().game().blue());assertEquals(1,book.current().game().red());assertEquals(1,book.count());
    }
    @Test public void singleDeleteAndConfirmationAreRedAndCancelKeepsResult(){
        openSavedResult();red(button("Delete this result"));button("Delete this result").performClick();red(button("Delete result"));outline(button("Cancel"));button("Cancel").performClick();assertEquals(1,saved().count());assertTrue(hasButton("Delete this result"));
    }
    @Test public void deleteAllUsesRedConfirmationAndPreservesActiveScore(){
        openResults();assertFalse(hasButton("Delete all results"));button("Options").performClick();red(button("Delete all results"));button("Delete all results").performClick();red(button("Delete all"));outline(button("Cancel"));button("Delete all").performClick();assertEquals(0,saved().count());assertEquals(2,saved().current().game().blue());assertEquals(1,saved().current().game().red());
    }
    @Test public void savedResultsAreCardsRatherThanGreyButtons(){
        openResults();for(View view:descendants(root()))if(view.getContentDescription()!=null&&view.getContentDescription().toString().contains("Open match result")){
            assertTrue(view instanceof LinearLayout);assertFalse(view instanceof Button);GradientDrawable shape=(GradientDrawable)((RippleDrawable)view.getBackground()).getDrawable(0);assertTrue(Color.blue(shape.getColor().getDefaultColor())>Color.red(shape.getColor().getDefaultColor()));return;
        }throw new AssertionError("Missing result card");
    }
    @Test public void resultUsesStartTimeTitleAndShowsFinishAndDuration(){
        openSavedResult();assertTrue(hasText("10:00"));assertFalse(hasText("Match 1"));assertTrue(textContains("Duration: 25 min 0 sec"));assertTrue(textContains("Date: 03.11.2026\nStart: 10:00\nEnd: 10:25"));assertTrue(textContains("Match 1"));assertFalse(textContains("Match 1 · 03.11.2026"));
    }
    @Test public void loadingLegacyBookCreatesBackupAndKeepsUnknownTimingHonest()throws Exception{
        ShadowDialog.getLatestDialog().dismiss();controller.pause().stop().destroy();
        String legacy=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("android/test-fixtures/book-v1-completed.txt")),java.nio.charset.StandardCharsets.UTF_8).trim();
        RuntimeEnvironment.getApplication().getSharedPreferences("squash",0).edit().putString("match_book",legacy).remove("match_book_v1_backup").commit();
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();activity.action(TouchTargets.MENU);
        assertEquals(legacy,activity.getSharedPreferences("squash",0).getString("match_book_v1_backup",""));assertTrue(activity.getSharedPreferences("squash",0).getString("match_book","").startsWith("B4:"));
        openSavedResult();assertTrue(hasText("Match result"));assertTrue(textContains("Duration: Not recorded"));assertTrue(textContains("Start: Not recorded"));assertTrue(textContains("End: Not recorded"));assertEquals(1,saved().count());assertEquals(11,saved().current().game().blue());
    }
}
