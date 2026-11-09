package at.andrew.squashtap;

import java.time.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatchTimingTest {
    private static final ZoneId VIENNA=ZoneId.of("Europe/Vienna");
    private static final Instant BEGIN=Instant.parse("2026-11-03T09:00:00Z");
    private static final class TestClock extends Clock {
        Instant now=BEGIN;ZoneId zone=VIENNA;
        @Override public ZoneId getZone(){return zone;}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
        @Override public Instant instant(){return now;}
        void advance(long seconds){now=now.plusSeconds(seconds);}
    }
    private MatchBook book(TestClock clock){return new MatchBook(new MatchState("timed",3),clock);}
    private void winSet(MatchBook book,int player){for(int i=0;i<11;i++)assertTrue(book.add(player));}
    private void finish(MatchBook book){while(!book.current().complete()){while(book.current().game().winner()==-1)assertTrue(book.add(0));if(!book.current().complete())assertTrue(book.nextGame());}}
    private MatchBook legacy(String name,TestClock clock)throws Exception{return MatchBook.decode(new String(Files.readAllBytes(Paths.get("android/test-fixtures/book-v1-"+name+".txt")),StandardCharsets.UTF_8).trim(),clock);}
    private String corruptHeaderTime(MatchBook book,boolean start)throws Exception{
        byte[] bytes=java.util.Base64.getDecoder().decode(book.encode().substring(3));
        java.io.DataInputStream in=new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes));in.readUTF();in.readUTF();in.readInt();in.readBoolean();
        int offset=bytes.length-in.available()+(start?0:8);java.nio.ByteBuffer.wrap(bytes).putLong(offset,BEGIN.toEpochMilli()+1);
        return "B4:"+java.util.Base64.getEncoder().encodeToString(bytes);
    }

    @Test public void setupTimeIsExcludedUntilFirstPoint(){TestClock clock=new TestClock();MatchBook b=book(clock);assertEquals(0,b.currentStartedAt());clock.advance(300);b.add(1);assertEquals(BEGIN.plusSeconds(300).toEpochMilli(),b.currentStartedAt());assertEquals(0,b.currentFinishedAt());}
    @Test public void subsequentPointsDoNotRestartTimer(){TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);clock.advance(90);b.add(1);assertEquals(BEGIN.toEpochMilli(),b.currentStartedAt());}
    @Test public void finalPointRecordsFinishAndDuration(){TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);clock.advance(1497);finish(b);MatchResult r=b.result("timed");assertEquals(BEGIN.toEpochMilli(),r.startedAt);assertEquals(BEGIN.plusSeconds(1497).toEpochMilli(),r.finishedAt);assertEquals(1497000,r.durationMillis());assertEquals("24 min 57 sec",r.durationText());}
    @Test public void interSetBreakCountsTowardDuration(){TestClock clock=new TestClock();MatchBook b=book(clock);winSet(b,0);clock.advance(120);b.nextGame();clock.advance(180);winSet(b,0);assertEquals(300000,b.result("timed").durationMillis());}
    @Test public void completedDurationDoesNotKeepGrowing(){TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);clock.advance(20);finish(b);clock.advance(300);b.saveCompleted(LocalDate.of(2026,11,3));assertEquals(20000,b.result("timed").durationMillis());}
    @Test public void rejectedTapAfterCompletionCannotChangeFinish(){TestClock clock=new TestClock();MatchBook b=book(clock);finish(b);long ended=b.currentFinishedAt();clock.advance(300);assertFalse(b.add(1));assertEquals(ended,b.currentFinishedAt());}
    @Test public void activeTimingSurvivesRestart(){TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);clock.advance(30);b=MatchBook.decode(b.encode(),clock);assertEquals(BEGIN.toEpochMilli(),b.currentStartedAt());clock.advance(600);finish(b);assertEquals(630000,b.result("timed").durationMillis());}
    @Test public void resultTimingRoundTripsExactly(){TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);clock.advance(100);finish(b);String encoded=b.encode();assertTrue(encoded.startsWith("B4:"));b=MatchBook.decode(encoded,clock);assertEquals(encoded,b.encode());assertEquals(100000,b.result("timed").durationMillis());assertEquals("Europe/Vienna",b.result("timed").zoneId);}
    @Test public void undoWinningPointClearsFinishAndPreservesStart(){TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);clock.advance(60);finish(b);assertTrue(b.undo());assertEquals(0,b.currentFinishedAt());assertEquals(BEGIN.toEpochMilli(),b.currentStartedAt());assertEquals(0,b.count());clock.advance(30);b.add(0);assertEquals(90000,b.result("timed").durationMillis());assertEquals(1,b.result("timed").number);}
    @Test public void undoAllPointsDoesNotResetKnownStart(){TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);b.undo();clock.advance(60);b.add(1);assertEquals(BEGIN.toEpochMilli(),b.currentStartedAt());}
    @Test public void newMatchGetsItsOwnStart(){TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);finish(b);b.newMatch("next",5);assertEquals(0,b.currentStartedAt());assertEquals(0,b.currentFinishedAt());clock.advance(90);b.add(1);assertEquals(BEGIN.plusSeconds(90).toEpochMilli(),b.currentStartedAt());assertEquals(BEGIN.toEpochMilli(),b.result("timed").startedAt);}
    @Test public void formatChangeKeepsStart(){TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);clock.advance(30);assertTrue(b.changeBestOf(5));finish(b);assertEquals(BEGIN.toEpochMilli(),b.result("timed").startedAt);assertEquals(30000,b.result("timed").durationMillis());}
    @Test public void deletingThenCorrectingCannotReviveTimedResult(){TestClock clock=new TestClock();MatchBook b=book(clock);finish(b);b.delete("timed");b.undo();clock.advance(30);b.add(0);b=MatchBook.decode(b.encode(),clock);assertEquals(0,b.count());assertTrue(b.currentDeleted());assertEquals(BEGIN.plusSeconds(30).toEpochMilli(),b.currentFinishedAt());}
    @Test public void startTitleAndLocalTimesUseCapturedTimezone(){TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);clock.advance(1500);finish(b);MatchResult r=b.result("timed");assertEquals("10:00",r.heading());assertEquals("03.11.2026 · 10:00",r.title());assertTrue(r.startedText().startsWith("03.11.2026 10:00:00"));assertTrue(r.finishedText().startsWith("03.11.2026 10:25:00"));assertEquals("Match 1 · 03.11.2026",r.referenceText());}
    @Test public void crossingMidnightKeepsStartDateAndCompletionDayNumber(){TestClock clock=new TestClock();clock.now=Instant.parse("2026-11-03T22:55:00Z");MatchBook b=book(clock);b.add(0);clock.advance(900);finish(b);MatchResult r=b.result("timed");assertEquals("03.11.2026 · 23:55",r.title());assertEquals("2026-11-04",r.date);assertTrue(r.finishedText().startsWith("04.11.2026 00:10:00"));assertEquals(900000,r.durationMillis());}
    @Test public void daylightSavingChangeDoesNotDistortDuration(){TestClock clock=new TestClock();clock.now=Instant.parse("2026-10-25T00:55:00Z");MatchBook b=book(clock);b.add(0);clock.advance(600);finish(b);MatchResult r=b.result("timed");assertTrue(r.startedText().contains("02:55:00"));assertTrue(r.finishedText().contains("02:05:00"));assertEquals(600000,r.durationMillis());}
    @Test public void backwardsClockHasHonestUnavailableDuration(){TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);clock.advance(-60);finish(b);MatchResult r=b.result("timed");assertEquals(-1,r.durationMillis());assertEquals("Unavailable (clock changed)",r.durationText());assertEquals(b.encode(),MatchBook.decode(b.encode(),clock).encode());}
    @Test public void timezoneChangeDoesNotReinterpretRecordedLocalTimes(){TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);clock.zone=ZoneId.of("Asia/Tokyo");clock.advance(90);finish(b);MatchResult r=b.result("timed");assertEquals("Europe/Vienna",r.zoneId);assertEquals("10:00",r.heading());assertTrue(r.finishedText().contains("10:01:30"));assertEquals(90000,r.durationMillis());}
    @Test public void emptyLegacyMatchCanStartTimingWithItsFirstPoint()throws Exception{TestClock clock=new TestClock();MatchBook b=legacy("empty",clock);assertEquals(0,b.currentStartedAt());b.add(0);assertEquals(BEGIN.toEpochMilli(),b.currentStartedAt());assertEquals(1,b.count());}
    @Test public void legacyActiveMatchNeverInventsItsStart()throws Exception{TestClock clock=new TestClock();MatchBook b=legacy("active",clock);assertEquals(2,b.current().game().blue());assertEquals(1,b.current().game().red());clock.advance(600);finish(b);MatchResult r=b.result("legacy-active");assertEquals(0,r.startedAt);assertEquals(BEGIN.plusSeconds(600).toEpochMilli(),r.finishedAt);assertEquals("Not recorded",r.durationText());assertEquals(2,r.number);}
    @Test public void legacyCompletedResultAndUndoArePreserved()throws Exception{TestClock clock=new TestClock();MatchBook b=legacy("completed",clock);MatchResult r=b.result("legacy-complete");assertEquals(1,b.count());assertEquals("Match result",r.heading());assertEquals("03.11.2026 · Start not recorded",r.title());assertEquals("Not recorded",r.startedText());assertEquals("Not recorded",r.finishedText());assertEquals("Not recorded",r.durationText());assertEquals(11,b.current().game().blue());b.undo();clock.advance(30);b.add(0);assertEquals(1,b.result("legacy-complete").number);assertEquals(0,b.result("legacy-complete").startedAt);}
    @Test public void legacyMigrationRetainsPastResultsCountersAndRallies()throws Exception{TestClock clock=new TestClock();MatchBook b=legacy("active",clock);String upgraded=b.encode();assertTrue(upgraded.startsWith("B4:"));b=MatchBook.decode(upgraded,clock);assertEquals(1,b.count());assertEquals(0,b.result("historic").startedAt);b.undo();assertEquals(0,b.current().game().red());finish(b);assertEquals(2,b.result("legacy-active").number);}
    @Test public void legacyDeletionMarkerSurvivesMigration()throws Exception{TestClock clock=new TestClock();MatchBook b=legacy("deleted",clock);b=MatchBook.decode(b.encode(),clock);assertTrue(b.currentDeleted());assertEquals(0,b.count());b.undo();b.add(0);assertEquals(0,b.count());}
    @Test public void durationFormatsSecondsMinutesAndHours(){int[][] points={{11,0},{11,0}};long begin=BEGIN.toEpochMilli();assertEquals("7 sec",new MatchResult("a","2026-11-03",1,3,points,begin,begin+7000,"UTC").durationText());assertEquals("2 min 5 sec",new MatchResult("a","2026-11-03",1,3,points,begin,begin+125000,"UTC").durationText());assertEquals("1 h 2 min 5 sec",new MatchResult("a","2026-11-03",1,3,points,begin,begin+3725000,"UTC").durationText());}
    @Test(expected=IllegalArgumentException.class) public void negativeStoredTimestampIsRejected(){new MatchResult("a","2026-11-03",1,3,new int[][]{{11,0},{11,0}},-1,10,"UTC");}
    @Test(expected=IllegalArgumentException.class) public void invalidStoredTimezoneIsRejected(){new MatchResult("a","2026-11-03",1,3,new int[][]{{11,0},{11,0}},BEGIN.toEpochMilli(),BEGIN.toEpochMilli(),"Invalid/Zone");}
    @Test(expected=IllegalArgumentException.class) public void unfinishedMatchCannotClaimAFinishTime()throws Exception{TestClock clock=new TestClock();MatchBook b=book(clock);b.add(0);MatchBook.decode(corruptHeaderTime(b,false),clock);}
    @Test(expected=IllegalArgumentException.class) public void resultAndCurrentTimingMustAgree()throws Exception{TestClock clock=new TestClock();MatchBook b=book(clock);finish(b);MatchBook.decode(corruptHeaderTime(b,true),clock);}
}
