package at.andrew.squashtap;
import org.junit.Test;import static org.junit.Assert.*;import java.time.*;import java.util.*;
public class ResultFilterTest {
 private MatchResult result(String id,String date,int number){return new MatchResult(id,date,number,3,new int[][]{{11,4},{11,5}});}
 @Test public void opponentAndInclusiveDateRangeCombine(){MatchBook b=new MatchBook(new MatchState("current",3));b.importResult(result("a","2026-11-01",1));b.importResult(result("b","2026-11-02",1));b.importResult(result("c","2026-11-03",1));OpponentDirectory d=new OpponentDirectory();String id=d.add("Martin");d.assign("a",id);d.assign("b",id);List<MatchResult> out=ResultFilter.select(b,d,id,LocalDate.parse("2026-11-01"),LocalDate.parse("2026-11-02"));assertEquals(2,out.size());assertEquals("b",out.get(0).id);}
 @Test public void removedPeopleRemainFilterableAndUnnamedIsSeparate(){MatchBook b=new MatchBook(new MatchState("current",3));b.importResult(result("a","2026-11-01",1));b.importResult(result("b","2026-11-02",1));OpponentDirectory d=new OpponentDirectory();String id=d.add("Martin");d.assign("a",id);d.archive(id);assertEquals("a",ResultFilter.select(b,d,id,null,null).get(0).id);assertEquals("b",ResultFilter.select(b,d,"",null,null).get(0).id);assertEquals(2,ResultFilter.select(b,d,null,null,null).size());}
 @Test public void filteringUsesRecordedStartZoneAcrossMidnight(){long time=Instant.parse("2026-11-03T23:30:00Z").toEpochMilli();MatchResult r=new MatchResult("a","2026-11-04",1,3,new int[][]{{11,4},{11,5}},time,time+1000,"Europe/Vienna");assertEquals(LocalDate.parse("2026-11-04"),ResultFilter.date(r));}
 @Test public void legacyFallsBackToRecordedCompletionDate(){assertEquals(LocalDate.parse("2026-11-03"),ResultFilter.date(result("a","2026-11-03",1)));}
 @Test public void reversedRangeIsRejected(){assertThrows(IllegalArgumentException.class,()->ResultFilter.select(new MatchBook(new MatchState("current",3)),new OpponentDirectory(),null,LocalDate.parse("2026-11-04"),LocalDate.parse("2026-11-03")));}
 @Test public void eventsAreDistinctAndAlphabeticalWithNewestMatchesWithinEachLabel(){
    MatchBook book=new MatchBook(new MatchState("current",3));
    book.importResult(result("a","2026-11-01",1));
    book.importResult(result("b","2026-11-02",1));
    book.importResult(result("c","2026-11-03",1));
    book.importResult(result("d","2026-11-04",1));
    book.setNotes("a",new MatchNotes("","Landesliga"));
    book.setNotes("b",new MatchNotes("","Alpine Cup"));
    book.setNotes("c",new MatchNotes("","Landesliga"));
    book.setNotes("current",new MatchNotes("","Austrian Open"));
    List<MatchResult> results=ResultFilter.select(book,new OpponentDirectory(),null,null,null);
    assertEquals(Arrays.asList("Alpine Cup","Landesliga"),ResultFilter.eventLabels(book,results,false,Locale.GERMAN));
    assertEquals(Arrays.asList("Alpine Cup","Austrian Open","Landesliga"),ResultFilter.eventLabels(book,results,true,Locale.GERMAN));
    ResultFilter.organizeByEvent(results,book,null,true,Locale.GERMAN);
    assertArrayEquals(new String[]{"b","c","a","d"},results.stream().map(r->r.id).toArray(String[]::new));
    ResultFilter.organizeByEvent(results,book,"Landesliga",false,Locale.GERMAN);
    assertArrayEquals(new String[]{"c","a"},results.stream().map(r->r.id).toArray(String[]::new));
 }
}
