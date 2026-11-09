package at.andrew.squashtap;
import org.junit.Test;
import static org.junit.Assert.*;
public class MatchStateTest {
    private void win(MatchState match,int player){for(int i=0;i<11;i++)assertTrue(match.add(player));}
    @Test public void bestOfThreeNeedsTwoSets(){MatchState m=new MatchState("one",3);assertEquals(2,m.needed());win(m,0);assertFalse(m.complete());assertTrue(m.nextGame());win(m,0);assertTrue(m.complete());assertEquals(0,m.winner());}
    @Test public void bestOfFiveNeedsThreeSets(){MatchState m=new MatchState("one",5);win(m,1);m.nextGame();win(m,1);assertFalse(m.complete());m.nextGame();win(m,1);assertTrue(m.complete());assertEquals(1,m.winner());}
    @Test public void splitBestOfThreePlaysDecider(){MatchState m=new MatchState("one",3);win(m,0);m.nextGame();win(m,1);assertEquals(1,m.gamesWon(0));assertEquals(1,m.gamesWon(1));m.nextGame();win(m,1);assertEquals(3,m.gameCount());assertEquals(1,m.winner());}
    @Test public void allFiveSetsCanBePlayed(){MatchState m=new MatchState("five",5);for(int player:new int[]{0,1,0,1,0}){win(m,player);if(!m.complete())m.nextGame();}assertEquals(5,m.gameCount());assertEquals(3,m.gamesWon(0));assertEquals(2,m.gamesWon(1));}
    @Test public void noSetAdvanceDuringPlay(){assertFalse(new MatchState("one",3).nextGame());}
    @Test public void wonSetWaitsForNext(){MatchState m=new MatchState("one",3);win(m,0);assertTrue(m.nextReady());assertFalse(m.add(1));assertEquals(11,m.game().blue());}
    @Test public void nextSetResetsOnlyCurrentPoints(){MatchState m=new MatchState("one",3);win(m,0);m.nextGame();assertEquals(0,m.game().blue());assertEquals(11,m.game(0).blue());assertEquals(1,m.gamesWon(0));}
    @Test public void matchEndBlocksFurtherTapsAndSets(){MatchState m=new MatchState("one",3);win(m,0);m.nextGame();win(m,0);assertFalse(m.add(1));assertFalse(m.nextGame());}
    @Test public void undoFinalPointReopensMatch(){MatchState m=new MatchState("one",3);win(m,0);m.nextGame();win(m,0);assertTrue(m.undo());assertFalse(m.complete());assertEquals(10,m.game().blue());assertEquals(1,m.gamesWon(0));}
    @Test public void undoCrossesEmptySetBoundary(){MatchState m=new MatchState("one",3);win(m,0);m.nextGame();m.undo();assertEquals(1,m.gameCount());assertEquals(10,m.game().blue());assertEquals(0,m.gamesWon(0));}
    @Test public void undoCurrentSetKeepsPreviousSet(){MatchState m=new MatchState("one",3);win(m,0);m.nextGame();m.add(1);m.undo();assertEquals(2,m.gameCount());assertEquals(1,m.gamesWon(0));assertEquals(0,m.game().red());}
    @Test public void saveRestoreRetainsAllSetsAndUndo(){MatchState m=new MatchState("one",5);win(m,0);m.nextGame();win(m,1);m.nextGame();m.add(1);MatchState t=MatchState.decode(m.encode());assertEquals(m.encode(),t.encode());t.undo();assertEquals(0,t.game().red());assertEquals(1,t.gamesWon(1));}
    @Test public void legacyPointsMigrateToFirstSet(){MatchState m=MatchState.fromLegacy("old","1|BBR");assertEquals(3,m.bestOf());assertEquals(2,m.game().blue());assertEquals(1,m.game().red());m.undo();assertEquals(0,m.game().red());}
    @Test public void legacyWonGameIsOneSetNotWholeMatch(){MatchState m=MatchState.fromLegacy("old","1|BBBBBBBBBBB");assertEquals(1,m.gamesWon(0));assertFalse(m.complete());assertTrue(m.nextReady());}
    @Test(expected=IllegalArgumentException.class)public void rejectsUnsupportedFormat(){new MatchState("one",4);}
    @Test(expected=IllegalArgumentException.class)public void rejectsInvalidId(){new MatchState("bad|id",3);}
    @Test(expected=IllegalArgumentException.class)public void rejectsNextSetBeforePreviousWon(){MatchState.decode("M1|one|3|1|B~1|");}
    @Test(expected=IllegalArgumentException.class)public void rejectsGamesAfterMatchWon(){MatchState.decode("M1|one|3|1|BBBBBBBBBBB~1|BBBBBBBBBBB~1|");}
    @Test public void deuceRulesStillApplyWithinMatch(){MatchState m=new MatchState("one",3);for(int i=0;i<10;i++){m.add(0);m.add(1);}m.add(0);assertFalse(m.nextReady());m.add(0);assertTrue(m.nextReady());assertEquals(12,m.game().blue());}
    @Test public void formatChangeKeepsCurrentPointsAndUndo(){MatchState m=MatchState.fromLegacy("old","1|BBR");assertTrue(m.changeBestOf(5));assertEquals("old",m.id());assertEquals(2,m.game().blue());assertEquals(1,m.game().red());m=MatchState.decode(m.encode());assertEquals(5,m.bestOf());assertTrue(m.undo());assertEquals(0,m.game().red());}
    @Test public void extendingMatchKeepsWonSetsAndNextState(){MatchState m=new MatchState("one",3);win(m,0);assertTrue(m.changeBestOf(5));assertEquals(1,m.gamesWon(0));assertTrue(m.nextReady());m.nextGame();win(m,0);assertFalse(m.complete());m.nextGame();win(m,0);assertTrue(m.complete());}
    @Test public void shorteningDuringDeciderRequiresOnlySecondWin(){MatchState m=new MatchState("one",5);win(m,0);m.nextGame();win(m,1);m.nextGame();m.add(0);assertTrue(m.changeBestOf(3));assertEquals(3,m.gameCount());for(int i=0;i<10;i++)m.add(0);assertTrue(m.complete());assertEquals(2,m.gamesWon(0));assertEquals(m.encode(),MatchState.decode(m.encode()).encode());}
    @Test public void cannotShortenAfterPlayerHasTwoSetWins(){MatchState m=new MatchState("one",5);win(m,1);m.nextGame();win(m,1);m.nextGame();m.add(0);String before=m.encode();assertFalse(m.canChangeBestOf(3));assertFalse(m.changeBestOf(3));assertEquals(before,m.encode());}
    @Test public void completedMatchKeepsItsFormat(){MatchState m=new MatchState("one",3);win(m,0);m.nextGame();win(m,0);assertFalse(m.canChangeBestOf(5));assertFalse(m.changeBestOf(5));assertEquals(3,m.bestOf());assertTrue(m.complete());}
    @Test public void selectingSameFormatDoesNotChangeState(){MatchState m=new MatchState("one",3);m.add(1);String before=m.encode();assertFalse(m.changeBestOf(3));assertEquals(before,m.encode());}
    @Test(expected=IllegalArgumentException.class)public void changingToUnsupportedFormatIsRejected(){new MatchState("one",3).changeBestOf(4);}
}
