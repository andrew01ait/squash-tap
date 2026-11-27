package at.andrew.squashtap;

import android.app.*;import android.content.*;import android.graphics.Color;import android.os.Looper;import android.view.*;import android.widget.*;
import androidx.wear.widget.SwipeDismissFrameLayout;
import java.util.*;import java.util.concurrent.*;import java.time.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.ActivityController;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowDialog;import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;

/** Real native views exercise the revised destinations, caller returns and preserved match state. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class,qualifiers="w233dp-h233dp-round") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.LEGACY)
public class MenuRevisionUiTest {
    private ActivityController<MainActivity> controller;private MainActivity activity;private SharedPreferences preferences;private QueuedTasks tasks;
    private static class QueuedTasks extends AbstractExecutorService {
        final List<Runnable> queued=new ArrayList<>();boolean closed;
        public void execute(Runnable r){queued.add(r);}public void shutdown(){closed=true;}public List<Runnable> shutdownNow(){closed=true;return queued;}public boolean isShutdown(){return closed;}public boolean isTerminated(){return closed;}public boolean awaitTermination(long t,TimeUnit u){return closed;}
    }
    @Before public void prepare(){
        preferences=RuntimeEnvironment.getApplication().getSharedPreferences("squash",0);
        MatchBook b=new MatchBook(new MatchState("current",3));b.add(0);b.add(1);OpponentDirectory d=new OpponentDirectory();String alice=d.add("Alice"),bob=d.add("Bob");d.assign("current",alice);
        for(int i=0;i<17;i++){String id="history-"+i;b.importResult(new MatchResult(id,"2026-11-06",i+1,3,new int[][]{{11,7},{11,8}}));d.assign(id,i<12?alice:bob);}
        preferences.edit().clear().putString("match_book",b.encode()).putString("opponent_book",d.encode()).putString(MatchDisplayPolicy.ACTIVE_MATCH,"current").putBoolean("asked_match_notification",true).commit();launch();
    }
    private void launch(){controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();tasks=new QueuedTasks();ReflectionHelpers.setField(activity,"tasks",tasks);}
    private void close(){Dialog d=ShadowDialog.getLatestDialog();if(d!=null)d.dismiss();if(controller!=null){controller.pause().stop().destroy();controller=null;}}
    @After public void finish(){close();}
    private Dialog dialog(){Dialog d=ShadowDialog.getLatestDialog();assertNotNull(d);assertTrue(d.isShowing());return d;}
    private List<View> all(View root){List<View> out=new ArrayList<>();out.add(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)out.addAll(all(((ViewGroup)root).getChildAt(i)));return out;}
    private List<View> all(){return all(dialog().getWindow().getDecorView());}
    private Button button(String text){for(View v:all())if(v instanceof Button&&((Button)v).getText().toString().split("\n",2)[0].equals(text))return (Button)v;throw new AssertionError("Missing "+text);}
    private boolean has(String text){for(View v:all())if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;return false;}
    private boolean hasButton(String text){for(View v:all())if(v instanceof Button&&((Button)v).getText().toString().split("\n",2)[0].equals(text))return true;return false;}
    private void click(String text){button(text).performClick();shadowOf(Looper.getMainLooper()).idle();}
    private MatchBook book(){return MatchBook.decode(preferences.getString("match_book",""));}
    private void root(){activity.action(TouchTargets.MENU);}
    private void match(){root();click("Current match");}
    private void results(){root();click("Results ("+book().count()+")");}
    private void back(){Dialog d=dialog();d.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));d.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));shadowOf(Looper.getMainLooper()).idle();}
    private EditText playerSearch(){
        for(View view:all())if(view instanceof EditText && activity.tr("Search players").contentEquals(((EditText)view).getHint()))return (EditText)view;
        throw new AssertionError("No player search");
    }
    private MenuLocation location(){return ReflectionHelpers.getField(activity,"location");}
    @Test public void playerSearchFiltersAcrossPagesKeepsFocusAndRestoresAfterDetail(){
        close();OpponentDirectory d=OpponentDirectory.decode(preferences.getString("opponent_book",""));
        for(int i=0;i<14;i++)d.add("Player "+i);
        d.add("Zoë Müller");
        preferences.edit().putString("opponent_book",d.encode()).commit();launch();root();click("Players");
        Dialog roster=dialog();EditText input=playerSearch();input.requestFocus();
        input.setText("muller");assertSame(roster,dialog());assertSame(input,playerSearch());assertTrue(input.hasFocus());
        assertTrue(hasButton("Zoë Müller"));assertFalse(hasButton("Alice"));assertFalse(hasButton("More names"));
        click("Zoë Müller");back();assertEquals("muller",playerSearch().getText().toString());
        playerSearch().setText("missing");assertTrue(has("No matching players"));assertTrue(hasButton("Add player"));
        playerSearch().setText("");click("Sort: A–Z");click("Z–A");
        assertTrue(all().indexOf(button("Zoë Müller"))<all().indexOf(button("Player 9")));
        click("More names");assertFalse(hasButton("Zoë Müller"));click("Previous names");assertTrue(hasButton("Zoë Müller"));
        back();click("Players");assertEquals("",playerSearch().getText().toString());assertTrue(hasButton("Sort: Z–A"));
        click("Sort: Z–A");click("Most recent activity");
        assertTrue(all().indexOf(button("Alice"))<all().indexOf(button("Bob")));
        back();click("Current match");click("Opponent: Alice");assertTrue(hasButton("Sort: Most recent activity"));
        playerSearch().setText("bob");click("Bob");assertTrue(hasButton("Opponent: Bob"));
    }

    @Test public void recentFilterCombinesWithSearchAndSortAndSurvivesOpeningAPlayer(){
        close();
        MatchBook b=new MatchBook(new MatchState("current",3));
        OpponentDirectory d=new OpponentDirectory();
        String ada=d.add("Ada"),bob=d.add("Bob"),cara=d.add("Cara"),unused=d.add("Unused");
        LocalDate today=LocalDate.now();
        String[] people={ada,bob,cara};int[] ages={30,29,0};
        for(int i=0;i<people.length;i++){
            String id="past-"+i;
            b.importResult(new MatchResult(id,today.minusDays(ages[i]).toString(),1,3,new int[][]{{11,5},{11,6}}));
            d.assign(id,people[i]);
        }
        preferences.edit().putString("match_book",b.encode()).putString("opponent_book",d.encode()).commit();
        launch();root();click("Players");
        click("Last 30 days");assertTrue(hasButton("Bob"));assertTrue(hasButton("Cara"));
        assertFalse(hasButton("Ada"));assertFalse(hasButton("Unused"));
        click("Sort: A–Z");click("Most recent activity");
        assertTrue(all().indexOf(button("Cara"))<all().indexOf(button("Bob")));
        playerSearch().setText("bo");click("Bob");back();
        assertTrue(((CheckBox)button("Last 30 days")).isChecked());assertEquals("bo",playerSearch().getText().toString());
        playerSearch().setText("");click("Last 30 days");assertTrue(hasButton("Unused"));assertTrue(hasButton("Ada"));
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="w192dp-h192dp-round-xhdpi")
    public void playerToolsAndSavedPeopleHaveDistinctGroupsOnASmallRoundWatch() throws Exception {
        close();
        RuntimeEnvironment.setFontScale(1.3f);
        preferences.edit().putString(WatchLanguage.PREFERENCE,"de").commit();
        launch();root();click("Spieler");

        Button sort=button("Sortierung: A–Z"), player=button("Alice");
        assertSecondaryLink(sort);
        View tools=(View)sort.getParent();
        assertSame(tools,playerSearch().getParent());
        assertSame(tools,button(activity.tr("Last 30 days")).getParent());
        assertNotSame(tools,player.getParent());
        assertEquals(Arrays.asList("Suchen & sortieren","Gespeicherte Spieler","Namen verwalten"),sections());
        TextView saved=all().stream().filter(v->v instanceof TextView && v.isAccessibilityHeading()
            &&"Gespeicherte Spieler".contentEquals(((TextView)v).getText())).map(v->(TextView)v).findFirst().get();
        assertTrue(all().indexOf(sort)<all().indexOf(saved));
        assertTrue(all().indexOf(saved)<all().indexOf(player));
        android.graphics.drawable.RippleDrawable background=(android.graphics.drawable.RippleDrawable)player.getBackground();
        assertEquals(MenuSection.PLAYERS.playerRow,
            ((android.graphics.drawable.GradientDrawable)background.getDrawable(0)).getColor().getDefaultColor());
        assertNotNull(player.getCompoundDrawablesRelative()[0]);
        assertNotNull(player.getCompoundDrawablesRelative()[2]);

        assertEquals(0,sort.getCompoundDrawablesRelative()[2].getLevel());
        click("Sortierung: A–Z");
        assertEquals(10000,sort.getCompoundDrawablesRelative()[2].getLevel());
        Button recent=button(activity.tr("Most recent activity"));
        assertSecondaryFill(recent);
        assertNull(recent.getCompoundDrawablesRelative()[2]);
        Button selected=button("✓ A–Z");
        assertTrue(selected.isSelected());
        int selectedFill=buttonFill(selected).getColorForState(selected.getDrawableState(),Color.TRANSPARENT);
        assertTrue(Color.luminance(selectedFill)>Color.luminance(buttonFill(recent).getDefaultColor()));
        click("Sortierung: A–Z");
        assertEquals(0,sort.getCompoundDrawablesRelative()[2].getLevel());
        layout();
        View screen=dialog().getWindow().getDecorView();
        for(Button control:new Button[]{sort,player}){
            centerOnScreen(screen,control);
            assertInsideRound(screen,control);
            assertLabelInsideRound(screen,control);
        }
        centerOnScreen(screen,saved);
        captureRoundScreen(screen,screen.getWidth(),"player-groups-round-192-de");
    }

    private void centerOnScreen(View screen,View item){
        int[] position=new int[2],window=new int[2];
        item.getLocationOnScreen(position);screen.getLocationOnScreen(window);
        scroll().scrollTo(0,scroll().getScrollY()+position[1]-window[1]+item.getHeight()/2-screen.getHeight()/2);
    }

    private void mainLaunch(){
        controller=Robolectric.buildActivity(MainActivity.class,new Intent(Intent.ACTION_MAIN)).setup();
        activity=controller.get();tasks=new QueuedTasks();ReflectionHelpers.setField(activity,"tasks",tasks);
    }
    private MatchBook completedAt(long millis){
        MatchBook b=new MatchBook(new MatchState("final-review",3),Clock.fixed(Instant.ofEpochMilli(millis),ZoneId.of("UTC")));
        b.add(0);assertTrue(b.retire(1));return b;
    }
    private void saveFixture(MatchBook b){
        preferences.edit().putString("match_book",b.encode()).remove(MatchDisplayPolicy.ACTIVE_MATCH).remove("match_ready_id")
                .putBoolean("match_paused",false).commit();
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="w192dp-h192dp-round-xhdpi")
    public void notesCanBeEditedOnCurrentAndSavedMatches() throws Exception{
        RuntimeEnvironment.setFontScale(1.3f);
        String active=book().current().encode();
        String directory=preferences.getString("opponent_book","");
        match();click("Match details");click("Add match notes");
        noteField("Note").setText("First point\nTry the backhand 📝");
        noteField("Opponent's club or team").setText("Club Nord");
        noteField("Event or league").setText("Landesliga");
        click("Save match notes");
        assertTrue(has("Try the backhand 📝"));
        assertEquals(new MatchNotes("First point\nTry the backhand 📝","Club Nord","Landesliga"),book().notes("current"));
        assertEquals(active,book().current().encode());
        assertEquals(directory,preferences.getString("opponent_book",""));
        click("Edit match notes");noteField("Note").setText("Discard this");click("Cancel");
        assertFalse(has("Discard this"));
        close();launch();match();click("Match details");click("Match notes");
        assertTrue(has("Club Nord"));assertTrue(has("Landesliga"));
        results();openFirstResult();click("Add match notes");
        noteField("Event or league").setText("Tuesday tournament");click("Save match notes");
        assertTrue(has("Tuesday tournament"));
        click("Back to Result");back();
        assertTrue(has("Tuesday tournament"));
        assertEquals(active,book().current().encode());
    }
    private EditText noteField(String title){
        return (EditText)all().stream().filter(v->v instanceof EditText&&title.equals(String.valueOf(v.getContentDescription())))
            .findFirst().orElseThrow(()->new AssertionError("Missing note field: "+title));
    }
    @Test public void closingResultOpensHomeAndKeepsTheSavedScoreAndAllHistory(){
        close();MatchBook b=completedAt(System.currentTimeMillis());saveFixture(b);String saved=b.encode();
        mainLaunch();assertTrue(hasButton("Quick rematch"));click("Mark match as done");assertTrue(hasButton("Sports"));
        assertEquals(saved,book().encode());assertFalse(MatchWidgets.read(activity).present);
        click("Results");openFirstResult();assertFalse(hasButton("Mark match as done"));back();back();
        click("New match");click("Tennis");assertTrue(hasButton("Create match"));back();back();
        close();mainLaunch();assertTrue(hasButton("Sports"));assertEquals(saved,book().encode());
        click("Players");back();click("Settings");back();click("Sports");click("Back to Home");assertTrue(hasButton("New match"));
    }
    @Test public void aSavedSharedResultCanBeClosedAndNeverSupplantsAPreparedLocalMatch(){
        close();MatchBook b=new MatchBook(new MatchState("idle",3));
        b.importResult(new MatchResult("remote","2026-10-01",1,3,new int[][]{{11,5},{11,6}}));
        saveFixture(b);mainLaunch();activity.openSavedRoomResult("remote","squash");
        assertTrue(hasButton("Mark match as done"));assertFalse(hasButton("Quick rematch"));
        close();mainLaunch();assertTrue(hasButton("Mark match as done"));click("Mark match as done");
        assertTrue(hasButton("Sports"));assertNotNull(book().result("remote"));
        click("New match");click("Squash");click("Create match");
        activity.openSavedRoomResult("remote","squash");assertFalse(hasButton("Mark match as done"));
        close();mainLaunch();assertEquals("Scoreboard",ReflectionHelpers.getField(activity,"panelPage"));
    }
    @Test public void threeHoursAreMeasuredFromTheLastInteractionAcrossRestarts(){
        close();long now=System.currentTimeMillis();MatchBook b=completedAt(now-FinishedMatchReview.TIMEOUT-10000);
        saveFixture(b);FinishedMatchReview.touch(preferences,b,now-60000);mainLaunch();assertTrue(hasButton("Mark match as done"));
        long previous=now-60000;FinishedMatchReview.touch(preferences,b,previous);
        android.view.MotionEvent touch=android.view.MotionEvent.obtain(5,5,android.view.MotionEvent.ACTION_DOWN,90,75,0);
        dialog().dispatchTouchEvent(touch);touch.recycle();assertTrue(FinishedMatchReview.lastUsed(preferences,b)>previous);
        assertFalse(FinishedMatchReview.expired(preferences,b,FinishedMatchReview.lastUsed(preferences,b)+FinishedMatchReview.TIMEOUT-1));
        assertTrue(FinishedMatchReview.expired(preferences,b,FinishedMatchReview.lastUsed(preferences,b)+FinishedMatchReview.TIMEOUT));
        close();FinishedMatchReview.touch(preferences,b,now-FinishedMatchReview.TIMEOUT);mainLaunch();
        assertTrue(hasButton("Sports"));assertNotNull(book().result("final-review"));assertFalse(MatchWidgets.read(activity).present);
    }
    @Test public void expiredReviewLeavesAnOpenNewMatchDraftAloneAndActiveMatchesBypassHome(){
        close();long now=System.currentTimeMillis();MatchBook b=completedAt(now-FinishedMatchReview.TIMEOUT-10000);
        saveFixture(b);FinishedMatchReview.touch(preferences,b,now);mainLaunch();click("New match");
        FinishedMatchReview.touch(preferences,b,now-FinishedMatchReview.TIMEOUT);
        ReflectionHelpers.callInstanceMethod(activity,"checkFinishedReview");assertTrue(hasButton("Create match"));
        controller.newIntent(new Intent(Intent.ACTION_MAIN));assertTrue(hasButton("Create match"));click("Create match");
        String next=book().current().id();close();mainLaunch();assertEquals("Scoreboard",ReflectionHelpers.getField(activity,"panelPage"));
        assertEquals(next,book().current().id());activity.action(TouchTargets.BLUE);close();mainLaunch();
        assertEquals("Scoreboard",ReflectionHelpers.getField(activity,"panelPage"));match();click("Pause match");close();mainLaunch();
        assertTrue(hasButton("Resume match"));assertEquals(1,book().current().game().blue());
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="w192dp-h192dp-round-xhdpi")
    public void idleGermanHomeShowsNewMatchAndRoutesStraightToResults() throws Exception{
        close();saveFixture(new MatchBook(new MatchState("idle",3)));
        preferences.edit().putString(WatchLanguage.PREFERENCE,"de").putString("my_name","Alex Weber").commit();
        mainLaunch();layout();
        assertTrue(hasButton("Neue Partie"));assertTrue(hasButton("Spieler"));assertTrue(hasButton("Ergebnisse"));
        View screen=dialog().getWindow().getDecorView();
        captureRoundScreen(screen,screen.getWidth(),"home-round-192-de");
        assertBrandedHomeFits(screen);
        assertHomeGreeting(screen,"Hi Alex");
        assertFalse(has("Alex Weber"));
        assertFalse(has("Startseite"));
        for(String label:new String[]{"Neue Partie","Ergebnisse","Spieler","Sportarten"}){
            Button tile=button(label);
            assertInsideRound(screen,tile);
            assertTrue(label,tile.getLayout().getLineCount()<=(label.contains(" ")?2:1));
        }
        Button settings=button("Einstellungen");
        assertEquals(1,settings.getLayout().getLineCount());
        assertLabelInsideRound(screen,settings);
        scroll().scrollTo(0,scroll().getChildAt(0).getHeight());
        assertInsideRound(screen,settings);
        assertEquals(1,settings.getLayout().getLineCount());
        captureRoundScreen(screen,screen.getWidth(),"home-settings-round-192-de");
        click("Einstellungen");back();layout();
        click("Ergebnisse");assertTrue(hasButton("Frühere Partie eintragen"));click("Zur Startseite");
        click("Spieler");
        click("Dein Name: Alex Weber");
        EditText ownName=(EditText)all().stream().filter(v->v instanceof EditText).findFirst().get();
        ownName.setText("");
        click("Namen speichern");back();layout();
        assertHomeGreeting(dialog().getWindow().getDecorView(),"Hi!");
        click("Neue Partie");click("Padel");assertTrue(hasButton("Partie anlegen"));
        assertTrue(sections().contains("Spielvorlagen"));
        assertTrue(has("Spielmodus, Regeln und Satzanzahl für spätere Partien."));
        assertTrue(hasButton("Als Vorlage speichern"));
        click("Vorlage laden");
        assertTrue(has("Speichere im Menü Neue Partie eine Vorlage"));
        back();assertTrue(hasButton("Partie anlegen"));
    }

    private void assertInsideRound(View screen,Button button){
        int[] origin=new int[2],point=new int[2];
        screen.getLocationOnScreen(origin);
        button.getLocationOnScreen(point);
        float x=point[0]-origin[0],y=point[1]-origin[1];
        float radius=Math.min(screen.getWidth(),screen.getHeight())/2f;
        String message=button.getText()+" at "+x+","+y+" size "+button.getWidth()+"×"+button.getHeight();
        assertTrue(message,button.getWidth()>=48*activity.getResources().getDisplayMetrics().density);
        assertTrue(message,button.getHeight()>=48*activity.getResources().getDisplayMetrics().density);
        for(float left:new float[]{x,x+button.getWidth()})for(float top:new float[]{y,y+button.getHeight()})
            assertTrue(message,Math.hypot(left-screen.getWidth()/2f,top-screen.getHeight()/2f)<radius);
    }

    private void assertLabelInsideRound(View screen,TextView button){
        int[] origin=new int[2],point=new int[2];
        screen.getLocationOnScreen(origin);
        button.getLocationOnScreen(point);
        float radius=Math.min(screen.getWidth(),screen.getHeight())/2f;
        float left=point[0]-origin[0]+button.getTotalPaddingLeft()-screen.getWidth()/2f;
        float top=point[1]-origin[1]+button.getTotalPaddingTop()-screen.getHeight()/2f;
        android.text.Layout text=button.getLayout();
        for(int n=0;n<text.getLineCount();n++)
            for(float x:new float[]{text.getLineLeft(n),text.getLineRight(n)})
                for(float y:new float[]{text.getLineTop(n),text.getLineBottom(n)})
                    assertTrue(button.getText()+" at "+(left+x)+", "+(top+y),Math.hypot(left+x,top+y)<radius);
    }

    private void assertBrandedHomeFits(View screen){
        View mark=null, name=null;
        for(View v:all()){
            if(v instanceof ImageView && ((ImageView)v).getDrawable()!=null)mark=v;
            if(v instanceof TextView && "Keep Score".contentEquals(((TextView)v).getText()))name=v;
        }
        assertNotNull(mark);assertNotNull(name);assertSame(mark.getParent(),name.getParent());
        float radius=screen.getWidth()/2f;
        for(View v:new View[]{mark,name}){
            int[] pos=new int[2];v.getLocationOnScreen(pos);assertTrue(v.isShown());
            for(float x:new float[]{pos[0],pos[0]+v.getWidth()})for(float y:new float[]{pos[1],pos[1]+v.getHeight()})
                assertTrue(v.getClass().getSimpleName()+" at "+x+", "+y+" for radius "+radius,Math.hypot(x-radius,y-radius)<radius);
        }
    }

    private void assertHomeGreeting(View screen,String text){
        TextView greeting=(TextView)all().stream()
            .filter(v->v instanceof TextView && text.contentEquals(((TextView)v).getText()))
            .findFirst().orElseThrow(()->new AssertionError("Missing greeting: "+text));
        assertTrue(greeting.isAccessibilityHeading());
        assertTrue(greeting.isShown());
        android.graphics.Rect visible=new android.graphics.Rect();
        assertTrue(greeting.getLocalVisibleRect(visible));
        assertEquals(greeting.getHeight(),visible.height());
        assertEquals(1,greeting.getLayout().getLineCount());
        assertEquals(0,greeting.getLayout().getEllipsisCount(0));
        assertLabelInsideRound(screen,greeting);
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="w192dp-h192dp-round-xhdpi")
    public void brandAndGreetingFitWithLargerTextOnSmallRoundWatches() throws Exception{
        close();RuntimeEnvironment.setFontScale(1.3f);saveFixture(new MatchBook(new MatchState("brand-large",3)));
        preferences.edit().putString(WatchLanguage.PREFERENCE,"de").putString("my_name","Müller, Marie-Louise").commit();
        mainLaunch();layout();
        View screen=dialog().getWindow().getDecorView();
        captureRoundScreen(screen,screen.getWidth(),"home-brand-round-192-de-large");assertBrandedHomeFits(screen);
        assertHomeGreeting(screen,"Hi Marie-Louise");
        assertFalse(has("Müller"));
        scroll().scrollTo(0,scroll().getChildAt(0).getHeight());
        assertInsideRound(screen,button("Einstellungen"));
        click("Einstellungen");assertMenuPath("Startseite › Einstellungen");back();
        click("Ergebnisse");assertEquals(MenuSection.RESULTS,location().section);
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="w192dp-h192dp-round-xhdpi")
    public void aboutMeIncludesOfflineCreditAndScannablePublicContactInTheWatchLanguage() throws Exception{
        close();preferences.edit().putString(WatchLanguage.PREFERENCE,"de").commit();launch();
        String original=book().encode();
        root();click("Einstellungen");click("Über mich");
        assertTrue(has("Andrew Lindley"));assertTrue(has("Ich spiele Squash"));assertTrue(tasks.queued.isEmpty());
        assertEquals(MenuSection.SETTINGS,location().section);
        click("Impressum & Kontakt");layout();View screen=dialog().getWindow().getDecorView();
        assertEquals(BuildConfig.SITE_URL+"/imprint?lang=de",decodeRoundQr(screen,screen.getWidth(),"about-contact-round-192-de"));
        back();assertTrue(hasButton("Impressum & Kontakt"));back();assertTrue(hasButton("Über mich"));
        assertEquals(original,book().encode());
    }

    @Test public void pathTracksTheActualEntryAndBackIncludingPagesWithTheSameTitleAsAPlayer(){
        close();OpponentDirectory d=OpponentDirectory.decode(preferences.getString("opponent_book",""));d.add("Settings");
        preferences.edit().putString("opponent_book",d.encode()).commit();launch();activity.showSports();
        assertMenuPath("Choose sport");click("Settings");assertMenuPath("Choose sport › Settings");back();
        click("Squash");assertMenuPath("Choose sport › Squash");click("Settings");assertMenuPath("Choose sport › Squash › Settings");
        click("Language · English");assertMenuPath("Choose sport › Squash › Settings › Language");assertEquals(MenuSection.SETTINGS,location().section);
        click("Back to Settings");assertMenuPath("Choose sport › Squash › Settings");back();click("Players");click("Settings");
        assertMenuPath("Choose sport › Squash › Players › Settings");assertEquals(MenuSection.PLAYERS,location().section);
        assertTrue(hasButton("Rename"));back();assertMenuPath("Choose sport › Squash › Players");
    }

    @Test public void newMatchPathNamesTheSelectedSportAndClearsSetupAfterCreation(){
        close();
        saveFixture(new MatchBook(new MatchState("idle-setup",3)));
        preferences.edit().putString(WatchLanguage.PREFERENCE,"de").commit();
        mainLaunch();

        click("Neue Partie");assertMenuPath("Startseite › Neue Partie");
        click("Squash");assertMenuPath("Startseite › Squash › Neue Partie");
        click(activity.tr("Match length: Best of ")+3);
        assertMenuPath("Startseite › Squash › Neue Partie › Partielänge");
        back();assertMenuPath("Startseite › Squash › Neue Partie");
        back();assertMenuPath("Startseite › Neue Partie");
        click("Tennis");assertMenuPath("Startseite › Tennis › Neue Partie");
        back();assertMenuPath("Startseite › Neue Partie");
        click("Squash");click("Partie anlegen");

        root();assertMenuPath("Squash");
        click("Aktuelle Partie");assertMenuPath("Squash › Aktuelle Partie");
        assertTrue(hasButton("Partie starten"));
        click("Partie starten");
        activity.action(TouchTargets.BLUE);
        root();click("Aktuelle Partie");assertMenuPath("Squash › Aktuelle Partie");
        assertTrue(hasButton("Partie pausieren"));
        click("Partiedetails");assertMenuPath("Squash › Aktuelle Partie › Partiedetails");
        back();click("Partieoptionen");assertMenuPath("Squash › Aktuelle Partie › Partieoptionen");
        back();click("Partie pausieren");
        root();click("Aktuelle Partie");assertMenuPath("Squash › Aktuelle Partie");
        assertTrue(hasButton("Partie fortsetzen"));
    }

    @Test @Config(qualifiers="w192dp-h192dp-round-xhdpi") public void finishedResultOffersVisibleNewMatchFirstAndRetainsTheSavedResultOnCancel(){
        RuntimeEnvironment.setFontScale(1.3f);
        close();MatchBook b=book();b.retire(1);preferences.edit().putString("match_book",b.encode()).remove(MatchDisplayPolicy.ACTIVE_MATCH).commit();
        launch();root();assertTrue(all().indexOf(button("New match"))<all().indexOf(button("Current match")));
        click("Current match");click("View result");layout();
        Button primary=button("New match");assertPrimary(primary);assertNotNull(primary.getCompoundDrawablesRelative()[2]);
        assertTrue(all().indexOf(primary)<all().indexOf(button("Quick rematch")));
        assertTrue(all().indexOf(button("Quick rematch"))<all().indexOf(button("Share result card")));
        android.graphics.Rect visible=new android.graphics.Rect();assertTrue(primary.getLocalVisibleRect(visible));assertEquals(primary.getHeight(),visible.height());
        String before=book().encode();click("New match");assertTrue(hasButton("Create match"));back();
        assertTrue(hasButton("Quick rematch"));assertEquals(before,book().encode());
        click("New match");click("Create match");assertFalse(book().current().id().equals("current"));assertNotNull(book().result("current"));
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="w192dp-h192dp-round-xhdpi")
    public void winningPointPutsGermanNewMatchLabelInsideTheRoundWatchAtLargeType() throws Exception {
        RuntimeEnvironment.setFontScale(1.3f);
        close();
        MatchBook finalRally = new MatchBook(new MatchState("final", 3));
        for (int n=0; n<11; n++) finalRally.add(0);
        assertTrue(finalRally.nextGame());
        for (int n=0; n<10; n++) finalRally.add(0);
        preferences.edit().putString("match_book",finalRally.encode()).putString(WatchLanguage.PREFERENCE,"de")
                .putString(MatchDisplayPolicy.ACTIVE_MATCH,"final").commit();
        launch();
        activity.action(TouchTargets.BLUE);
        layout();
        assertNotNull(book().result("final"));
        Button primary = button("Neue Partie");
        assertTrue(all().indexOf(primary) < all().indexOf(button("Revanche")));
        View screen = dialog().getWindow().getDecorView();
        int diameter = screen.getWidth();
        captureRoundScreen(screen, diameter, "new-match-round-192-de");
        float radius = diameter / 2f;
        android.text.Layout label = primary.getLayout();
        int[] origin = new int[2];
        primary.getLocationOnScreen(origin);
        int top = origin[1] + primary.getTotalPaddingTop();
        for (int line=0; line<label.getLineCount(); line++) {
            for (float x : new float[]{label.getLineLeft(line),label.getLineRight(line)})
                for (int y : new int[]{label.getLineTop(line),label.getLineBottom(line)})
                    assertTrue("New match text clipped by round display",
                            Math.hypot(origin[0]+primary.getTotalPaddingLeft()+x-radius, top+y-radius) < radius);
        }
        click("Neue Partie");
        assertNotNull(book().result("final"));
    }
    private boolean awake(){return (activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;}
    private TransferClient.Session readyTransfer(){TransferClient.Session s=new TransferClient.Session();s.expiresAt=System.currentTimeMillis()+600000;ReflectionHelpers.setField(activity,"transfer",s);return s;}
    private void openFirstResult(){for(View v:all())if(v.getContentDescription()!=null&&v.getContentDescription().toString().contains("Open match result")){v.performClick();shadowOf(Looper.getMainLooper()).idle();return;}throw new AssertionError("Missing result card");}
    private List<String> sections(){
        List<String> titles=new ArrayList<>();
        for(View view:all())if(view.isAccessibilityHeading()&&view.getContentDescription()!=null){
            assertEquals(location().section.accent,((TextView)view).getCurrentTextColor());
            assertEquals(view.getContentDescription().toString(),((TextView)view).getText().toString());
            titles.add(view.getContentDescription().toString());
        }
        return titles;
    }
    @Test public void mixedMenusUseTaskHeadingsAndSingleChoiceScreensStayCompact(){
        root();assertEquals(Arrays.asList("Match","Match history","Sport","App"),sections());
        click("Current match");assertEquals(Arrays.asList("Score","Players & options","Sharing","End match"),sections());
        click("Live sharing & referee");assertEquals(Arrays.asList("Share your match","Join another match"),sections());
        click("Join with watch code");assertTrue(sections().isEmpty());back();back();back();
        click("Settings");assertEquals(Arrays.asList("Language & display","Feedback","Data","Watch shortcuts","App"),sections());
        click("Language · English");assertTrue(sections().isEmpty());back();back();
        click("Players");assertEquals(Arrays.asList("Search & sort","Saved players","Manage names"),sections());back();
        click("Results (17)");assertEquals(Arrays.asList("More matches"),sections());
        click("Options");assertEquals(Arrays.asList("CSV","Delete results"),sections());back();
        click("Filters");assertEquals(Arrays.asList("Match","Date range"),sections());
    }
    @Test public void directPlayerSelectionReturnsToCurrentMatchAndKeepsTheLiveScore(){
        String score=book().encode();
        match();
        click("Opponent: Alice");
        assertTrue(button("✓ Alice").isSelected());
        assertNull(button("Bob").getCompoundDrawablesRelative()[2]);
        back();
        assertTrue(hasButton("Pause match"));
        click("Opponent: Alice");
        click("Bob");
        assertTrue(hasButton("Opponent: Bob"));
        assertEquals("Bob",OpponentDirectory.decode(preferences.getString("opponent_book","")).nameFor("current"));
        assertEquals(score,book().encode());
        assertEquals("current",preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""));
        click("Save retirement result");click("I quit");back();back();
        assertTrue(hasButton("Pause match"));
        assertEquals(score,book().encode());
    }
    @Test public void launcherMatchShortcutReturnsToTheLauncherAfterAPicker(){
        activity.showSports();click("Current match · Squash");
        click("Opponent: Alice");back();click("Back to sports");
        assertTrue(hasButton("Tennis"));assertTrue(hasButton("Players"));
        click("Current match · Squash");back();assertTrue(hasButton("Close app"));
    }
    @Test public void doublesPlayersAreReachableDirectlyAndKeepTheirTeamRoles(){
        close();
        MatchBook b=new MatchBook(new MatchState("doubles",3,MatchFormat.defaults("padel")));
        OpponentDirectory d=OpponentDirectory.decode(preferences.getString("opponent_book",""));
        d.assignTeam("doubles",d.opponentId("current"),"","");
        preferences.edit().putString("match_book",b.encode()).putString("opponent_book",d.encode())
            .putString(MatchDisplayPolicy.ACTIVE_MATCH,"doubles").commit();
        launch();match();click("Players");
        assertEquals(Arrays.asList("Our team","Opposing team"),sections());
        click("Partner: No name");click("Bob");back();
        assertTrue(hasButton("Pause match"));
        assertTrue(has("Bob"));assertTrue(has("Alice"));
        OpponentDirectory result=OpponentDirectory.decode(preferences.getString("opponent_book",""));
        assertEquals("Bob",result.roleName("doubles",1));
        assertEquals("Alice",result.roleName("doubles",0));
        assertEquals(b.encode(),book().encode());
    }
    @Test public void historyLeadsWithAMatchAndKeepsBothToolsOneTapAway(){
        results();layout();
        Button filters=button("Filters"),options=button("Options");
        List<LinearLayout> cards=historyCards();
        assertEquals(10,cards.size());
        assertTrue(all().indexOf(cards.get(0))<all().indexOf(filters));
        assertTrue(all().indexOf(options)<all().indexOf(cards.get(1)));
        assertSame(filters.getParent(),options.getParent());
        assertEquals(filters.getTop(),options.getTop());
        assertTrue(options.getLeft()>=filters.getRight());
        assertFalse(hasButton("Add past match"));
        click("Options");assertTrue(hasButton("Add past match"));back();
        layout();scroll().scrollTo(0,300);
        int before=scroll().getScrollY();
        assertTrue(before>0);
        openFirstResult();back();layout();assertEquals(before,scroll().getScrollY());
        back();click("Results (17)");layout();assertEquals(0,scroll().getScrollY());
    }
    private List<LinearLayout> historyCards(){
        List<LinearLayout> cards=new ArrayList<>();
        String action=activity.tr(". Open match result.");
        for(View v:all())if(v instanceof LinearLayout && v.getContentDescription()!=null
                && v.getContentDescription().toString().endsWith(action))cards.add((LinearLayout)v);
        return cards;
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="w192dp-h192dp-round-xhdpi")
    public void mostRecentScoreAndOpponentFitAtLargeGermanTextBeforeTheTools() throws Exception{
        RuntimeEnvironment.setFontScale(1.3f);
        close();preferences.edit().putString(WatchLanguage.PREFERENCE,"de").commit();launch();
        root();click("Ergebnisse (17)");layout();
        LinearLayout first=historyCards().get(0);
        assertEquals("2–0  Gewonnen",((TextView)first.getChildAt(0)).getText().toString());
        assertTrue(((TextView)first.getChildAt(1)).getText().toString().contains("Bob"));
        assertTrue(((TextView)first.getChildAt(2)).getText().toString().contains("06.11.2026"));
        View screen=dialog().getWindow().getDecorView();
        captureRoundScreen(screen,screen.getWidth(),"results-first-round-192-de");
        float radius=screen.getWidth()/2f;
        for(int child=0;child<first.getChildCount();child++){
            TextView text=(TextView)first.getChildAt(child);
            android.text.Layout lines=text.getLayout();int[] origin=new int[2];text.getLocationOnScreen(origin);
            for(int line=0;line<lines.getLineCount();line++)
                for(float x:new float[]{lines.getLineLeft(line),lines.getLineRight(line)})
                    for(int y:new int[]{lines.getLineTop(line),lines.getLineBottom(line)})
                        assertTrue("Clipped "+text.getText(),Math.hypot(origin[0]+text.getTotalPaddingLeft()+x-radius,origin[1]+text.getTotalPaddingTop()+y-radius)<radius);
        }
        click("Optionen");assertTrue(hasButton("Frühere Partie eintragen"));
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="w192dp-h192dp-round-xhdpi")
    public void badmintonResultToolsFitInsideTheRoundScreenWhenScrolledIntoView() throws Exception{
        close();RuntimeEnvironment.setFontScale(1.3f);
        MatchBook matches=new MatchBook(new MatchState("idle-badminton",3,MatchFormat.defaults("badminton")));
        for(int day=1;day<=3;day++){
            MatchState score=new MatchState("badminton-"+day,3,MatchFormat.defaults("badminton"));
            score.enterCompletedSet(21,18);score.enterCompletedSet(21,16);
            matches.importResult(MatchResult.from(score,"2026-11-0"+day,day));
        }
        saveFixture(matches);
        preferences.edit().putString(WatchLanguage.PREFERENCE,"de").putString("selected_sport","badminton").commit();
        mainLaunch();click("Ergebnisse");layout();
        View screen=dialog().getWindow().getDecorView();
        Button filter=button("Filter"),options=button("Optionen");
        assertTrue(all().indexOf(historyCards().get(0))<all().indexOf(filter));
        View tools=(View)filter.getParent();
        int[] position=new int[2],window=new int[2];
        tools.getLocationOnScreen(position);screen.getLocationOnScreen(window);
        scroll().scrollTo(0,position[1]-window[1]+tools.getHeight()/2-screen.getHeight()/2);
        assertInsideRound(screen,filter);assertInsideRound(screen,options);
        assertEquals(1,filter.getLayout().getLineCount());assertEquals(1,options.getLayout().getLineCount());
        captureRoundScreen(screen,screen.getWidth(),"badminton-result-tools-round-192-de");
        click("Filter");back();layout();assertInsideRound(dialog().getWindow().getDecorView(),button("Filter"));
        click("Optionen");assertTrue(hasButton("Frühere Partie eintragen"));
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="w192dp-h192dp-round-xhdpi")
    public void openGermanMenuPathShowsItsRouteOnTheRoundDisplay() throws Exception{
        close();saveFixture(new MatchBook(new MatchState("idle-path",3)));
        preferences.edit().putString(WatchLanguage.PREFERENCE,"de").commit();
        mainLaunch();click("Sportarten");click("Einstellungen");
        menuPathControl().performClick();layout();
        View screen=dialog().getWindow().getDecorView();
        String path="Startseite › Sportart wählen › Einstellungen";
        assertTrue(all().stream().anyMatch(v->v instanceof TextView && v.getVisibility()==View.VISIBLE
                && path.contentEquals(((TextView)v).getText())));
        captureRoundScreen(screen,screen.getWidth(),"watch-menu-path-round-192-de");
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="w180dp-h180dp-round-xhdpi")
    public void historyToolsStackWhenTheTwoLabelsDoNotFitAtLargeType(){
        RuntimeEnvironment.setFontScale(1.8f);
        close();preferences.edit().putString(WatchLanguage.PREFERENCE,"de").commit();launch();
        root();click("Ergebnisse (17)");layout();
        Button filters=button("Filter"),options=button("Optionen");
        assertEquals(LinearLayout.VERTICAL,((LinearLayout)filters.getParent()).getOrientation());
        assertEquals(filters.getWidth(),options.getWidth());
        assertTrue(options.getTop()>=filters.getBottom());
        assertTrue(filters.getWidth()>=48*activity.getResources().getDisplayMetrics().density);
        click("Optionen");assertTrue(hasButton("Frühere Partie eintragen"));
    }
    @Test public void nestedMenuDisclosesItsPathWhileQrShowsTheCode(){
        readyTransfer();activity.showSports();
        click("Squash");click("Results (17)");assertMenuPath("Choose sport › Squash › Results");
        click("Options");click("Import CSV");
        String transfer=location().path(activity::tr);
        assertTrue(transfer.startsWith("Choose sport › Squash › Results › Result options › "));
        assertMenuPath(transfer);
        click("Show phone QR");
        for(View view:all())assertFalse(String.valueOf(view.getContentDescription()).endsWith("Show menu path"));
        back();assertMenuPath(transfer);
    }

    private View menuPathControl(){
        return all().stream().filter(v->String.valueOf(v.getContentDescription()).endsWith(activity.tr("Show menu path")))
                .findFirst().orElseThrow(()->new AssertionError("Missing menu path control"));
    }

    private void assertMenuPath(String path){
        assertEquals(path,location().path(activity::tr));
        View header=menuPathControl();
        assertFalse(header.getContentDescription().toString().matches(".*Level [0-9]+.*"));
        if(header instanceof LinearLayout)assertTrue(((LinearLayout)header).getChildCount()<=3);
        TextView trail=(TextView)all(header).stream().filter(v->v instanceof TextView && path.contentEquals(((TextView)v).getText())
                && v.getVisibility()==View.GONE).findFirst().orElseThrow(()->new AssertionError("Missing collapsed menu path"));
        header.performClick();
        assertEquals(View.VISIBLE,trail.getVisibility());
        assertTrue(header.getContentDescription().toString().contains(path));
        header.performClick();
        assertEquals(View.GONE,trail.getVisibility());
    }
    @Test public void emptyHistoryOffersEntryAndFilteredEmptyOffersReset(){
        close();
        MatchBook blank=new MatchBook(new MatchState("blank",3));
        MatchState otherSport=new MatchState("tennis-result",3,MatchFormat.defaults("tennis"));
        otherSport.enterCompletedSet(6,1);otherSport.enterCompletedSet(6,2);
        blank.importResult(MatchResult.from(otherSport,"2026-11-06",1));
        preferences.edit().putString("match_book",blank.encode()).remove(MatchDisplayPolicy.ACTIVE_MATCH).commit();
        launch();root();click("Results (0)");
        assertTrue(has("No saved matches yet"));click("Add past match");back();
        assertTrue(hasButton("Filters"));assertTrue(hasButton("Add past match"));
        click("Filters");click("Today");
        assertTrue(has("No matches fit"));assertFalse(hasButton("Add past match"));
        assertTrue(button("Filters").isSelected());click("Reset all filters");
        assertTrue(hasButton("Add past match"));assertFalse(button("Filters").isSelected());
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers="w192dp-h192dp-round-xhdpi") public void smallRoundGermanQrIsReadableAndHasARealBackButton()throws Exception{
        RuntimeEnvironment.setFontScale(1.3f);
        String payload="https://example.test/watch#id=synthetic-menu-test&token=abcdefghijklmnopqrstuvwxyz0123456789&key=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-example&view=results";
        preferences.edit().putString(WatchLanguage.PREFERENCE,"de").commit();
        close();launch();matchInGerman();
        activity.showQr(payload,"Scan with phone",activity::showMatchControls,"Back to Current match");
        assertTrue(sections().isEmpty());
        assertFalse(hasButton("Spielstand"));
        for(View view:all())assertFalse(String.valueOf(view.getContentDescription()).contains(activity.tr("Show menu path")));
        Button backButton=button("Zurück zur Partie");
        int diameter=activity.getResources().getDisplayMetrics().widthPixels;
        View root=dialog().getWindow().getDecorView();
        int spec=View.MeasureSpec.makeMeasureSpec(diameter,View.MeasureSpec.EXACTLY);
        root.measure(spec,spec);root.layout(0,0,diameter,diameter);
        assertTrue(backButton.getHeight()>=48*activity.getResources().getDisplayMetrics().density);
        assertEquals(payload, decodeRoundQr(root, diameter, "qr-round-192-de-large"));
        backButton.performClick();
        assertTrue(hasButton("Partie pausieren"));
    }
    private int[] captureRoundScreen(View root, int diameter, String name) throws Exception {
        android.graphics.Bitmap frame=android.graphics.Bitmap.createBitmap(diameter,diameter,android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(frame));
        int[] pixels=new int[diameter*diameter];
        frame.getPixels(pixels,0,diameter,0,0,diameter,diameter);
        // Physical round displays mask everything outside their circular viewport.
        float radius=diameter/2f;
        for(int y=0;y<diameter;y++)for(int x=0;x<diameter;x++)
            if(Math.hypot(x-radius,y-radius)>radius)pixels[y*diameter+x]=Color.BLACK;
        frame.setPixels(pixels,0,diameter,0,0,diameter,diameter);
        java.io.File output=new java.io.File("build/qa/" + name + ".png");
        output.getParentFile().mkdirs();
        try(java.io.OutputStream out=new java.io.FileOutputStream(output)){
            assertTrue(frame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        }
        return pixels;
    }
    private String decodeRoundQr(View root, int diameter, String name) throws Exception {
        int[] pixels = captureRoundScreen(root, diameter, name);
        com.google.zxing.RGBLuminanceSource source=new com.google.zxing.RGBLuminanceSource(diameter,diameter,pixels);
        return new com.google.zxing.qrcode.QRCodeReader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(source))).getText();
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers="w192dp-h192dp-round-xhdpi") public void diagnosticsQrPrunesLegacyHistoryAndScansFromTheGermanWatchScreen() throws Exception {
        RuntimeEnvironment.setFontScale(1.3f);
        preferences.edit().putString(WatchLanguage.PREFERENCE,"de").commit();
        close(); launch(); root(); click("Einstellungen");
        StringBuilder legacy = new StringBuilder();
        for (int i=0; i<80; i++) legacy.append("11:01:30 legacy event ").append(i).append('\n');
        StringBuilder crash = new StringBuilder("2026-02-01 v1 main\nIllegalStateException: Demo");
        for (int i=0; i<120; i++) crash.append("\n at synthetic.frame").append(i);
        preferences.edit().putString("lifecycle_trace",legacy.toString()).putString("last_crash",crash.toString()).commit();
        AppDiagnostics.record(activity, "NFC share: commands=0 mapping=0 stage=waiting end=pause after=2s");
        AppDiagnostics.record(activity, "Activity stop; finishing=false changingConfig=false");
        AppDiagnostics.record(activity, "Activity resume; ambient=false paused=false");
        String trace = preferences.getString("lifecycle_trace","");
        assertEquals(12, trace.split("\n").length);
        assertFalse(trace.contains("legacy event 0\n"));
        assertTrue(trace.contains("legacy event 79\n"));

        click("Diagnose"); click(activity.tr("Send to phone"));
        View root = dialog().getWindow().getDecorView();
        int diameter = activity.getResources().getDisplayMetrics().widthPixels;
        int spec = View.MeasureSpec.makeMeasureSpec(diameter,View.MeasureSpec.EXACTLY);
        root.measure(spec,spec); root.layout(0,0,diameter,diameter);
        String link = decodeRoundQr(root, diameter, "diagnostics-qr-round-192-de");
        assertTrue(link.startsWith(BuildConfig.SITE_URL + "/diagnostics.html#d="));
        assertTrue(link.getBytes("UTF-8").length <= 318);
        android.net.Uri uri = android.net.Uri.parse("https://example.test/?" + java.net.URI.create(link).getFragment());
        String[] state = uri.getQueryParameter("s").split("\\.");
        assertEquals("de", state[1]);
        assertEquals("17", state[9]);
        assertTrue((Integer.parseInt(state[2], 36) & (1 << 10)) != 0);
        com.google.zxing.qrcode.encoder.QRCode symbol = com.google.zxing.qrcode.encoder.Encoder.encode(link,
            com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L,
            java.util.Collections.singletonMap(com.google.zxing.EncodeHintType.CHARACTER_SET,"UTF-8"));
        assertTrue(symbol.getVersion().getVersionNumber() <= 11);
        byte[] bytes = Base64.getUrlDecoder().decode(uri.getQueryParameter("d"));
        try (java.util.Scanner decoded = new java.util.Scanner(new java.util.zip.GZIPInputStream(
            new java.io.ByteArrayInputStream(bytes)), "UTF-8").useDelimiter("\\A")) {
            String report = decoded.next();
            assertTrue(report.contains("Keep Score v" + BuildConfig.VERSION_NAME));
            assertTrue(report.contains("NFC share: commands=0 mapping=0 stage=waiting end=pause after=2s"));
            assertFalse(report.contains("synthetic.frame"));
            assertFalse(report.contains("legacy event 0\n"));
        }
        click(activity.tr("Back to Diagnostics"));
        assertTrue(has("synthetic.frame119"));
    }

    private void matchInGerman(){
        root();click("Aktuelle Partie");
        assertEquals(Arrays.asList("Spielstand","Spieler & Optionen","Teilen","Partie beenden"),sections());
    }
    @Implements(value=UpdateClient.class, isInAndroidSdk=false)
    public static class TestUpdateClient {
        UpdatePolicy release;
        java.util.function.Consumer<UpdateClient.Progress> transfer;
        Runnable install;

        @Implementation protected UpdatePolicy check() { return release; }
        @Implementation protected java.io.File download(UpdatePolicy offer, UpdateClient.Progress progress) {
            assertSame(release, offer);
            transfer.accept(progress);
            return new java.io.File("example-update.apk");
        }
        @Implementation protected void install(java.io.File apk) { install.run(); }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers="de-rAT-w192dp-h192dp-round-xhdpi", shadows=TestUpdateClient.class)
    public void tappingDownloadRevealsUpdateProgressOnASmallWatch() throws Exception {
        org.junit.Assume.assumeFalse(BuildConfig.PLAY_DISTRIBUTION);
        RuntimeEnvironment.setFontScale(1.3f);
        preferences.edit().putString(WatchLanguage.PREFERENCE, "de").commit();
        close(); launch();
        shadowOf(activity.getPackageManager()).setCanRequestPackageInstalls(true);
        String feed = "schema=1\npackageName=" + activity.getPackageName()
            + "\nversionCode=" + (BuildConfig.VERSION_CODE + 1) + "\nversionName=9.9.9\nminSdk=30\nsizeBytes=100"
            + "\napkUrl=https://example.test/update.apk\nsha256=" + String.join("", Collections.nCopies(64,"a"))
            + "\nnotes=" + String.join(" ", Collections.nCopies(24, "Neue Funktion für die Uhr."));
        TestUpdateClient client = org.robolectric.shadow.api.Shadow.extract(ReflectionHelpers.getField(activity, "updater"));
        client.release = UpdatePolicy.parse(feed, activity.getPackageName(), "example.test");
        root(); click("Einstellungen"); click("Nach Updates suchen");
        assertMenuPath("Squash › Einstellungen › App-Updates");
        assertUpdateProgress(false, true);
        tasks.queued.remove(0).run();
        assertUpdateProgress(false, false);
        ScrollView offer = scroll();
        Button download = button("Laden und aktualisieren");
        layout();
        offer.scrollTo(0, download.getTop() - offer.getPaddingTop());
        assertTrue(offer.getScrollY() > offer.getHeight());

        click("Laden und aktualisieren");
        assertEquals(0, scroll().getScrollY());
        assertFalse(scroll().isVerticalScrollBarEnabled());
        assertDownloadStage("Geprüftes Update wird geladen …", 0);
        client.transfer = progress -> {
            progress.update(37);
            assertDownloadStage(WatchLanguage.get(activity).status("Downloading: 37%"), 37);
            try {
                View view = dialog().getWindow().getDecorView();
                captureRoundScreen(view, view.getWidth(), "update-progress-round-192-de");
            } catch (Exception failed) { throw new AssertionError(failed); }
            controller.pause().resume();
            assertDownloadStage(WatchLanguage.get(activity).status("Downloading: 37%"), 37);
            back();
            progress.update(82);
            assertTrue(hasButton("Nach Updates suchen"));
            click("Nach Updates suchen");
            assertDownloadStage(WatchLanguage.get(activity).status("Downloading: 82%"), 82);
            progress.update(100);
            assertDownloadStage(WatchLanguage.get(activity).status("Downloading: 100%"), 100);
            progress.verifying();
            assertDownloadStage("Update wird geprüft …", -1);
        };
        client.install = () -> assertDownloadStage(WatchLanguage.get(activity).status("Verified. Requesting installation…"), -1);
        tasks.queued.remove(0).run();
        assertUpdateProgress(false, false);
        assertTrue(has(WatchLanguage.get(activity).status("Installation requested. Reopen Keep Score after it finishes.")));

        client.transfer = progress -> { progress.update(12); throw new IllegalStateException("offline"); };
        click("Laden und aktualisieren");
        tasks.queued.remove(0).run();
        assertUpdateProgress(false, false);
        assertTrue(has("offline"));
        click("Laden und aktualisieren");
        assertDownloadStage("Geprüftes Update wird geladen …", 0);
    }

    private void assertUpdateProgress(boolean barVisible, boolean spinnerVisible) {
        ProgressBar bar = ReflectionHelpers.getField(activity,"updateBar");
        ProgressBar spinner = ReflectionHelpers.getField(activity,"updateSpinner");
        assertEquals(barVisible,bar.isShown());
        assertEquals(spinnerVisible,spinner.isShown());
        assertFalse(bar.isIndeterminate());
        assertTrue(spinner.isIndeterminate());
    }

    private void assertDownloadStage(String label, int percent) {
        assertUpdateMessageVisible(label);
        assertUpdateProgress(percent>=0,percent<0);
        ProgressBar indicator = ReflectionHelpers.getField(activity,percent<0?"updateSpinner":"updateBar");
        if (percent >= 0) assertEquals(percent, indicator.getProgress());
        else assertEquals(indicator.getWidth(),indicator.getHeight());
        android.graphics.Rect bounds = new android.graphics.Rect();
        assertTrue(indicator.getGlobalVisibleRect(bounds));
        assertEquals(indicator.getWidth(), bounds.width());
        assertEquals(indicator.getHeight(), bounds.height());
        View screen = dialog().getWindow().getDecorView();
        float radius = screen.getWidth() / 2f;
        for (int x : new int[]{bounds.left, bounds.right})
            for (int y : new int[]{bounds.top, bounds.bottom})
                assertTrue("Progress clipped by round display", Math.hypot(x-radius, y-radius) <= radius);
    }

    private void assertUpdateMessageVisible(String message) {
        layout();
        TextView text = ReflectionHelpers.getField(activity, "updateText");
        assertTrue("Update progress should appear before version details", text.getText().toString().startsWith(message));
        android.text.Layout layout = text.getLayout();
        android.graphics.Rect visible = new android.graphics.Rect();
        assertTrue(text.getLocalVisibleRect(visible));
        int last = layout.getLineForOffset(message.length() - 1);
        assertTrue(message + " clipped at top", text.getTotalPaddingTop() + layout.getLineTop(0) >= visible.top);
        assertTrue(message + " clipped at bottom", text.getTotalPaddingTop() + layout.getLineBottom(last) <= visible.bottom);
    }

    @Test public void aHistoricalResultHasNoNextMatchSectionAndKeepsItsReturn(){
        results();openFirstResult();assertEquals(Arrays.asList("Manage result"),sections());
        assertFalse(hasButton("Quick rematch"));back();assertTrue(hasButton("Filters"));
    }
    private ScrollView scroll(){for(View v:all())if(v instanceof ScrollView)return (ScrollView)v;throw new AssertionError("Missing scroll");}
    private void layout() {
        View view = dialog().getWindow().getDecorView();
        android.util.DisplayMetrics screen = activity.getResources().getDisplayMetrics();
        view.measure(View.MeasureSpec.makeMeasureSpec(screen.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screen.heightPixels, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, screen.widthPixels, screen.heightPixels);
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void rootGroupsSportHistoryAndSharedAppDestinations(){root();List<String> labels=new ArrayList<>();for(View v:all())if(v instanceof Button)labels.add(((Button)v).getText().toString());assertEquals(Arrays.asList("Current match","Players","Live sharing & referee","Results (17)","Statistics","Match presets","Scoring rules","Change sport","Settings","Back to score"),labels);assertFalse(hasButton("Manage on phone"));assertMenuLink(button("Players"));}
    @Test public void currentMatchPutsTheSessionActionBeforeSecondaryControls(){match();List<String> labels=new ArrayList<>();for(View v:all())if(v instanceof Button&&!((Button)v).getText().toString().equals("Scoreboard"))labels.add(((Button)v).getText().toString());assertEquals(Arrays.asList("Pause match","Enter set score","Match details","Opponent: Alice","Match options","Live sharing & referee","Save retirement result","Cancel match","Back to Menu"),labels);click("Pause match");assertFalse(awake());match();assertTrue(hasButton("Resume match"));assertFalse(hasButton("Pause match"));}
    @Test public void finishedCurrentMatchHidesSetupAndScoringActions(){close();MatchBook b=book();b.retire(1);preferences.edit().putString("match_book",b.encode()).remove(MatchDisplayPolicy.ACTIVE_MATCH).commit();launch();match();assertTrue(hasButton("View result"));assertTrue(hasButton("New match"));assertFalse(hasButton("Enter set score"));assertFalse(hasButton("Match options"));assertFalse(hasButton("Start match"));assertFalse(hasButton("Resume match"));}
    @Test public void choosingANewFormatStagesItUntilCreateIsExplicit(){String before=book().encode();match();click("Match options");click("New match");click("Match length: Best of 3");click("Best of 5 · first to 3");assertTrue(hasButton("Match length: Best of 5"));assertEquals(before,book().encode());click("Create match");assertTrue(has("Replace this match?"));assertEquals(before,book().encode());click("Replace & create match");assertEquals(5,book().current().bestOf());assertFalse(book().current().hasPoints());assertFalse(preferences.contains(MatchDisplayPolicy.ACTIVE_MATCH));}
    @Test public void cancelledNewMatchReturnsToItsCallerAndKeepsTheStagedChoice(){String before=book().encode();match();click("Match options");click("New match");click("Match length: Best of 3");click("Best of 5 · first to 3");click("Create match");back();assertTrue(hasButton("Match length: Best of 5"));back();assertTrue(hasButton("Save retirement result"));assertEquals(before,book().encode());assertEquals("current",preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""));}
    @Test public void buttonAndSystemBackFollowTheSameNamedParent(){match();click("Match options");click("Match length: Best of 3");click("Back to Match options");assertTrue(hasButton("Save retirement result"));click("Match length: Best of 3");back();assertTrue(hasButton("Save retirement result"));back();assertTrue(hasButton("Pause match"));back();assertTrue(hasButton("Settings"));back();assertFalse(ShadowDialog.getLatestDialog().isShowing());assertTrue(awake());}
    @Test public void nativeSwipeDismissUsesTheSameParentWithoutPausingPlay(){match();click("Match options");SwipeDismissFrameLayout frame=null;for(View v:all())if(v instanceof SwipeDismissFrameLayout){frame=(SwipeDismissFrameLayout)v;break;}assertNotNull(frame);assertTrue(frame.isSwipeable());ReflectionHelpers.callInstanceMethod(frame,"performDismissFinishedCallbacks");shadowOf(Looper.getMainLooper()).idle();assertTrue(hasButton("Pause match"));assertEquals("current",preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""));}
    @Test public void diagnosticQrBackReturnsToDiagnosticsAndReleasesItsScreenFlag(){root();click("Settings");click("Diagnostics");click("Send to phone");Dialog qr=dialog();assertTrue((qr.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0);back();assertTrue(hasButton("Copy details"));assertEquals(0,dialog().getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);back();assertTrue(hasButton("Check for updates"));}
    @Test public void resultDetailsPickerAndDeleteCancelPreserveFiltersPageAndScroll(){results();click("Filters");click("Opponent: All");click("Alice");click("Back to Results");assertTrue(has("12 of 17 saved"));click("Older results");assertTrue(has("Page 2 of 2"));layout();scroll().scrollTo(0,110);int position=scroll().getScrollY();assertTrue(position>0);openFirstResult();click("Opponent: Alice");back();assertTrue(hasButton("Delete this result"));click("Delete this result");back();click("Back to Results");layout();assertTrue(has("12 of 17 saved"));assertTrue(has("Page 2 of 2"));assertEquals(position,scroll().getScrollY());}
    @Test public void deleteAllIsKeptInsideResultOptions(){results();assertFalse(hasButton("Delete all results"));click("Options");assertMenuLink(button("Delete all results"));click("Delete all results");int c=button("Delete all").getBackgroundTintList().getDefaultColor();assertTrue(Color.red(c)>Color.blue(c));back();assertTrue(hasButton("Export CSV"));assertEquals(17,book().count());}
    @Test public void enteredSetIsAvailableDirectlyInTheCurrentMatchAndCancelChangesNothing(){String before=book().encode();match();click("Enter set score");assertTrue(has("Set 1 score"));click("Me won");assertTrue(hasButton("Review set score"));for(View v:all())if(v instanceof NumberPicker)assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS,((NumberPicker)v).getDescendantFocusability());back();assertTrue(hasButton("Pause match"));assertEquals(before,book().encode());}
    @Test public void pastMatchEntryBelongsToResultsAndCancelKeepsTheLiveMatch(){String before=book().encode();match();assertFalse(hasButton("Add past match"));results();assertFalse(hasButton("Add past match"));click("Options");click("Add past match");assertTrue(hasButton("Save match result"));assertFalse(hasButton("Enter set score"));back();assertTrue(hasButton("Add past match"));assertEquals(before,book().encode());assertEquals("current",preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""));}
    @Test public void resultsPhoneActionSetsItsLandingViewAndReturnsToResults(){
        readyTransfer().includesNotes=true;
        results();click("Options");click("View results on phone");
        assertTrue(hasButton("Show phone QR"));
        String link=ReflectionHelpers.callInstanceMethod(activity,"phoneLink");
        assertTrue(link.endsWith("&view=results"));
        click("Show phone QR");
        assertEquals("Phone QR",ReflectionHelpers.getField(activity,"panelPage"));
        back();assertTrue(hasButton("Receive changes"));
        back();assertTrue(hasButton("Filters"));
    }
    @Test public void resultCardCannotReuseThePrivateJournalQr(){
        readyTransfer().includesNotes=true;
        results();openFirstResult();click("Share result card");
        assertTrue(has("End the current phone link to open this one."));
        click("Back");
        assertFalse(hasButton("Show phone QR"));
        assertFalse(hasButton("Share page via NFC"));
        assertTrue(hasButton("Receive changes"));
        assertTrue(hasButton("End transfer"));
    }
    @Test public void importPhoneActionTargetsImportAndReturnsToResultOptions(){readyTransfer();results();click("Options");click("Import CSV");String link=ReflectionHelpers.callInstanceMethod(activity,"phoneLink");assertTrue(link.endsWith("&view=import"));back();assertTrue(hasButton("Delete all results"));assertTrue(hasButton("Export CSV"));}
    @Test public void namesPhoneActionPreservesItsOpponentPickerCaller(){readyTransfer();match();click("Match options");click("Opponent: Alice");click("Edit names on phone");String link=ReflectionHelpers.callInstanceMethod(activity,"phoneLink");assertTrue(link.endsWith("&view=opponents"));back();assertTrue(hasButton("No name"));assertTrue(hasButton("Bob"));back();assertTrue(hasButton("Match length: Best of 3"));}
    @Test public void aLatePhoneLinkCannotReopenPanelsAfterReturningToScoreboard(){results();click("Options");click("View results on phone");Dialog waiting=dialog();assertTrue(has("Preparing phone link"));click("Scoreboard");assertFalse(waiting.isShowing());TransferClient.Session created=new TransferClient.Session();created.expiresAt=System.currentTimeMillis()+600000;ReflectionHelpers.callInstanceMethod(activity,"transferCreated",ReflectionHelpers.ClassParameter.from(TransferClient.Session.class,created),ReflectionHelpers.ClassParameter.from(boolean.class,false),ReflectionHelpers.ClassParameter.from(boolean.class,false),ReflectionHelpers.ClassParameter.from(String.class,""),ReflectionHelpers.ClassParameter.from(Dialog.class,waiting));assertFalse(ShadowDialog.getLatestDialog().isShowing());assertEquals(created,ReflectionHelpers.getField(activity,"transfer"));assertTrue(awake());}
    private void assertPrimary(Button button){
        assertNotNull(button.getBackgroundTintList());
        for(int[] state:new int[][]{{android.R.attr.state_enabled},{android.R.attr.state_enabled,android.R.attr.state_pressed}}){
            int fill=button.getBackgroundTintList().getColorForState(state,0);
            int ink=button.getTextColors().getColorForState(state,0);
            assertEquals(Color.red(fill),Color.green(fill));
            assertEquals(Color.green(fill),Color.blue(fill));
            assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(ink,fill)>=4.5);
        }
    }

    private void assertMenuLink(Button button){
        assertSecondaryFill(button);
        assertNotNull(button.getCompoundDrawablesRelative()[2]);
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="w192dp-h192dp-round-xhdpi")
    public void matchEndingButtonsHaveAFilledSecondaryStyleOnASmallRoundWatch() throws Exception {
        close();
        RuntimeEnvironment.setFontScale(1.3f);
        preferences.edit().putString(WatchLanguage.PREFERENCE,"de").commit();
        launch();root();click("Aktuelle Partie");

        Button retire=button("Aufgabe werten"),cancel=button("Partie abbrechen");
        Button back=button("Zurück zum Menü"),pause=button("Partie pausieren");
        assertEquals(Color.TRANSPARENT,buttonFill(back).getDefaultColor());
        assertNull(back.getCompoundDrawablesRelative()[2]);
        assertPrimary(pause);
        for(Button action:new Button[]{retire,cancel}){
            assertSecondaryLink(action);
            assertTrue(Color.luminance(buttonFill(action).getDefaultColor())<Color.luminance(buttonFill(pause).getDefaultColor()));
        }
        assertEquals(buttonFill(retire).getDefaultColor(),buttonFill(cancel).getDefaultColor());
        layout();
        View screen=dialog().getWindow().getDecorView();
        for(Button action:new Button[]{retire,cancel}){
            centerOnScreen(screen,action);
            assertInsideRound(screen,action);
            assertLabelInsideRound(screen,action);
        }
        scroll().scrollBy(0,-(cancel.getHeight()+activity.dp(5))/2);
        captureRoundScreen(screen,screen.getWidth(),"end-match-round-192-de");

        String before=book().encode();
        click("Partie abbrechen");
        int discard=button("Partie verwerfen").getBackgroundTintList().getDefaultColor();
        assertTrue(Color.red(discard)>Color.blue(discard));
        click("Weiterspielen");
        click("Aufgabe werten");
        assertSecondaryLink(button(activity.tr("I quit")));
        back();
        click(activity.tr("Match options"));
        assertSecondaryLink(button("Partie abbrechen"));
        assertSecondaryLink(button("Aufgabe werten"));
        assertEquals(before,book().encode());
    }

    private android.content.res.ColorStateList buttonFill(Button button){
        android.graphics.drawable.RippleDrawable background=(android.graphics.drawable.RippleDrawable)button.getBackground();
        return ((android.graphics.drawable.GradientDrawable)background.getDrawable(0)).getColor();
    }

    private void assertSecondaryFill(Button button){
        String label=button.getText().toString();
        assertEquals(MainActivity.MENU_CONTROL_TEXT.getDefaultColor(),button.getTextColors().getDefaultColor());
        assertNull(button.getBackgroundTintList());
        android.content.res.ColorStateList surface=buttonFill(button);
        int fill=surface.getColorForState(button.getDrawableState(),surface.getDefaultColor());
        assertEquals(label,255,Color.alpha(fill));
        assertEquals(Color.red(fill),Color.green(fill));
        assertEquals(Color.green(fill),Color.blue(fill));
        assertTrue(Color.luminance(fill)<.1f);
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(button.getTextColors().getDefaultColor(),fill)>=4.5);
    }

    private void assertSecondaryLink(Button button){
        assertMenuLink(button);
        android.content.res.ColorStateList surface=buttonFill(button);
        int fill=surface.getDefaultColor();
        int pressed=surface.getColorForState(new int[]{android.R.attr.state_enabled,android.R.attr.state_pressed},fill);
        assertTrue(Color.luminance(pressed)>Color.luminance(fill));
    }

    @Test public void secondaryControlsUseFilledSurfacesAcrossDestinations(){
        root();assertPrimary(button("Current match"));
        assertNotNull(button("Current match").getCompoundDrawablesRelative()[2]);
        for(String label:new String[]{"Results (17)","Statistics","Live sharing & referee","Settings"}){
            assertSecondaryLink(button(label));
        }
        int secondary=buttonFill(button("Statistics")).getDefaultColor();
        click("Statistics");assertSecondaryLink(button("Results (17)"));
        click("Results (17)");assertEquals(MenuSection.RESULTS,location().section);layout();
        for(String label:new String[]{"Filters","Options"}){
            Button tool=button(label);float density=activity.getResources().getDisplayMetrics().density;
            assertMenuLink(tool);
            assertTrue(tool.getWidth()>=48*density);assertTrue(tool.getHeight()>=48*density);
        }
        Button older=button("Older results");
        assertSecondaryFill(older);
        assertEquals(secondary,buttonFill(older).getDefaultColor());
        assertNull(older.getCompoundDrawablesRelative()[2]);
        click("Options");assertSecondaryLink(button("Export CSV"));
        assertPrimary(button("Add past match"));assertNotNull(button("Add past match").getCompoundDrawablesRelative()[2]);
        assertTrue(all().stream().anyMatch(view->view instanceof TextView&&view.isAccessibilityHeading()
            &&"Result options".contentEquals(((TextView)view).getText())
            &&((TextView)view).getCurrentTextColor()==MenuSection.RESULTS.accent));
        back();back();assertEquals(MenuSection.STATISTICS,location().section);
    }

    @Test public void homeTilesAndSportRowsHaveTheirOwnReadableColours(){
        close();saveFixture(new MatchBook(new MatchState("idle-colours",3)));mainLaunch();
        String[] tiles={"New match","Results","Players","Sports","Settings"};
        MenuSection[] shortcuts={MenuSection.NEW_MATCH,MenuSection.RESULTS,MenuSection.PLAYERS,MenuSection.SPORTS,MenuSection.SETTINGS};
        int[][] states={{android.R.attr.state_enabled},{android.R.attr.state_enabled,android.R.attr.state_pressed}};
        for(int[] state:states){
            Set<Integer> seen=new HashSet<>();
            for(int i=0;i<tiles.length;i++){
                Button tile=button(tiles[i]);
                int fill=tile.getBackgroundTintList().getColorForState(state,0);
                int ink=tile.getTextColors().getColorForState(state,0);
                assertEquals(tiles[i],shortcuts[i].fill.getColorForState(state,0),fill);
                assertTrue(tiles[i],seen.add(fill));
                assertTrue(tiles[i],androidx.core.graphics.ColorUtils.calculateContrast(ink,fill)>=4.5);
            }
        }
        click("Sports");
        Set<Integer> sportColours=new HashSet<>();
        for(String sport:MatchFormat.SPORTS){
            Button row=button(MatchFormat.name(sport));
            assertEquals(sport,SportIcons.accent(sport),row.getTextColors().getDefaultColor());
            assertTrue(sport,sportColours.add(row.getTextColors().getDefaultColor()));
            assertTrue(row.getCompoundDrawablesRelative()[0] instanceof SportIcons);
            assertNotNull(row.getCompoundDrawablesRelative()[2]);
        }
        assertMenuLink(button("Players"));assertMenuLink(button("Settings"));
    }

    @Test public void scoreboardShortcutPreservesPauseAndStatisticsKeepsItsReturnRoute(){match();click("Pause match");match();click("Scoreboard");assertFalse(awake());assertFalse(preferences.contains(MatchDisplayPolicy.ACTIVE_MATCH));root();click("Statistics");click("Results (17)");assertTrue(hasButton("Back to Statistics"));back();assertTrue(has("Win rate:"));assertTrue(hasButton("Back to Menu"));}
    @Test public void resetAllFiltersRestoresTheCurrentSportFirstPageAndTopWithoutChangingMatchData(){
        close();MatchBook b=book();OpponentDirectory d=OpponentDirectory.decode(preferences.getString("opponent_book",""));String alice=d.opponentId("history-0");
        b.newMatch("tennis-current",3,MatchFormat.defaults("tennis"));b.add(0);
        for(int i=0;i<24;i++){MatchState m=new MatchState("tennis-filter-"+i,3,MatchFormat.defaults("tennis").mode(i%2==1));m.enterCompletedSet(6,1);m.enterCompletedSet(6,2);b.importResult(MatchResult.from(m,"2026-11-06",i+1));d.assign(m.id(),alice);}
        preferences.edit().putString("match_book",b.encode()).putString("opponent_book",d.encode()).putString("selected_sport","tennis").putString(MatchDisplayPolicy.ACTIVE_MATCH,"tennis-current").commit();launch();root();click("Results (24)");layout();scroll().scrollTo(0,110);assertTrue(scroll().getScrollY()>0);
        click("Filters");assertEquals("Reset all filters",all().stream().filter(v->v instanceof Button).map(v->((Button)v).getText().toString()).findFirst().get());click("Mode: All modes");click("Doubles");click("Opponent: All");click("Alice");click("Custom date range");List<EditText> inputs=new ArrayList<>();for(View v:all())if(v instanceof EditText)inputs.add((EditText)v);inputs.get(0).setText("2026-11-06");inputs.get(1).setText("2026-11-06");click("Apply dates");assertTrue(has("12 of 41 saved"));click("Older results");assertTrue(has("Page 2 of 2"));layout();scroll().scrollTo(0,110);
        String before=book().encode(),names=preferences.getString("opponent_book","");click("Reset all filters");layout();assertTrue(has("24 of 41 saved"));assertTrue(has("Page 1 of 3"));assertEquals(0,scroll().getScrollY());assertFalse(hasButton("Reset all filters"));assertEquals("tennis",ReflectionHelpers.getField(activity,"selectedSport"));assertEquals(before,book().encode());assertEquals(names,preferences.getString("opponent_book",""));assertEquals("tennis-current",preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""));
        click("Filters");assertTrue(hasButton("Mode: All modes"));assertTrue(hasButton("Opponent: All"));assertTrue(has("Dates: any to any"));click("Today");assertTrue(has("No matches fit"));click("Filters");click("Reset all filters");assertTrue(has("24 of 41 saved"));
    }
}
