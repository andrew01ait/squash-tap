package at.andrew.squashtap;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Collections;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowTextToSpeech;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SpokenScoreUiTest {
    private static final String[] PLAYERS = {"Andrew Lindley", "Sam Reed", "Adrian Huber", "Michaela Müller"};
    private Context context;
    private SharedPreferences prefs;
    private WatchLanguage ui;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        prefs = context.getSharedPreferences("squash", Context.MODE_PRIVATE);
        prefs.edit().clear().putString(WatchLanguage.PREFERENCE, "en").commit();
        ui = WatchLanguage.get(context);
    }

    private String point(MatchState match, int winner) {
        MatchState before = MatchState.decode(match.encode());
        assertTrue(match.add(winner));
        return SpokenScore.call(ui, before, match, false, slot -> PLAYERS[slot]);
    }

    private static void repeat(MatchState match, int winner, int count) {
        for (int i = 0; i < count; i++) assertTrue(match.add(winner));
    }

    @Test public void squashCallsServingSideFirstAndNamesTheGameAndMatchWinner() {
        MatchState match = new MatchState("live", 3);
        assertEquals("1, 0.", point(match, 0));
        assertEquals("Hand out. 1, 1.", point(match, 1));
        assertEquals("2, 1.", point(match, 1));
        repeat(match, 1, 8);
        assertEquals("11 to 1. Game to Sam.", point(match, 1));
        assertTrue(match.nextGame());
        repeat(match, 1, 10);
        assertEquals("11 to 0. Match to Sam. 2 games to 0.", point(match, 1));
    }

    @Test public void tennisCallsPointsDeuceAdvantageAndGamesAcrossUndo() {
        MatchState match = new MatchState("tennis", 3, MatchFormat.defaults("tennis"));
        match.serving(1, 0);
        assertEquals("15, love.", point(match, 1));
        repeat(match, 1, 2);
        repeat(match, 0, 2);
        assertEquals("Deuce.", point(match, 0));
        assertEquals("Advantage Andrew.", point(match, 0));
        assertEquals("First game, Andrew.", point(match, 0));
        assertTrue(match.undo());
        assertEquals("Correction. Advantage Andrew.", SpokenScore.call(ui, null, match, true, slot -> PLAYERS[slot]));
    }

    @Test public void padelDecidingPointAndTennisTieBreakUseTheirOwnScoring() {
        MatchState padel = new MatchState("padel", 3, MatchFormat.defaults("padel").deuce("no-ad"));
        repeat(padel, 0, 3);
        repeat(padel, 1, 2);
        assertEquals("Golden Point.", point(padel, 1));
        prefs.edit().putString(WatchLanguage.PREFERENCE, "de").commit();
        ui = WatchLanguage.get(context);
        MatchState tennis = new MatchState("tennis", 3, MatchFormat.defaults("tennis"));
        for (int game = 0; game < 11; game++) repeat(tennis, game % 2, 4);
        repeat(tennis, 1, 3);
        assertEquals("Spiel für Sam. Ausgleich, 6 zu 6. Tie-Break.", point(tennis, 1));
        assertEquals("1 zu 0 für Sam.", point(tennis, 1));
        repeat(tennis, 1, 5);
        assertEquals("Spiel und Satz für Sam. 7 zu 6.", point(tennis, 1));
    }

    @Test public void pickleballCallsIncludeServerChangesWithoutAPoint() {
        MatchState match = new MatchState("pickle", 1, MatchFormat.defaults("pickleball"));
        match.serving(0, 1);
        assertEquals("1, 0, 2.", point(match, 0));
        assertEquals("Side out. 0, 1, 1.", point(match, 1));
        assertEquals("Second server. 0, 1, 2.", point(match, 0));
        assertTrue(match.undo());
        assertEquals("Correction. 0, 1, 1.", SpokenScore.call(ui, null, match, true, i -> "Sam"));
    }

    @Test public void badmintonAndTableTennisUseKnownServiceAndLocalLanguage() {
        prefs.edit().putString(WatchLanguage.PREFERENCE, "fr").commit();
        ui = WatchLanguage.get(context);
        MatchState badminton = new MatchState("badminton", 3, MatchFormat.defaults("badminton"));
        assertEquals("1 à 0.", point(badminton, 1));
        prefs.edit().putString(WatchLanguage.PREFERENCE, "es").commit();
        ui = WatchLanguage.get(context);
        MatchState table = new MatchState("table", 3, MatchFormat.defaults("table-tennis"));
        assertEquals("0 a 1.", point(table, 1));
        MatchState withServe = new MatchState("serve", 3, MatchFormat.defaults("table-tennis"));
        withServe.serving(1, 0);
        assertEquals("1 a 0.", point(withServe, 1));
    }

    @Test public void refereeCallsLatestScoreOnceAndRecognizesCorrections() {
        MatchState match = new MatchState("room", 3);
        assertEquals("", SpokenScore.remote(ui, null, match, i -> "Sam"));
        MatchState before = MatchState.decode(match.encode());
        repeat(match, 0, 3);
        assertEquals("3, 0.", SpokenScore.remote(ui, before, match, i -> "Sam"));
        before = MatchState.decode(match.encode());
        assertEquals("", SpokenScore.remote(ui, before, match, i -> "Sam"));
        match.undo();
        assertEquals("Correction. 2, 0.", SpokenScore.remote(ui, before, match, i -> "Sam"));
        assertEquals("", SpokenScore.remote(ui, before, new MatchState("different", 3), i -> "Sam"));
    }

    private void language(String code) {
        prefs.edit().putString(WatchLanguage.PREFERENCE, code).commit();
        ui = WatchLanguage.get(context);
    }

    @Test public void squashDistinguishesGameBallMatchBallReceiverAndTenAll() {
        language("de");
        MatchState match = new MatchState("squash", 3);
        repeat(match, 0, 9);
        repeat(match, 1, 8);
        assertEquals("Aufschlagwechsel. 10 zu 8. Satzball.", point(match, 0));
        assertEquals("Aufschlagwechsel. 9 zu 10. Satzball für Andrew.", point(match, 1));
        assertEquals("10 zu 10. Zwei Punkte Vorsprung nötig.", point(match, 1));
        assertEquals("Aufschlagwechsel. 11 zu 10. Satzball.", point(match, 0));
        assertEquals("12 zu 10. Satz für Andrew.", point(match, 0));
        assertTrue(match.nextGame());
        repeat(match, 1, 8);
        repeat(match, 0, 9);
        assertEquals("10 zu 8. Matchball.", point(match, 0));
        assertEquals("Aufschlagwechsel. 9 zu 10. Matchball für Andrew.", point(match, 1));
        assertEquals("11 zu 9. Match für Andrew. 2 zu 0 in Sätzen.", point(match, 0));
    }

    @Test public void badmintonCallsServiceAndGameOrMatchPointsAtBothPresetLimits() {
        language("de");
        for (int target : new int[]{21, 15}) {
            MatchFormat format = MatchFormat.defaults("badminton").target(target);
            MatchState match = new MatchState("badminton-" + target, 3, format);
            match.serving(0, 1);
            assertEquals("Aufschlagwechsel. 1 zu 0.", point(match, 1));
            assertEquals("Aufschlagwechsel. 1 zu 1.", point(match, 0));
            for (int i = 1; i < format.cap() - 2; i++) {
                repeat(match, 0, 1);
                repeat(match, 1, 1);
            }
            int last = format.cap() - 1;
            assertEquals(last + ", Satzball, " + (last - 1) + ".", point(match, 1));
            assertEquals("Aufschlagwechsel. " + last + ", Satzball, " + last + ".", point(match, 0));
            assertEquals("Satz für Andrew. " + format.cap() + " zu " + last + ".", point(match, 0));
            assertTrue(match.nextGame());
            repeat(match, 1, 8);
            repeat(match, 0, target - 2);
            assertEquals((target - 1) + ", Matchball, 8.", point(match, 0));
            assertEquals("Match für Andrew. " + target + " zu 8. 2 zu 0 in Sätzen.", point(match, 0));
        }
    }

    @Test public void tableTennisCallsTheNextServerFirstThroughDeuceAndWinnerFirstAtGameEnd() {
        language("de");
        MatchState match = new MatchState("table", 5, MatchFormat.defaults("table-tennis"));
        match.serving(1, 0);
        for (int i = 0; i < 9; i++) {
            repeat(match, 0, 1);
            repeat(match, 1, 1);
        }
        assertEquals("10 zu 9.", point(match, 0));
        assertEquals("10 zu 10.", point(match, 1));
        assertEquals("10 zu 11.", point(match, 1));
        assertEquals("11 zu 11.", point(match, 0));
        assertEquals("11 zu 12.", point(match, 1));
        assertEquals("13 zu 11. Satz für Sam.", point(match, 1));
        assertTrue(match.nextGame());
        assertTrue(match.enterCompletedSet(9, 11));
        assertTrue(match.nextGame());
        repeat(match, 1, 10);
        assertEquals("11 zu 0. Match für Sam. 3 zu 0 in Sätzen.", point(match, 1));
    }

    @Test public void padelDistinguishesStarGoldenAndAdvantageAndNamesTheSetLeader() {
        language("de");
        MatchState padel = new MatchState("padel", 3, MatchFormat.defaults("padel"));
        repeat(padel, 1, 8);
        repeat(padel, 0, 3);
        repeat(padel, 1, 2);
        assertEquals("Einstand.", point(padel, 1));
        assertEquals("Vorteil Andrew und Adrian.", point(padel, 0));
        assertEquals("Einstand.", point(padel, 1));
        assertEquals("Vorteil Sam und Michaela.", point(padel, 1));
        assertEquals("Star Point.", point(padel, 0));
        assertEquals("Spiel für Andrew und Adrian. 2 zu 1 für Sam und Michaela.", point(padel, 0));
        String[] modes = {"no-ad", "advantage"};
        String[] calls = {"Golden Point.", "Einstand."};
        for (int i = 0; i < modes.length; i++) {
            MatchState choice = new MatchState("choice", 3, MatchFormat.defaults("padel").deuce(modes[i]));
            repeat(choice, 0, 3);
            repeat(choice, 1, 2);
            assertEquals(calls[i], point(choice, 1));
        }
    }

    @Test public void tieBreaksNameTheLeaderRegardlessOfServeInBothSportsAndBothKindsOfSet() {
        language("de");
        for (String sport : new String[]{"padel", "tennis"}) {
            MatchFormat format = MatchFormat.defaults(sport);
            String blue = format.doubles ? "Andrew und Adrian" : "Andrew";
            String red = format.doubles ? "Sam und Michaela" : "Sam";
            MatchState ordinary = new MatchState("set", 3, format);
            ordinary.serving(0, 1);
            for (int game = 0; game < 12; game++) repeat(ordinary, game % 2, 4);
            assertEquals("1 zu 0 für " + blue + ".", point(ordinary, 0));
            assertEquals(1, ordinary.server() % 2);
            assertEquals("1 beide.", point(ordinary, 1));
            assertEquals("2 zu 1 für " + red + ".", point(ordinary, 1));
            repeat(ordinary, 1, 4);
            assertEquals("Spiel und Satz für " + red + ". 7 zu 6.", point(ordinary, 1));
            MatchState decider = new MatchState("decider", 3, format.finalTie(true));
            assertTrue(decider.enterCompletedSet(6, 4));
            assertTrue(decider.nextGame());
            assertTrue(decider.enterCompletedSet(4, 6));
            assertTrue(decider.nextGame());
            decider.serving(0, 1);
            assertEquals("1 zu 0 für " + blue + ".", point(decider, 0));
            assertEquals("1 beide.", point(decider, 1));
            repeat(decider, 1, 8);
            assertEquals("Match für " + red + ". 10 zu 1 im Match-Tie-Break. 2 zu 1 in Sätzen.", point(decider, 1));
        }
    }

    @Test public void pickleballUsesTwoNumbersForSinglesAndRallyAndThreeForTraditionalDoubles() {
        language("de");
        MatchFormat traditional = MatchFormat.defaults("pickleball");
        MatchState single = new MatchState("single", 1, traditional.mode(false));
        single.serving(0, 1);
        assertEquals("Aufschlagwechsel. 0, 0.", point(single, 1));
        repeat(single, 1, 10);
        assertEquals("11 zu 0. Match für Sam.", point(single, 1));
        for (String rule : new String[]{"rally-2025", "rally-2026"}) {
            MatchState rally = new MatchState(rule, 1, traditional.deuce(rule));
            rally.serving(0, 1);
            repeat(rally, 0, 10);
            assertEquals("1, 10.", point(rally, 1));
            if (rule.equals("rally-2025")) assertEquals("10, 1.", point(rally, 0));
            assertEquals("11 zu 1. Match für Andrew und Adrian.", point(rally, 0));
        }
        MatchState doubles = new MatchState("traditional", 3, traditional);
        doubles.serving(0, 1);
        assertEquals("1, 0, 2.", point(doubles, 0));
        assertEquals("Aufschlagwechsel. 0, 1, 1.", point(doubles, 1));
        assertEquals("Zweiter Aufschläger. 0, 1, 2.", point(doubles, 0));
        assertEquals("Aufschlagwechsel. 1, 0, 1.", point(doubles, 0));
        repeat(doubles, 0, 9);
        assertEquals("11 zu 0. Satz für Andrew und Adrian.", point(doubles, 0));
    }

    @Test public void refereeCoalescedUpdatesAnnounceCurrentGameOrScoreAndSkipIntermediateServiceChanges() {
        MatchState padel = new MatchState("padel", 3, MatchFormat.defaults("padel"));
        MatchState before = MatchState.decode(padel.encode());
        repeat(padel, 0, 4);
        repeat(padel, 1, 4);
        assertEquals("Game, Sam and Michaela. 1 all.", SpokenScore.remote(ui, before, padel, i -> PLAYERS[i]));
        MatchState pickle = new MatchState("pickle", 1, MatchFormat.defaults("pickleball"));
        pickle.serving(0, 1);
        before = MatchState.decode(pickle.encode());
        repeat(pickle, 1, 2);
        assertEquals("1, 0, 1.", SpokenScore.remote(ui, before, pickle, i -> PLAYERS[i]));
        before = MatchState.decode(pickle.encode());
        assertEquals("Second server. 1, 0, 2.", point(pickle, 0));
        assertEquals("Second server. 1, 0, 2.", SpokenScore.remote(ui, before, pickle, i -> PLAYERS[i]));
        assertEquals("", SpokenScore.remote(ui, pickle, MatchState.decode(pickle.encode()), i -> PLAYERS[i]));
    }

    @Test public void allFourWatchLanguagesHaveSportSpecificRallyCalls() {
        String[] languages = {"en", "de", "fr", "es"};
        String[] squashBall = {"10, 0. Game ball.", "10 zu 0. Satzball.", "10 à 0. Balle de jeu.", "10 a 0. Bola de juego."};
        String[] service = {"Service over. 1, 2.", "Aufschlagwechsel. 1 zu 2.", "Changement de service. 1 à 2.", "Cambio de saque. 1 a 2."};
        String[] tableScore = {"2, 0.", "2 zu 0.", "2 à 0.", "2 a 0."};
        String[] secondServer = {"Second server. ", "Zweiter Aufschläger. ", "Deuxième serveur. ", "Segundo servidor. "};
        String[] golden = {"Golden Point.", "Golden Point.", "Point en or.", "Punto de oro."};
        String[] deciding = {"Deciding point.", "Entscheidungspunkt.", "Point décisif.", "Punto decisivo."};
        for (int i = 0; i < languages.length; i++) {
            language(languages[i]);
            MatchState squash = new MatchState("squash", 3);
            repeat(squash, 0, 9);
            assertEquals(squashBall[i], point(squash, 0));
            MatchState badminton = new MatchState("badminton", 3, MatchFormat.defaults("badminton"));
            repeat(badminton, 0, 2);
            assertEquals(service[i], point(badminton, 1));
            MatchState table = new MatchState("table", 3, MatchFormat.defaults("table-tennis"));
            table.serving(0, 1);
            repeat(table, 1, 1);
            assertEquals(tableScore[i], point(table, 1));
            MatchState pickle = new MatchState("pickle", 1, MatchFormat.defaults("pickleball"));
            pickle.serving(0, 1);
            repeat(pickle, 1, 1);
            assertEquals(secondServer[i] + "0, 0, 2.", point(pickle, 0));
            for (String sport : new String[]{"tennis", "padel"}) {
                MatchState game = new MatchState(sport, 3, MatchFormat.defaults(sport).deuce("no-ad"));
                repeat(game, 0, 3);
                repeat(game, 1, 2);
                assertEquals(sport.equals("tennis") ? deciding[i] : golden[i], point(game, 1));
            }
        }
    }

    @Test public void callsUseGivenNamesAndPreserveHyphensApostrophesAccentsAndNicknames() {
        String[] names = {"\u00a0Anne-Marie\u202fMüller", "de la Cruz, José Luis", "D’André Dupont", "Susi"};
        assertEquals("Anne-Marie", SpokenScore.sideName(ui, 0, false, i -> names[i]));
        assertEquals("José", SpokenScore.sideName(ui, 1, false, i -> names[i]));
        assertEquals("Anne-Marie and D’André", SpokenScore.sideName(ui, 0, true, i -> names[i]));
        assertEquals("José and Susi", SpokenScore.sideName(ui, 1, true, i -> names[i]));
        names[0] = "Andrew Lindley, Jr.";
        assertEquals("Andrew", SpokenScore.sideName(ui, 0, false, i -> names[i]));
    }

    @Test public void identicalGivenNamesAndIncompleteOrIndistinguishableTeamsUseTheirSideNumber() {
        language("de");
        String[] names = {"Alex Roth", "alex Schmidt", "Chris Grün", "Chris Huber"};
        assertEquals("Spieler 1", SpokenScore.sideName(ui, 0, false, i -> names[i]));
        assertEquals("Spieler 2", SpokenScore.sideName(ui, 1, false, i -> names[i]));
        assertEquals("Team 1", SpokenScore.sideName(ui, 0, true, i -> names[i]));
        names[1] = "Chris Huber";
        names[3] = "Alex Schmidt";
        assertEquals("Team 2", SpokenScore.sideName(ui, 1, true, i -> names[i]));
        names[3] = "";
        assertEquals("Team 2", SpokenScore.sideName(ui, 1, true, i -> names[i]));
        assertEquals("Alex und Chris", SpokenScore.sideName(ui, 0, true, i -> names[i]));
    }

    @Test public void tennisGameWinnerCanTrailLevelOrLeadInTheSetOnBothWatchScoringPaths() {
        MatchState tennis = new MatchState("games", 3, MatchFormat.defaults("tennis"));
        repeat(tennis, 1, 12);
        repeat(tennis, 0, 7);
        MatchState before = MatchState.decode(tennis.encode());
        assertTrue(tennis.add(0));
        String[] languages = {"en", "de", "fr", "es"};
        String[] calls = {"Game, Andrew. Sam leads 3 games to 2.", "Spiel für Andrew. Sam führt 3 zu 2.",
                "Jeu pour Andrew. Sam mène 3 jeux à 2.", "Juego para Andrew. Sam va por delante 3 a 2."};
        for (int i = 0; i < calls.length; i++) {
            language(languages[i]);
            assertEquals(calls[i], SpokenScore.call(ui, before, tennis, false, slot -> PLAYERS[slot]));
            assertEquals(calls[i], SpokenScore.remote(ui, before, tennis, slot -> PLAYERS[slot]));
        }
        language("de");
        repeat(tennis, 0, 3);
        assertEquals("Spiel für Andrew. Ausgleich, 3 zu 3.", point(tennis, 0));
        repeat(tennis, 0, 3);
        assertEquals("Andrew gewinnt das Spiel und führt 4 zu 3.", point(tennis, 0));
    }

    @Test public void tennisDoublesNamesBothPartnersAndHandlesPartialTeams() {
        language("de");
        MatchState tennis = new MatchState("doubles", 3, MatchFormat.defaults("tennis").mode(true));
        repeat(tennis, 1, 8);
        repeat(tennis, 0, 3);
        MatchState before = MatchState.decode(tennis.encode());
        assertEquals("Spiel für Andrew und Adrian. 2 zu 1 für Sam und Michaela.", point(tennis, 0));
        assertEquals("Spiel für Team 1. 2 zu 1 für Sam und Michaela.",
                SpokenScore.remote(ui, before, tennis, slot -> slot == 2 ? "" : PLAYERS[slot]));
    }

    @Test public void tennisMatchWinningGameAndDecidingMatchTieBreakUseTheRightUnits() {
        language("de");
        MatchState ordinary = new MatchState("match", 3, MatchFormat.defaults("tennis"));
        assertTrue(ordinary.enterCompletedSet(6, 3));
        assertTrue(ordinary.nextGame());
        repeat(ordinary, 0, 23);
        assertEquals("Spiel, Satz und Match für Andrew. 6 zu 0. 2 zu 0 in Sätzen.", point(ordinary, 0));
        MatchState decider = new MatchState("decider", 3, MatchFormat.defaults("tennis").finalTie(true));
        assertTrue(decider.enterCompletedSet(6, 4));
        assertTrue(decider.nextGame());
        assertTrue(decider.enterCompletedSet(4, 6));
        assertTrue(decider.nextGame());
        repeat(decider, 0, 8);
        repeat(decider, 1, 8);
        repeat(decider, 0, 1);
        assertEquals("Match für Andrew. 10 zu 8 im Match-Tie-Break. 2 zu 1 in Sätzen.", point(decider, 0));
    }

    @Test public void watchSpeaksForenamesWhileTheScorecardAndRemoteRoomRetainTheSavedNames() throws Exception {
        ShadowTextToSpeech.addLanguageAvailability(Locale.GERMAN);
        MatchState tennis = new MatchState("live-tennis", 3, MatchFormat.defaults("tennis"));
        repeat(tennis, 1, 12);
        repeat(tennis, 0, 7);
        OpponentDirectory directory = new OpponentDirectory();
        directory.assign(tennis.id(), directory.add("Susi Berger"));
        prefs.edit().putBoolean(ScoreSpeech.PREFERENCE, true).putString(WatchLanguage.PREFERENCE, "de")
                .putString("my_name", "Andrew Lindley").putString("match_book", new MatchBook(tennis).encode())
                .putString("opponent_book", directory.encode()).putString(MatchDisplayPolicy.ACTIVE_MATCH, tennis.id())
                .putBoolean("asked_match_notification", true).commit();
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            ShadowTextToSpeech speech = voice();
            initialized(speech);
            MainActivity activity = controller.get();
            activity.action(TouchTargets.BLUE);
            assertEquals("Spiel für Andrew. Susi führt 3 zu 2.", speech.getLastSpokenText());
            activity.action(TouchTargets.UNDO);
            assertEquals("Korrektur. 40 zu null.", speech.getLastSpokenText());
            org.json.JSONArray labels = activity.roomSnapshot().getJSONArray("labels");
            assertEquals("Andrew Lindley", labels.getString(0));
            assertEquals("Susi Berger", labels.getString(1));
            assertEquals(directory.encode(), prefs.getString("opponent_book", ""));
        } finally {
            controller.pause().stop().destroy();
        }
    }

    private ShadowTextToSpeech voice() {
        return shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance());
    }

    private void initialized(ShadowTextToSpeech voice) {
        voice.getOnInitListener().onInit(TextToSpeech.SUCCESS);
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void coldEngineKeepsOnlyLatestCallAndDropsCallbacksAfterClosing() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.ENGLISH);
        ScoreSpeech speaker = new ScoreSpeech(context, () -> fail("voice unavailable"));
        speaker.start(Locale.ENGLISH);
        ShadowTextToSpeech first = voice();
        speaker.say("1, 0.");
        speaker.say("2, 0.");
        initialized(first);
        assertEquals(Collections.singletonList("2, 0."), first.getSpokenTextList());
        speaker.say("3, 0.");
        assertEquals(TextToSpeech.QUEUE_FLUSH, first.getQueueMode());
        speaker.close();
        assertTrue(first.isShutdown());
        speaker.start(Locale.GERMAN);
        speaker.say("3 zu 1.");
        ShadowTextToSpeech second = voice();
        speaker.close();
        initialized(second);
        assertNull(second.getLastSpokenText());
    }

    @Test public void unavailableLocalVoiceReportsOnceAndNeverUsesANetworkVoice() {
        ShadowTextToSpeech.addVoice(new Voice("online", Locale.FRENCH, 300, 300, true, Collections.emptySet()));
        ShadowTextToSpeech.addVoice(new Voice("needs-data", Locale.FRENCH, 300, 300, false,
                Collections.singleton(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)));
        int[] reports = {0};
        ScoreSpeech speaker = new ScoreSpeech(context, () -> reports[0]++);
        speaker.start(Locale.FRENCH);
        ShadowTextToSpeech voice = voice();
        initialized(voice);
        speaker.say("1 à 0.");
        speaker.say("2 à 0.");
        assertNull(voice.getLastSpokenText());
        assertEquals(1, reports[0]);
        speaker.close();
    }

    @Test public void watchAnnouncesOnlyAcceptedTapsAndUndoIndependentlyOfVibration() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.GERMAN);
        prefs.edit().putBoolean(ScoreSpeech.PREFERENCE, true).putString(WatchLanguage.PREFERENCE, "de")
                .putString("haptic_mode", "off").putBoolean("asked_match_notification", true).commit();
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            ShadowTextToSpeech voice = voice();
            initialized(voice);
            assertNull(voice.getLastSpokenText());
            MainActivity a = controller.get();
            a.action(TouchTargets.UNDO);
            assertNull(voice.getLastSpokenText());
            a.action(TouchTargets.BLUE);
            assertEquals("1 zu 0.", voice.getLastSpokenText());
            a.action(TouchTargets.UNDO);
            assertEquals("Korrektur. 0 zu 0.", voice.getLastSpokenText());
            assertEquals(2, voice.getSpokenTextList().size());
        } finally {
            controller.pause().stop().destroy();
        }
    }
}
