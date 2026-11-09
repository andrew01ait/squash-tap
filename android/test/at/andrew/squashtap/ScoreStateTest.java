package at.andrew.squashtap;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreStateTest {
    private ScoreState deuce(){ScoreState s=new ScoreState();for(int i=0;i<10;i++){s.add(0);s.add(1);}return s;}
    @Test public void startsAtZero(){ScoreState s=new ScoreState();assertEquals(0,s.blue());assertEquals(0,s.red());assertEquals(-1,s.winner());}
    @Test public void blueTapOnlyChangesBlue(){ScoreState s=new ScoreState();assertTrue(s.add(0));assertEquals(1,s.blue());assertEquals(0,s.red());}
    @Test public void redTapOnlyChangesRed(){ScoreState s=new ScoreState();assertTrue(s.add(1));assertEquals(0,s.blue());assertEquals(1,s.red());}
    @Test public void undoRestoresLastPlayer(){ScoreState s=new ScoreState();s.add(0);s.add(1);s.add(0);s.undo();assertEquals(1,s.blue());assertEquals(1,s.red());s.undo();assertEquals(0,s.red());}
    @Test public void undoAtZeroDoesNotGoNegative(){ScoreState s=new ScoreState();assertFalse(s.undo());assertEquals(0,s.blue());assertEquals(0,s.red());}
    @Test public void tenDoesNotWin(){ScoreState s=new ScoreState();for(int i=0;i<10;i++)s.add(0);assertEquals(-1,s.winner());}
    @Test public void blueWinsElevenZero(){ScoreState s=new ScoreState();for(int i=0;i<11;i++)s.add(0);assertEquals(0,s.winner());}
    @Test public void redWinsElevenNine(){ScoreState s=new ScoreState();for(int i=0;i<9;i++){s.add(0);s.add(1);}s.add(1);s.add(1);assertEquals(1,s.winner());}
    @Test public void tenAllIsDeuce(){ScoreState s=deuce();assertTrue(s.deuce());assertEquals(-1,s.winner());}
    @Test public void elevenTenDoesNotWin(){ScoreState s=deuce();s.add(0);assertEquals(-1,s.winner());assertTrue(s.deuce());}
    @Test public void twelveTenWins(){ScoreState s=deuce();s.add(0);s.add(0);assertEquals(0,s.winner());}
    @Test public void extendedDeuceWorks(){ScoreState s=deuce();for(int i=0;i<50;i++){s.add(0);s.add(1);}s.add(1);s.add(1);assertEquals(60,s.blue());assertEquals(62,s.red());assertEquals(1,s.winner());}
    @Test public void tapsAfterWinAreIgnored(){ScoreState s=new ScoreState();for(int i=0;i<11;i++)s.add(0);assertFalse(s.add(1));assertFalse(s.add(0));assertEquals(11,s.blue());assertEquals(0,s.red());}
    @Test public void undoWinningPointReopensGame(){ScoreState s=new ScoreState();for(int i=0;i<11;i++)s.add(0);s.undo();assertEquals(-1,s.winner());assertTrue(s.add(1));}
    @Test public void resetClearsScoresAndHistory(){ScoreState s=deuce();s.reset();assertEquals("1|",s.encode());assertFalse(s.undo());}
    @Test public void saveRestoreKeepsUndoHistory(){ScoreState s=deuce();s.add(1);ScoreState restored=ScoreState.decode(s.encode());assertEquals(10,restored.blue());assertEquals(11,restored.red());assertTrue(restored.undo());assertEquals(10,restored.red());}
    @Test public void restoredWinRemainsClosed(){ScoreState s=deuce();s.add(0);s.add(0);assertEquals(0,ScoreState.decode(s.encode()).winner());}
    @Test(expected=IllegalArgumentException.class) public void rejectsBadPlayer(){new ScoreState().add(5);}
    @Test(expected=IllegalArgumentException.class) public void rejectsUnknownSchema(){ScoreState.decode("2|BR");}
    @Test(expected=IllegalArgumentException.class) public void rejectsCorruptedScore(){ScoreState.decode("1|BRX");}
    @Test(expected=IllegalArgumentException.class) public void rejectsPointsAfterSavedWin(){ScoreState.decode("1|BBBBBBBBBBBR");}
    @Test public void undoEverythingReturnsToZero(){ScoreState s=deuce();while(s.undo()){}assertEquals("1|",s.encode());}
    @Test public void randomValidGamesRoundTrip(){java.util.Random r=new java.util.Random(91);for(int game=0;game<1000;game++){ScoreState s=new ScoreState();for(int i=0;i<100&&s.winner()==-1;i++)s.add(r.nextInt(2));ScoreState t=ScoreState.decode(s.encode());assertEquals(s.blue(),t.blue());assertEquals(s.red(),t.red());assertEquals(s.winner(),t.winner());}}
}
