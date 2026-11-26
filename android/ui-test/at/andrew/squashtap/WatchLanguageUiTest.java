package at.andrew.squashtap;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, application=Application.class, qualifiers="de-rAT-w192dp-h192dp-round")
@LooperMode(LooperMode.Mode.PAUSED)
public class WatchLanguageUiTest {
    private Context context;
    private SharedPreferences prefs;
    private ActivityController<MainActivity> activity;

    @Before public void prepare(){
        context=RuntimeEnvironment.getApplication();
        prefs=context.getSharedPreferences("squash",Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @After public void finish(){close();}

    private void launch(){
        Intent intent=new Intent(Intent.ACTION_MAIN).setClass(context,MainActivity.class);
        activity=Robolectric.buildActivity(MainActivity.class,intent).setup();
    }

    private void close(){
        if(activity!=null){
            Dialog dialog=ShadowDialog.getLatestDialog();
            if(dialog!=null)dialog.dismiss();
            activity.pause().stop().destroy();
            activity=null;
        }
    }

    private List<TextView> texts(View root){
        List<TextView> list=new ArrayList<>();
        if(root instanceof TextView)list.add((TextView)root);
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++)list.addAll(texts(group.getChildAt(i)));
        }
        return list;
    }

    private TextView find(String label, boolean prefix){
        for(TextView view:texts(ShadowDialog.getLatestDialog().getWindow().getDecorView())){
            String shown=view.getText().toString();
            if(prefix?shown.startsWith(label):shown.equals(label))return view;
        }
        throw new AssertionError("Missing label "+label);
    }

    private void click(String label){
        TextView control=find(label,true);
        assertTrue(control instanceof Button);
        assertTrue(control.performClick());
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void usesFirstSupportedWatchLocaleAndEnglishFallback(){
        Configuration config=new Configuration(context.getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags("it-IT,es-MX,de-AT"));
        assertEquals("Elegir deporte",WatchLanguage.get(context.createConfigurationContext(config)).text("Choose sport"));
        config.setLocales(LocaleList.forLanguageTags("it-IT"));
        assertEquals("en",WatchLanguage.get(context.createConfigurationContext(config)).code);
        assertEquals("Tischtennis",WatchLanguage.get(context).text("Table tennis"));
    }

    @Test public void localeOverridesSurviveRestartAndCanReturnToAutomatic(){
        launch();
        assertNotNull(find("Startseite",false));
        click("Einstellungen");click("Sprache");click("Français");
        assertEquals("fr",prefs.getString(WatchLanguage.PREFERENCE,""));
        assertNotNull(find("✓ Français",false));
        click("Retour");assertNotNull(find("Réglages",false));
        close();launch();assertNotNull(find("Accueil",false));
        click("Réglages");click("Langue");click("Español");
        assertNotNull(find("Idioma",false));
        click("Automático (reloj)");click("Zurück");
        assertNotNull(find("Einstellungen",false));
    }

    @Test public void sportOrderAndSavedPlayerTitlesRespectTheLanguageBoundary(){
        prefs.edit().putString(WatchLanguage.PREFERENCE,"es").commit();
        String[] sorted=WatchLanguage.get(context).sortedSports();
        assertArrayEquals(new String[]{"badminton","padel","pickleball","squash","tennis","table-tennis"},sorted);
        OpponentDirectory directory=new OpponentDirectory();
        directory.add("Settings");prefs.edit().putString("opponent_book",directory.encode()).commit();
        launch();click("Jugadores");click("Settings");
        assertNotNull(find("Settings",false));
        assertNotNull(find("Cambiar nombre",false));
        assertEquals("{1} $ \\ / X",WatchLanguage.get(context).format("{0} / {1}","{0} / {1}","{0} / {1}","{0} / {1}","{1} $ \\","X"));
    }

    @Test public void refereeAndFollowerCanvasExposeOnlyTheReadOnlyAction(){
        MatchState match=new MatchState("shared",3);
        List<Integer> actions=new ArrayList<>();
        ScoreView board=new ScoreView(context,match,actions::add);
        board.ownName("Settings");
        board.opponent("No name");
        board.layout(0,0,240,240);
        board.remote("Schiedsrichter zählt",false);
        assertTrue(board.getContentDescription().toString().contains("Settings 0, No name 0"));
        assertTrue(board.getContentDescription().toString().contains("Partiemenü"));
        assertFalse(board.getContentDescription().toString().contains("zurücknehmen"));
        board.onTouchEvent(MotionEvent.obtain(10,10,MotionEvent.ACTION_DOWN,55,110,0));
        board.onTouchEvent(MotionEvent.obtain(10,40,MotionEvent.ACTION_UP,55,110,0));
        assertEquals(Collections.singletonList(TouchTargets.MENU),actions);
        board.remote("Endergebnis",true);
        assertTrue(board.getContentDescription().toString().contains("Ergebnis speichern"));
        board.performClick();
        assertEquals(Arrays.asList(TouchTargets.MENU,TouchTargets.MENU),actions);
        assertFalse(match.hasPoints());
        board.remote(null,false);
        assertFalse(board.getContentDescription().toString().contains("Mitlesen"));
    }
}
