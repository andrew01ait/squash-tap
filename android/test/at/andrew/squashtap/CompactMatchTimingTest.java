package at.andrew.squashtap;

import java.time.Instant;
import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompactMatchTimingTest {
    private MatchResult result(String start,String end,String zone){return new MatchResult("timing","2026-11-07",1,3,new int[][]{{11,4},{11,6}},start==null?0:Instant.parse(start).toEpochMilli(),end==null?0:Instant.parse(end).toEpochMilli(),zone);}
    @Test public void sameDayShowsOneDateAndShortStartAndEndTimes(){
        MatchResult r=result("2026-11-07T18:30:00Z","2026-11-07T19:15:00Z","Europe/Vienna");assertEquals("Date: 07.11.2026\nStart: 19:30\nEnd: 20:15",r.timingText());assertEquals("45 min 0 sec",r.durationText());
    }
    @Test public void overnightMatchKeepsTheDifferentEndDate(){
        assertEquals("Date: 07.11.2026\nStart: 23:50\nEnd: 08.11.2026 · 00:20",result("2026-11-07T22:50:00Z","2026-11-07T23:20:00Z","Europe/Vienna").timingText());
    }
    @Test public void yearBoundaryDoesNotHideTheEndYear(){
        assertEquals("Date: 31.12.2026\nStart: 23:50\nEnd: 01.01.2027 · 00:10",result("2026-12-31T22:50:00Z","2026-12-31T23:10:00Z","Europe/Vienna").timingText());
    }
    @Test public void storedTimezoneControlsTheDateEvenAfterTravel(){
        TimeZone before=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));assertEquals("Date: 08.11.2026\nStart: 00:10\nEnd: 00:40",result("2026-11-07T23:10:00Z","2026-11-07T23:40:00Z","Europe/Vienna").timingText());}finally{TimeZone.setDefault(before);}
    }
    @Test public void legacyUnknownTimesRemainUnknownWithRecordedResultDate(){
        MatchResult r=result(null,null,"");assertEquals("Date: 07.11.2026\nStart: Not recorded\nEnd: Not recorded",r.timingText());assertEquals("Not recorded",r.durationText());
    }
    @Test public void missingStartDoesNotInventAStartFromTheFinish(){
        MatchResult r=result(null,"2026-11-07T19:15:00Z","Europe/Vienna");assertEquals("Date: 07.11.2026\nStart: Not recorded\nEnd: 20:15",r.timingText());assertEquals("Not recorded",r.durationText());
    }
    @Test public void clockReversalKeepsBothRecordedTimesAndUnavailableDuration(){
        MatchResult r=result("2026-11-07T19:15:00Z","2026-11-07T18:30:00Z","Europe/Vienna");assertEquals("Date: 07.11.2026\nStart: 20:15\nEnd: 19:30",r.timingText());assertEquals("Unavailable (clock changed)",r.durationText());
    }
}
