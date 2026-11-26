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
        click("Change sport");assertTrue(text().contains("Keep Score"));assertFalse(text().contains("ALL SPORTS"));
        click("Players");assertTrue(text().contains("Keep Score"));assertFalse(text().contains("ALL SPORTS"));
        click("Back to sports");assertNotNull(button("Table tennis"));click("Settings");assertTrue(text().contains("Keep Score"));assertFalse(text().contains("ALL SPORTS"));
        click("Vibrations: Points and milestones");assertTrue(text().contains("Keep Score"));assertFalse(text().contains("ALL SPORTS"));click("Back to Settings");
        back();assertNotNull(button("Table tennis"));assertNotNull(button("Close app"));
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
        newMatch("Tennis");click("Create match");assertFalse(book().current().hasPoints());activity.action(TouchTargets.MENU);click("Change sport");assertFalse(button("Squash").isEnabled());ShadowDialog.getLatestDialog().dismiss();controller.pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity.class,new Intent(Intent.ACTION_MAIN)).setup();activity=controller.get();assertEquals("Scoreboard",ReflectionHelpers.getField(activity,"panelPage"));activity.action(TouchTargets.MENU);click("Change sport");assertFalse(button("Badminton").isEnabled());assertTrue(button("Tennis").isEnabled());
    }

    @Test public void launcherPutsSharingAfterAllSportsAndJoiningNeverNeedsALocalMatch(){
        cancelCurrent();click("Change sport");
        String before=book().encode();
        List<String> order=new ArrayList<>();
        for(View v:views())if(v instanceof Button)order.add(((Button)v).getText().toString());
        assertEquals(Arrays.asList("New Squash match","Badminton","Padel","Pickleball","Squash","Table tennis","Tennis","Players","Live sharing & referee","Settings","Close app"),order);
        click("Live sharing & referee");
        for(String item:new String[]{"Audience live link","Referee on phone","Share with watches","Join with watch code"})assertNotNull(button(item));
        click("Join with watch code");assertNotNull(button("Join match"));swipe();
        assertNotNull(button("Audience live link"));back();assertNotNull(button("Tennis"));assertEquals(before,book().encode());
    }

    @Test public void hostingFromTheLauncherChoosesTheSportAndCancelKeepsThePreviousBook(){
        cancelCurrent();click("Change sport");String before=book().encode();
        click("Live sharing & referee");click("Referee on phone");click("Badminton");
        assertNotNull(button("Create & invite referee"));click("Cancel");assertNotNull(button("Tennis"));
        back();click("Share with watches");click("Tennis");assertNotNull(button("Create & invite watches"));
        click("Cancel");back();back();assertEquals(before,book().encode());assertNull(LiveScoreClient.get(activity).session());
    }

    @Test public void audienceSetupReturnsToTheChosenSportAndOnlySharesOnRequest(){
        cancelCurrent();click("Change sport");click("Tennis");click("Live sharing & referee");
        click("Audience live link");click("Create match");
        assertEquals("tennis",book().current().format().sport);assertNotNull(button("Start live sharing"));
        assertNull(LiveScoreClient.get(activity).session());back();back();assertNotNull(button("Current match"));
        click("Current match");click("Live sharing & referee");click("Join with watch code");back();back();
        assertNotNull(button("Start match"));assertFalse(book().current().hasPoints());
    }

    @Test public void refereeAndOtherWatchesAreAvailableTogetherAndScoringStaysLocked() throws Exception {
        MatchRooms rooms=ReflectionHelpers.getField(activity,"rooms");
        rooms.pause();
        RoomClient client=new RoomClient(new RoomClient.Vault(){
            private String data="{}";
            public org.json.JSONObject read()throws Exception{return new org.json.JSONObject(data);}
            public void write(org.json.JSONObject room){data=room.toString();}
        },(method,path,token,body)->{throw new java.io.IOException("offline");});
        ReflectionHelpers.setField(rooms,"client",client);
        cancelCurrent();click("Change sport");click("Live sharing & referee");click("Referee on phone");
        click("Tennis");click("Create & invite referee");
        assertEquals("host",client.current().role);assertEquals("tennis",rooms.displayed().format().sport);
        String before=book().encode();
        click("Invite other watches");assertTrue(client.current().watchJoin);
        assertNull(find("Language"));assertNull(find("Set scores"));click("Back to score");
        activity.action(TouchTargets.MENU);click("Set scores");assertTrue(text().contains("Sets won"));back();
        click("Settings");click("Language · English");click("Deutsch");
        back();assertNotNull(button("Sprache · Deutsch"));back();assertNotNull(button("Einstellungen"));
        click("Live teilen & Schiedsrichter");assertNull(find("Sprache"));back();
        click("Einstellungen");click("Sprache · Deutsch");click("English");back();back();click("Back to score");
        activity.action(TouchTargets.RED);assertEquals(before,book().encode());assertNotNull(button("Set scores"));
    }

    @Test public void matchDetailsSeparatesTheTallyFromEachGameAndShowsLatestFirst(){
        MatchBook live=ReflectionHelpers.getField(activity,"book");
        while(!live.current().nextReady())live.add(0);
        live.nextGame();live.add(1);
        String before=live.encode();
        click("Current match");click("Match details");
        String details=text();assertTrue(details.contains("Games won"));assertTrue(details.contains("1  –  0"));
        assertTrue(details.contains("11 – 1"));assertTrue(details.contains("0 – 1"));
        assertTrue(details.indexOf("Game 2")<details.indexOf("Game 1"));back();assertEquals(before,live.encode());
    }

    @Test public void watchBackFromEachSharingEntryReturnsOneLevelAndKeepsTheMatch(){
        String before=book().encode();
        click("Change sport");click("Live sharing & referee");swipe();assertNotNull(button("Tennis"));
        click("Squash");click("Live sharing & referee");click("Back to Menu");assertNotNull(button("Current match"));
        click("Current match");click("Live sharing & referee");
        Dialog dialog=ShadowDialog.getLatestDialog();
        dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));
        dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));
        shadowOf(Looper.getMainLooper()).idle();assertNotNull(button("Pause match"));assertFalse(activity.isFinishing());
        click("Back to Menu");back();assertNotNull(button("Tennis"));assertEquals(before,book().encode());
    }

    @Test public void platformBackFromSharingUsesTheCurrentWindowOnly(){
        // Robolectric constructs a test Application rather than using the release application flags.
        activity.getApplicationInfo().setEnableOnBackInvokedCallback(true);
        assertTrue(android.window.WindowOnBackInvokedDispatcher.isOnBackInvokedCallbackEnabled(activity));
        click("Live sharing & referee");Dialog sharing=ShadowDialog.getLatestDialog();
        android.window.OnBackInvokedCallback callback=sharing.getWindow().getDecorView().getViewRootImpl().getOnBackInvokedDispatcher().getTopCallback();
        assertNotNull("Sharing window needs a system Back destination",callback);
        callback.onBackInvoked();shadowOf(Looper.getMainLooper()).idle();assertNotNull(button("Current match"));
        callback.onBackInvoked();shadowOf(Looper.getMainLooper()).idle();assertNotNull(button("Current match"));
        click("Live sharing & referee");
        android.window.OnBackInvokedCallback fromActivity=activity.getWindow().getDecorView().getViewRootImpl().getOnBackInvokedDispatcher().getTopCallback();
        assertNotNull(fromActivity);fromActivity.onBackInvoked();shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(button("Current match"));assertFalse(activity.isFinishing());
    }

    @Test public void cancellingConnectionReturnsToCodeAndIgnoresAValidLateReply() throws Exception {
        MatchRooms rooms=ReflectionHelpers.getField(activity,"rooms");rooms.pause();
        RoomClientUiTest.Relay relay=new RoomClientUiTest.Relay();
        RoomClient host=new RoomClient(new RoomClientUiTest.MemoryVault(),relay);
        RoomClient.Session invitation=host.prepare(RoomClientUiTest.snapshot(new MatchState("shared-test",3),false),false,true);host.sync();
        java.util.concurrent.CountDownLatch sent=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        RoomClient guest=new RoomClient(new RoomClientUiTest.MemoryVault(),(method,path,token,body)->{
            if(path.equals("/join")){sent.countDown();release.await(8,java.util.concurrent.TimeUnit.SECONDS);}
            return relay.send(method,path,token,body);
        });
        ReflectionHelpers.setField(rooms,"client",guest);
        String before=book().encode();
        click("Live sharing & referee");click("Join with watch code");
        for(View v:views())if(v instanceof EditText)((EditText)v).setText(invitation.joinCode);
        click("Join match");
        try{
            assertTrue(sent.await(5,java.util.concurrent.TimeUnit.SECONDS));swipe();
            assertNotNull(button("Join match"));assertTrue(text().contains(invitation.joinCode));back();back();
        }finally{release.countDown();}
        java.util.concurrent.ExecutorService io=ReflectionHelpers.getField(rooms,"io");io.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();assertNotNull(button("Current match"));assertFalse(activity.isFinishing());
        assertNull(guest.current());assertEquals(before,book().encode());
    }

    @Test public void completedControlsPrioritizeResultsAndRematchAndSkipNewSharingSetup(){
        MatchBook local=ReflectionHelpers.getField(activity,"book");local.retire(1);ReflectionHelpers.callInstanceMethod(activity,"afterMatchChange");
        click("Current match");assertNotNull(button("View result"));assertNotNull(button("New match"));assertNotNull(button("Share result card"));
        assertNull(find("Live sharing & referee"));assertNull(find("Cancel match"));assertNull(find("Start match"));
        click("View result");assertNotNull(button("Quick rematch"));back();assertNotNull(button("Match details"));
    }

    private void drainRoom(MatchRooms rooms) throws Exception {
        java.util.concurrent.ExecutorService io=ReflectionHelpers.getField(rooms,"io");
        io.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();
    }

    private RoomClient startRefereeMatch(RoomClientUiTest.Relay relay) {
        MatchRooms rooms=ReflectionHelpers.getField(activity,"rooms");
        rooms.pause();
        RoomClient host=new RoomClient(new RoomClientUiTest.MemoryVault(),relay);
        ReflectionHelpers.setField(rooms,"client",host);
        click("Current match");
        click("Live sharing & referee");
        click("Referee on phone");
        assertTrue(rooms.isHost());
        return host;
    }

    private Button firstAction() {
        for(View view:views())if(view instanceof Button)return (Button)view;
        throw new AssertionError("Missing match action");
    }

    @Test public void hostCanKeepOrDiscardFromTheFirstMatchMenuActionEvenOffline() throws Exception {
        RoomClientUiTest.Relay relay=new RoomClientUiTest.Relay();
        RoomClient host=startRefereeMatch(relay);
        assertEquals("Cancel match",firstAction().getText().toString());
        click("Cancel match");
        swipe();
        assertNotNull(button("Invite other watches"));
        click("Invite other watches");
        RoomClient.Session ready=host.sync();
        RoomClient follower=new RoomClient(new RoomClientUiTest.MemoryVault(),relay);
        follower.findJoin(ready.joinCode).accept();
        click("Back to score");
        ScoreView score=ReflectionHelpers.getField(activity,"counter");
        score.performClick();
        assertEquals("Cancel match",firstAction().getText().toString());
        String before=book().encode();
        click("Cancel match");
        assertTrue(text().contains("without saving a result"));
        click("Keep playing");
        assertNotNull(button("Set scores"));
        click("Cancel match");
        back();
        assertNotNull(button("Set scores"));
        assertEquals(before,book().encode());
        assertEquals(ready.id,host.current().id);

        relay.offline=true;
        click("Cancel match");
        click("Discard match");
        assertNull(host.current());
        assertNotEquals("active",book().current().id());
        assertNull(book().result("active"));
        assertNotNull(book().result("history"));
        assertTrue(button("Tennis").isEnabled());
        MatchRooms rooms=ReflectionHelpers.getField(activity,"rooms");
        drainRoom(rooms);
        relay.offline=false;
        host.retryOutbox();
        assertFalse(relay.rooms.containsKey(ready.id));
        assertThrows(RoomClient.ServiceError.class,follower::sync);
    }

    @Test public void discardConfirmationKeepsARefereeFinalThatArrivedBeforeTheTap() throws Exception {
        RoomClientUiTest.Relay relay=new RoomClientUiTest.Relay();
        RoomClient host=startRefereeMatch(relay);
        RoomClient.Session ready=host.sync();
        String before=book().encode();
        click("Back to score");
        activity.action(TouchTargets.MENU);
        click("Cancel match");
        MatchState remote=book().current();
        remote.retire(1);
        relay.refereeFinal(ready.id,RoomClientUiTest.snapshot(remote,true),ready.key);
        host.sync(); // The final is on disk, but the dialog still shows the earlier score.
        click("Discard match");
        assertEquals("Save to my results",firstAction().getText().toString());
        assertNull(find("Cancel match"));
        assertEquals(before,book().encode());
        assertTrue(host.current().snapshot().getBoolean("final"));
        click("Save to my results");
        assertNotNull(book().result("active"));
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers="w192dp-h192dp-round-xhdpi")
    public void germanHostSeesTheWholeCancelButtonWhenReopeningTheMatchMenu() {
        RuntimeEnvironment.setFontScale(1.3f);
        startRefereeMatch(new RoomClientUiTest.Relay());
        click("Back to score");
        activity.action(TouchTargets.MENU);
        click("Settings");click("Language · English");click("Deutsch");back();back();
        ScrollView menu=ReflectionHelpers.getField(activity,"panelScroll");
        menu.scrollTo(0,menu.getChildAt(0).getHeight());
        back();
        ScoreView score=ReflectionHelpers.getField(activity,"counter");
        score.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        Button cancel=firstAction();
        assertEquals("Partie abbrechen",cancel.getText().toString());
        View root=cancel.getRootView();
        int size=activity.getResources().getDisplayMetrics().widthPixels;
        int spec=View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY);
        root.measure(spec,spec);root.layout(0,0,size,size);
        menu=ReflectionHelpers.getField(activity,"panelScroll");
        assertEquals(0,menu.getScrollY());
        assertTrue(cancel.getHeight()>=48*activity.getResources().getDisplayMetrics().density);
        int[] pos=new int[2];cancel.getLocationInWindow(pos);
        float radius=size/2f;
        for(int x:new int[]{pos[0],pos[0]+cancel.getWidth()})
            for(int y:new int[]{pos[1],pos[1]+cancel.getHeight()})
                assertTrue("Cancel button outside the round screen",Math.hypot(x-radius,y-radius)<=radius);
        click("Partie abbrechen");
        assertNotNull(button("Partie verwerfen"));
        click("Weiterspielen");
        assertNotNull(button("Satzstände"));
    }

    @Test public void followingWatchKeepsLocalSettingsSeparateThroughTheFinalWhistle() throws Exception {
        MatchRooms rooms=ReflectionHelpers.getField(activity,"rooms");
        rooms.pause();
        RoomClientUiTest.Relay relay=new RoomClientUiTest.Relay();
        RoomClient host=new RoomClient(new RoomClientUiTest.MemoryVault(),relay);
        RoomClient follower=new RoomClient(new RoomClientUiTest.MemoryVault(),relay);
        MatchState remote=new MatchState("shared-settings",3);
        host.prepare(RoomClientUiTest.snapshot(remote,false),false,true);
        RoomClient.Session joined=follower.findJoin(host.sync().joinCode).accept();
        ReflectionHelpers.setField(rooms,"client",follower);
        ReflectionHelpers.callInstanceMethod(rooms,"apply",ReflectionHelpers.ClassParameter.from(RoomClient.Session.class,joined));
        String local=book().encode();
        activity.action(TouchTargets.MENU);
        assertNull(find("Cancel match"));
        click("Live sharing & referee");assertNull(find("Language"));assertNull(find("Settings"));
        assertNull(find("Cancel match"));assertNotNull(button("Leave shared match"));
        assertNull(find("Set scores"));assertNull(find("Connected devices"));back();
        click("Settings");click("Language · English");click("Deutsch");
        remote.retire(1);host.queue(RoomClientUiTest.snapshot(remote,true));host.sync();
        rooms.resume();shadowOf(Looper.getMainLooper()).idle();drainRoom(rooms);
        assertNotNull(button("✓ Deutsch"));back();back();
        assertNotNull(button("In meine Ergebnisse speichern"));
        click("In meine Ergebnisse speichern");back();assertNotNull(button("Einstellungen"));
        assertEquals(local,book().encode());assertEquals(joined.id,follower.current().id);
    }
    @Test public void hostCanInspectAndRemoveOneDeviceWithoutChangingTheCodeOrScore() throws Exception {
        MatchRooms rooms=ReflectionHelpers.getField(activity,"rooms");rooms.pause();
        RoomClientUiTest.Relay relay=new RoomClientUiTest.Relay();String id="0123456789abcdef01234567";
        java.util.concurrent.atomic.AtomicBoolean removed=new java.util.concurrent.atomic.AtomicBoolean();
        RoomClient host=new RoomClient(new RoomClientUiTest.MemoryVault(),(method,path,token,body)->{
            if(path.endsWith("/participants/"+id)&&method.equals("DELETE")){removed.set(true);return new org.json.JSONObject();}
            if(path.endsWith("/participants")){
                org.json.JSONArray rows=new org.json.JSONArray();
                if(!removed.get())rows.put(new org.json.JSONObject().put("id",id).put("role","watch").put("seenAt",5000));
                rows.put(new org.json.JSONObject().put("id","fedcba9876543210fedcba98").put("role","referee").put("seenAt",5000));
                return new org.json.JSONObject().put("members",rows).put("now",6000);
            }
            return relay.send(method,path,token,body);
        });
        ReflectionHelpers.setField(rooms,"client",host);click("Current match");click("Live sharing & referee");click("Share with watches");
        RoomClient.Session ready=host.sync();ReflectionHelpers.callInstanceMethod(rooms,"apply",ReflectionHelpers.ClassParameter.from(RoomClient.Session.class,ready));rooms.hub();
        String before=book().encode();click("Connected devices");drainRoom(rooms);
        click("Player watch #234567 · just now");back();drainRoom(rooms);assertFalse(removed.get());
        click("Player watch #234567 · just now");click("Remove device");drainRoom(rooms);drainRoom(rooms);
        assertNull(find("Player watch #234567 · just now"));assertNotNull(button("Referee #DCBA98 · just now"));
        back();assertEquals(ready.joinCode,host.current().joinCode);assertEquals(before,book().encode());
        click("Connected devices");back();drainRoom(rooms);assertNotNull(button("Connected devices"));assertFalse(activity.isFinishing());
    }

}
