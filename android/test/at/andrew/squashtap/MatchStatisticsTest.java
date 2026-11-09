package at.andrew.squashtap;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatchStatisticsTest {
    private MatchResult won(){return new MatchResult("won","2026-11-07",1,3,new int[][]{{11,7},{9,11},{12,10}});}
    private MatchResult lostFive(){return new MatchResult("lost-five","2026-11-07",2,5,new int[][]{{11,3},{5,11},{11,8},{10,12},{8,11}});}
    private MatchResult lostThree(){return new MatchResult("lost-three","2026-11-06",1,3,new int[][]{{0,11},{2,11}});}
    private MatchBook book(){return new MatchBook(new MatchState("active",3));}
    private void finish(MatchBook book){while(!book.current().complete()){for(int i=0;i<11;i++)book.add(0,LocalDate.of(2026,11,7));if(!book.current().complete())book.nextGame();}}
    @Test public void emptyHistoryHasZeroTotalsWithoutAnInvalidPercentage(){
        MatchStatistics s=MatchStatistics.from(Collections.emptyList());assertEquals(0,s.matchesPlayed());assertEquals(0,s.setsPlayed());assertEquals(0,s.pointsPlayed());assertEquals(0,s.matchBalance());assertEquals(0,s.matchWinRate(),0);
    }
    @Test public void mixedFormatsCountEverySetAndRallyFromMePerspective(){
        MatchStatistics s=MatchStatistics.from(Arrays.asList(won(),lostFive(),lostThree()));
        assertEquals(3,s.matchesPlayed());assertEquals(1,s.matchesWon);assertEquals(2,s.matchesLost);assertEquals(-1,s.matchBalance());assertEquals(100.0/3,s.matchWinRate(),0.000001);
        assertEquals(10,s.setsPlayed());assertEquals(4,s.setsWon);assertEquals(6,s.setsLost);assertEquals(-2,s.setBalance());
        assertEquals(79,s.pointsFor);assertEquals(95,s.pointsAgainst);assertEquals(174,s.pointsPlayed());assertEquals(-16,s.pointBalance());
    }
    @Test public void legacyResultsWithoutTimesStillCount(){
        MatchStatistics s=MatchStatistics.from(Collections.singletonList(won()));assertEquals(1,s.matchesWon);assertEquals(100,s.matchWinRate(),0);assertEquals(2,s.setsWon);assertEquals(32,s.pointsFor);
    }
    @Test public void unfinishedMatchNeverCountsAsACompletedMatchOrPartialSet(){
        MatchBook b=book();b.importResult(won());for(int i=0;i<11;i++)b.add(1);b.nextGame();b.add(0);
        MatchStatistics s=MatchStatistics.from(b.newestFirst());assertEquals(1,s.matchesPlayed());assertEquals(3,s.setsPlayed());assertEquals(60,s.pointsPlayed());
    }
    @Test public void deletingAndClearingResultsImmediatelyChangesDerivedTotals(){
        MatchBook b=book();b.importResult(won());b.importResult(lostFive());assertEquals(2,MatchStatistics.from(b.newestFirst()).matchesPlayed());b.delete("won");
        MatchStatistics s=MatchStatistics.from(b.newestFirst());assertEquals(0,s.matchesWon);assertEquals(1,s.matchesLost);assertEquals(5,s.setsPlayed());b.deleteAll();assertEquals(0,MatchStatistics.from(b.newestFirst()).pointsPlayed());
    }
    @Test public void undoAndRecompletionDoNotDoubleCountTheCurrentMatch(){
        MatchBook b=book();finish(b);assertEquals(1,MatchStatistics.from(b.newestFirst()).matchesWon);b.undo();assertEquals(0,MatchStatistics.from(b.newestFirst()).matchesPlayed());b.add(0);assertEquals(1,MatchStatistics.from(b.newestFirst()).matchesWon);assertEquals(22,MatchStatistics.from(b.newestFirst()).pointsFor);
        b.delete(b.current().id());b.undo();b.add(0);assertEquals(0,MatchStatistics.from(b.newestFirst()).matchesPlayed());
    }
    @Test public void importAndPersistenceNeedNoNewSchemaOrStoredCounters(){
        MatchBook b=book();b.importResult(won());b.importResult(lostFive());String before=b.encode();MatchStatistics s=MatchStatistics.from(MatchBook.decode(before).newestFirst());assertEquals(2,s.matchesPlayed());assertEquals(77,s.pointsFor);assertEquals(73,s.pointsAgainst);assertEquals(before,b.encode());
    }
}
