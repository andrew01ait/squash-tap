package at.andrew.squashtap;

import android.app.Application;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
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

/** Real view actions verify staged optional names never overwrite the old match. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class,qualifiers="w233dp-h233dp-round")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class NewMatchOpponentUiTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private SharedPreferences preferences;
    @Before public void launch(){
        MatchBook book=new MatchBook(new MatchState("current",3));book.add(0);book.add(0);book.add(1);
        book.importResult(new MatchResult("history","2026-11-04",1,3,new int[][]{{11,4},{11,6}}));
        OpponentDirectory directory=new OpponentDirectory();String alice=directory.add("Alice");directory.add("Martin");directory.assign("current",alice);directory.assign("history",alice);
        preferences=RuntimeEnvironment.getApplication().getSharedPreferences("squash",0);
        preferences.edit().clear().putString("match_book",book.encode()).putString("opponent_book",directory.encode()).putString(MatchDisplayPolicy.ACTIVE_MATCH,"current").putBoolean("asked_match_notification",true).commit();
        launchActivity();
    }
    private void launchActivity(){controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();}
    private void closeActivity(){Dialog dialog=ShadowDialog.getLatestDialog();if(dialog!=null)dialog.dismiss();if(controller!=null){controller.pause().stop().destroy();controller=null;}}
    @After public void finish(){closeActivity();}
    private List<View> views(View view){List<View> out=new ArrayList<>();out.add(view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)out.addAll(views(((ViewGroup)view).getChildAt(i)));return out;}
    private List<View> views(){Dialog dialog=ShadowDialog.getLatestDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());return views(dialog.getWindow().getDecorView());}
    private Button button(String text){for(View view:views())if(view instanceof Button&&((Button)view).getText().toString().split("\n",2)[0].equals(text))return (Button)view;throw new AssertionError("Missing button: "+text);}
    private void click(String text){button(text).performClick();}
    private boolean contains(String text){for(View view:views())if(view instanceof TextView&&((TextView)view).getText().toString().contains(text))return true;return false;}
    private MatchBook book(){return MatchBook.decode(preferences.getString("match_book",""));}
    private OpponentDirectory directory(){return OpponentDirectory.decode(preferences.getString("opponent_book",""));}
    private void openNew(){activity.action(TouchTargets.NEW);}
    private void chooseMartin(){click("Opponent: No name (optional)");click("Martin");}
    private void person(Button button){int c=button.getBackgroundTintList().getColorForState(button.getDrawableState(),0);assertTrue(Color.red(c)>Color.green(c));assertTrue(Color.blue(c)>Color.green(c));assertNotNull(button.getCompoundDrawables()[0]);assertEquals(Gravity.START,button.getGravity()&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK);}
    private void idleNewMatch(){assertFalse(preferences.contains(MatchDisplayPolicy.ACTIVE_MATCH));assertFalse(book().current().hasPoints());assertEquals(0,book().currentStartedAt());}

    @Test public void savedNamesHaveDistinctPersonStyleInNormalPressedAndDisabledStates(){
        activity.action(TouchTargets.MENU);click("Players");Button name=button("Martin"),action=button("Add player");assertNotEquals(action.getBackgroundTintList().getDefaultColor(),name.getBackgroundTintList().getDefaultColor());person(name);name.setPressed(true);person(name);name.setPressed(false);name.setEnabled(false);person(name);assertNull(button("Back to Menu").getBackgroundTintList());
    }
    @Test public void currentAndFilterNameSelectorsUseThePersonStyle(){
        activity.action(TouchTargets.MENU);click("Current match");click("Match options");person(button("Opponent: Alice"));click("Opponent: Alice");person(button("Alice"));person(button("No name"));click("Cancel");click("Back to Current match");click("Back to Menu");click("Results (1)");click("Filters");person(button("Opponent: All"));click("Opponent: All");person(button("All opponents"));person(button("Unnamed"));person(button("Martin"));
    }
    @Test public void nameCanBeSkippedAndPreviousOpponentIsNotInherited(){
        openNew();person(button("Opponent: No name (optional)"));click("Create match");assertTrue(contains("Opponent: No name"));click("Replace & create match");assertEquals("",directory().nameFor(book().current().id()));assertEquals("Alice",directory().nameFor("history"));assertEquals(1,book().count());idleNewMatch();
    }
    @Test public void choosingNameIsStagedThenCommittedToNewBestOfFiveAndFutureResult(){
        String before=book().encode(),names=directory().encode();openNew();chooseMartin();assertEquals(before,book().encode());assertEquals(names,directory().encode());assertNotNull(button("Opponent: Martin (optional)"));click("Best of 5");click("Create match");assertEquals(before,book().encode());assertTrue(contains("Opponent: Martin"));click("Replace & create match");
        MatchBook next=book();assertEquals(5,next.current().bestOf());assertNotEquals("current",next.current().id());assertEquals("Martin",directory().nameFor(next.current().id()));assertEquals("Alice",directory().nameFor("history"));idleNewMatch();
        while(!next.current().complete()){for(int i=0;i<11;i++)next.add(0);if(!next.current().complete())next.nextGame();}
        boolean named=false;for(MatchCsv.Row row:MatchCsv.parse(MatchCsv.export(next.newestFirst(),directory())))if(row.result.id.equals(next.current().id())){assertEquals("Martin",row.name);named=true;}assertTrue(named);
    }
    @Test public void cancellingSetupKeepsCurrentScoreOpponentAndSession(){
        String before=book().encode(),names=directory().encode();openNew();chooseMartin();click("Cancel");assertEquals(before,book().encode());assertEquals(names,directory().encode());assertEquals("current",preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""));openNew();assertNotNull(button("Opponent: No name (optional)"));
    }
    @Test public void cancellingReplacementDoesNotAssignTheStagedName(){
        String before=book().encode(),names=directory().encode();openNew();chooseMartin();click("Create match");click("Back to setup");click("Cancel");assertEquals(before,book().encode());assertEquals(names,directory().encode());assertEquals("Alice",directory().nameFor("current"));
    }
    @Test public void cancellingPickerKeepsItsPreviousDraftSelection(){
        String before=book().encode();openNew();chooseMartin();click("Opponent: Martin (optional)");click("Cancel");assertNotNull(button("Opponent: Martin (optional)"));assertEquals(before,book().encode());assertEquals("Alice",directory().nameFor("current"));
    }
    @Test public void chosenNameCanBeClearedBeforeStarting(){
        openNew();chooseMartin();click("Opponent: Martin (optional)");click("No name");click("Best of 5");click("Create match");click("Replace & create match");assertEquals("",directory().nameFor(book().current().id()));assertEquals(5,book().current().bestOf());idleNewMatch();
    }
    @Test public void newNameCanBeAddedAndSelectedDuringSetupWithoutChangingOldOpponent(){
        String before=book().encode();openNew();click("Opponent: No name (optional)");click("Add player");for(View view:views())if(view instanceof EditText)((EditText)view).setText("Susi");click("Save name");assertNotNull(button("Opponent: Susi (optional)"));assertEquals(before,book().encode());assertEquals("Alice",directory().nameFor("current"));click("Create match");click("Replace & create match");assertEquals("Susi",directory().nameFor(book().current().id()));idleNewMatch();
    }
    @Test public void freshEmptyDirectoryDoesNotRequireCreatingAName(){
        closeActivity();MatchBook fresh=new MatchBook(new MatchState("empty",3));preferences.edit().clear().putString("match_book",fresh.encode()).putBoolean("asked_match_notification",true).commit();launchActivity();openNew();click("Opponent: No name (optional)");click("No name");click("Create match");assertTrue(directory().list(true).isEmpty());assertEquals("",directory().nameFor(book().current().id()));idleNewMatch();
    }
}
