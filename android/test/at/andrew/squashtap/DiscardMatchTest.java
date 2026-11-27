package at.andrew.squashtap;

import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscardMatchTest {
    private static final LocalDate TODAY=LocalDate.of(2026,11,10);
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-11-10T18:00:00Z"),ZoneId.of("Europe/Vienna"));
    private MatchResult historical(){return new MatchResult("history",TODAY.toString(),1,3,new int[][]{{11,4},{11,8}});}
    @Test public void discardingAnySportClearsRalliesTimingAndServingWithoutChangingHistory(){
        for(String sport:MatchFormat.SPORTS){
            MatchFormat format=MatchFormat.defaults(sport);MatchBook book=new MatchBook(new MatchState("abandoned",3,format),CLOCK);book.importResult(historical());
            book.current().serving(0,format.doubles?1:-1);book.add(0);book.add(1);
            assertTrue(book.currentStartedAt()>0);String result=book.result("history").gamesText();
            book.discardCurrent("replacement");book=MatchBook.decode(book.encode(),CLOCK);
            assertEquals(sport,book.current().format().sport);assertEquals(3,book.current().bestOf());assertEquals("replacement",book.current().id());
            assertFalse(book.current().hasPoints());assertFalse(book.undo());assertEquals(-1,book.current().firstServer(0));assertEquals(0,book.currentStartedAt());assertEquals(0,book.currentFinishedAt());
            assertNull(book.result("abandoned"));assertEquals(1,book.count());assertEquals(result,book.result("history").gamesText());assertEquals(1,MatchStatistics.from(book.newestFirst()).matchesPlayed());
            if(format.pickleball())book.current().serving(0,1);book.add(0);assertTrue(book.currentStartedAt()>0);assertTrue(book.retire(1));assertEquals(2,book.result("replacement").number);
        }
    }
    @Test public void completedResultsRequireDeletionInsteadOfDiscard(){
        MatchBook book=new MatchBook(new MatchState("finished",3),CLOCK);book.retire(1);String before=book.encode();
        try{book.discardCurrent("replacement");fail();}catch(IllegalStateException expected){}
        assertEquals(before,book.encode());assertNotNull(book.result("finished"));
    }
    @Test public void cancellingAReopenedDeletedMatchDoesNotReuseItsDailyReference(){
        MatchBook book=new MatchBook(new MatchState("deleted",3),CLOCK);book.add(0);book.retire(1);book.delete("deleted");book.undo();assertTrue(book.currentDeleted());
        book.discardCurrent("fresh");assertFalse(book.currentDeleted());book=MatchBook.decode(book.encode(),CLOCK);book.retire(1);
        assertNull(book.result("deleted"));assertEquals(2,book.result("fresh").number);
    }
    @Test public void invalidReplacementIdentityLeavesTheMatchUntouched(){
        MatchBook book=new MatchBook(new MatchState("active",3),CLOCK);book.importResult(historical());book.add(1);String before=book.encode();
        for(String id:new String[]{"active","history","bad/id"}){try{book.discardCurrent(id);fail();}catch(IllegalArgumentException expected){}assertEquals(before,book.encode());}
    }
    @Test public void forgettingAnAbandonedTeamPreservesNamesAndHistoricalRoles(){
        OpponentDirectory names=new OpponentDirectory();String a=names.add("Alice"),b=names.add("Bob"),c=names.add("Chris");names.assignTeam("old",a,b,c);names.assignTeam("history",c,a,b);
        names.forgetMatch("old");names=OpponentDirectory.decode(names.encode());
        for(int role=0;role<3;role++)assertEquals("",names.roleId("old",role));
        assertEquals(c,names.roleId("history",0));assertEquals(a,names.roleId("history",1));assertEquals(b,names.roleId("history",2));assertEquals(3,names.list(false).size());
    }
}
