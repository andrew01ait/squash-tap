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
    private boolean awake(){return (activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;}
    private TransferClient.Session readyTransfer(){TransferClient.Session s=new TransferClient.Session();s.expiresAt=System.currentTimeMillis()+600000;ReflectionHelpers.setField(activity,"transfer",s);return s;}
    private void openFirstResult(){for(View v:all())if(v.getContentDescription()!=null&&v.getContentDescription().toString().contains("Open match result")){v.performClick();shadowOf(Looper.getMainLooper()).idle();return;}throw new AssertionError("Missing result card");}
    private List<String> sections(){
        List<String> titles=new ArrayList<>();
        for(View view:all())if(view.isAccessibilityHeading()&&view.getContentDescription()!=null)
            titles.add(view.getContentDescription().toString());
        return titles;
    }
    @Test public void mixedMenusUseTaskHeadingsAndSingleChoiceScreensStayCompact(){
        root();assertEquals(Arrays.asList("Match","Match history","Sport","App"),sections());
        click("Current match");assertEquals(Arrays.asList("Score","Match setup","Sharing","End match"),sections());
        click("Live sharing & referee");assertEquals(Arrays.asList("Share your match","Join another match"),sections());
        click("Join with watch code");assertTrue(sections().isEmpty());back();back();back();
        click("Settings");assertEquals(Arrays.asList("Language & display","Feedback","Data","Watch shortcuts","App"),sections());
        click("Language · English");assertTrue(sections().isEmpty());back();back();
        click("Players");assertEquals(Arrays.asList("Saved players","Manage names"),sections());back();
        click("Results (17)");assertEquals(Arrays.asList("Saved matches"),sections());
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
    @Test public void resultActionsUseTheSameFullWidthRowsAsOtherMenus(){
        results();layout();
        Button footer=button("Back to Menu");
        for(String label:new String[]{"Filters","Options","Add past match"}){
            Button row=button(label);
            assertEquals(footer.getWidth(),row.getWidth());
            assertNotNull(row.getCompoundDrawablesRelative()[2]);
            assertEquals(button("Filters").getTextSize(),row.getTextSize(),0.01f);
            assertTrue(row.getHeight()>=48*activity.getResources().getDisplayMetrics().density);
        }
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
    private String decodeRoundQr(View root, int diameter, String name) throws Exception {
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
        AppDiagnostics.record(activity, "NFC share: commands=0 mapping=0 stage=waiting");
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
            assertTrue(report.contains("NFC share: commands=0 mapping=0 stage=waiting"));
            assertFalse(report.contains("synthetic.frame"));
            assertFalse(report.contains("legacy event 0\n"));
        }
        click(activity.tr("Back to Diagnostics"));
        assertTrue(has("synthetic.frame119"));
    }

    private void matchInGerman(){
        root();click("Aktuelle Partie");
        assertEquals(Arrays.asList("Spielstand","Partie einrichten","Teilen","Partie beenden"),sections());
    }
    @Test public void aHistoricalResultHasNoNextMatchSectionAndKeepsItsReturn(){
        results();openFirstResult();assertEquals(Arrays.asList("Manage result"),sections());
        assertFalse(hasButton("Quick rematch"));back();assertTrue(hasButton("Filters"));
    }
    private ScrollView scroll(){for(View v:all())if(v instanceof ScrollView)return (ScrollView)v;throw new AssertionError("Missing scroll");}
    private void layout(){View v=dialog().getWindow().getDecorView();int side=(int)(233*activity.getResources().getDisplayMetrics().density);v.measure(View.MeasureSpec.makeMeasureSpec(side,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(side,View.MeasureSpec.EXACTLY));v.layout(0,0,side,side);shadowOf(Looper.getMainLooper()).idle();}

    @Test public void rootGroupsSportHistoryAndSharedAppDestinations(){root();List<String> labels=new ArrayList<>();for(View v:all())if(v instanceof Button)labels.add(((Button)v).getText().toString());assertEquals(Arrays.asList("Current match","Players","Live sharing & referee","Results (17)","Statistics","Favourite formats","Scoring rules","Change sport","Settings","Back to score"),labels);assertFalse(hasButton("Manage on phone"));int c=button("Players").getBackgroundTintList().getDefaultColor();assertTrue(Color.red(c)>Color.green(c)&&Color.blue(c)>Color.green(c));}
    @Test public void currentMatchPutsTheSessionActionBeforeSecondaryControls(){match();List<String> labels=new ArrayList<>();for(View v:all())if(v instanceof Button&&!((Button)v).getText().toString().equals("Scoreboard"))labels.add(((Button)v).getText().toString());assertEquals(Arrays.asList("Pause match","Enter set score","Match details","Opponent: Alice","Match options","Live sharing & referee","Save retirement result","Cancel match","Back to Menu"),labels);click("Pause match");assertFalse(awake());match();assertTrue(hasButton("Resume match"));assertFalse(hasButton("Pause match"));}
    @Test public void finishedCurrentMatchHidesSetupAndScoringActions(){close();MatchBook b=book();b.retire(1);preferences.edit().putString("match_book",b.encode()).remove(MatchDisplayPolicy.ACTIVE_MATCH).commit();launch();match();assertTrue(hasButton("View result"));assertTrue(hasButton("New match"));assertFalse(hasButton("Enter set score"));assertFalse(hasButton("Match options"));assertFalse(hasButton("Start match"));assertFalse(hasButton("Resume match"));}
    @Test public void choosingANewFormatStagesItUntilCreateIsExplicit(){String before=book().encode();match();click("Match options");click("New match");click("Match length: Best of 3");click("Best of 5 · first to 3");assertTrue(hasButton("Match length: Best of 5"));assertEquals(before,book().encode());click("Create match");assertTrue(has("Replace this match?"));assertEquals(before,book().encode());click("Replace & create match");assertEquals(5,book().current().bestOf());assertFalse(book().current().hasPoints());assertFalse(preferences.contains(MatchDisplayPolicy.ACTIVE_MATCH));}
    @Test public void cancelledNewMatchReturnsToItsCallerAndKeepsTheStagedChoice(){String before=book().encode();match();click("Match options");click("New match");click("Match length: Best of 3");click("Best of 5 · first to 3");click("Create match");back();assertTrue(hasButton("Match length: Best of 5"));back();assertTrue(hasButton("Save retirement result"));assertEquals(before,book().encode());assertEquals("current",preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""));}
    @Test public void buttonAndSystemBackFollowTheSameNamedParent(){match();click("Match options");click("Match length: Best of 3");click("Back to Match options");assertTrue(hasButton("Save retirement result"));click("Match length: Best of 3");back();assertTrue(hasButton("Save retirement result"));back();assertTrue(hasButton("Pause match"));back();assertTrue(hasButton("Settings"));back();assertFalse(ShadowDialog.getLatestDialog().isShowing());assertTrue(awake());}
    @Test public void nativeSwipeDismissUsesTheSameParentWithoutPausingPlay(){match();click("Match options");SwipeDismissFrameLayout frame=null;for(View v:all())if(v instanceof SwipeDismissFrameLayout){frame=(SwipeDismissFrameLayout)v;break;}assertNotNull(frame);assertTrue(frame.isSwipeable());ReflectionHelpers.callInstanceMethod(frame,"performDismissFinishedCallbacks");shadowOf(Looper.getMainLooper()).idle();assertTrue(hasButton("Pause match"));assertEquals("current",preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""));}
    @Test public void diagnosticQrBackReturnsToDiagnosticsAndReleasesItsScreenFlag(){root();click("Settings");click("Diagnostics");click("Send to phone");Dialog qr=dialog();assertTrue((qr.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0);back();assertTrue(hasButton("Copy details"));assertEquals(0,dialog().getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);back();assertTrue(hasButton("Check for updates"));}
    @Test public void resultDetailsPickerAndDeleteCancelPreserveFiltersPageAndScroll(){results();click("Filters");click("Opponent: All");click("Alice");click("Back to Results");assertTrue(has("12 of 17 saved"));click("Older results");assertTrue(has("Page 2 of 2"));layout();scroll().scrollTo(0,110);int position=scroll().getScrollY();assertTrue(position>0);openFirstResult();click("Opponent: Alice");back();assertTrue(hasButton("Delete this result"));click("Delete this result");back();click("Back to Results");layout();assertTrue(has("12 of 17 saved"));assertTrue(has("Page 2 of 2"));assertEquals(position,scroll().getScrollY());}
    @Test public void deleteAllIsKeptInsideResultOptions(){results();assertFalse(hasButton("Delete all results"));click("Options");Button delete=button("Delete all results");int c=delete.getBackgroundTintList().getDefaultColor();assertTrue(Color.red(c)>Color.blue(c));click("Delete all results");back();assertTrue(hasButton("Export CSV"));assertEquals(17,book().count());}
    @Test public void enteredSetIsAvailableDirectlyInTheCurrentMatchAndCancelChangesNothing(){String before=book().encode();match();click("Enter set score");assertTrue(has("Set 1 score"));click("Me won");assertTrue(hasButton("Review set score"));for(View v:all())if(v instanceof NumberPicker)assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS,((NumberPicker)v).getDescendantFocusability());back();assertTrue(hasButton("Pause match"));assertEquals(before,book().encode());}
    @Test public void pastMatchEntryBelongsToResultsAndCancelKeepsTheLiveMatch(){String before=book().encode();match();assertFalse(hasButton("Add past match"));results();click("Add past match");assertTrue(hasButton("Save match result"));assertFalse(hasButton("Enter set score"));back();assertTrue(hasButton("Add past match"));assertEquals(before,book().encode());assertEquals("current",preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""));}
    @Test public void resultsPhoneActionSetsItsLandingViewAndReturnsToResults(){readyTransfer();results();click("Options");click("View results on phone");assertTrue(hasButton("Show phone QR"));String link=ReflectionHelpers.callInstanceMethod(activity,"phoneLink");assertTrue(link.endsWith("&view=results"));click("Show phone QR");assertEquals("Phone QR",ReflectionHelpers.getField(activity,"panelPage"));back();assertTrue(hasButton("Receive changes"));back();assertTrue(hasButton("Add past match"));}
    @Test public void importPhoneActionTargetsImportAndReturnsToResultOptions(){readyTransfer();results();click("Options");click("Import CSV");String link=ReflectionHelpers.callInstanceMethod(activity,"phoneLink");assertTrue(link.endsWith("&view=import"));back();assertTrue(hasButton("Delete all results"));assertTrue(hasButton("Export CSV"));}
    @Test public void namesPhoneActionPreservesItsOpponentPickerCaller(){readyTransfer();match();click("Match options");click("Opponent: Alice");click("Edit names on phone");String link=ReflectionHelpers.callInstanceMethod(activity,"phoneLink");assertTrue(link.endsWith("&view=opponents"));back();assertTrue(hasButton("No name"));assertTrue(hasButton("Bob"));back();assertTrue(hasButton("Match length: Best of 3"));}
    @Test public void aLatePhoneLinkCannotReopenPanelsAfterReturningToScoreboard(){results();click("Options");click("View results on phone");Dialog waiting=dialog();assertTrue(has("Preparing phone link"));click("Scoreboard");assertFalse(waiting.isShowing());TransferClient.Session created=new TransferClient.Session();created.expiresAt=System.currentTimeMillis()+600000;ReflectionHelpers.callInstanceMethod(activity,"transferCreated",ReflectionHelpers.ClassParameter.from(TransferClient.Session.class,created),ReflectionHelpers.ClassParameter.from(boolean.class,false),ReflectionHelpers.ClassParameter.from(boolean.class,false),ReflectionHelpers.ClassParameter.from(String.class,""),ReflectionHelpers.ClassParameter.from(Dialog.class,waiting));assertFalse(ShadowDialog.getLatestDialog().isShowing());assertEquals(created,ReflectionHelpers.getField(activity,"transfer"));assertTrue(awake());}
    @Test public void navigationRowsAreLighterAndKeepLargeTargetsAndExplicitBack(){root();Button nav=button("Results (17)");assertNull(nav.getBackgroundTintList());assertNotNull(nav.getCompoundDrawablesRelative()[2]);assertTrue(nav.getMinimumHeight()>=48);assertTrue(Color.blue(nav.getCurrentTextColor())>Color.red(nav.getCurrentTextColor()));nav.setPressed(true);assertNull(nav.getBackgroundTintList());nav.setPressed(false);nav.setEnabled(false);assertTrue(Color.blue(nav.getCurrentTextColor())>Color.red(nav.getCurrentTextColor()));assertNull(button("Back to score").getBackgroundTintList());results();layout();for(String label:new String[]{"Filters","Options","Add past match"}){Button tool=button(label);float density=activity.getResources().getDisplayMetrics().density;assertTrue(tool.getWidth()>=48*density);assertTrue(tool.getHeight()>=48*density);}}
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
