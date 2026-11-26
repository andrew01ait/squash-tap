package at.andrew.squashtap;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class OpponentDirectoryTest {
 @Test public void recentUsesMatchDayAndAllThreeDoublesRolesEvenAfterALateHistoricalImport(){
     OpponentDirectory d=new OpponentDirectory();
     String alice=d.add("Alice"),bea=d.add("Bea"),zoe=d.add("Zoë"),chris=d.add("Chris"),dan=d.add("Dan");
     MatchBook b=new MatchBook(new MatchState("playing",3));
     b.importResult(new MatchResult("padel","2026-11-24",1,3,new int[][]{{6,1},{6,2}},0,0,"",-1,MatchFormat.defaults("padel"),null));
     d.assignTeam("padel",zoe,bea,chris);
     b.importResult(new MatchResult("late","2026-10-01",1,3,new int[][]{{11,7},{11,8}}));
     d.assign("late",alice);d.assign("playing",alice);
     java.util.function.Function<Boolean,List<String>> ordered=active->d.playerList(false,"recent",Locale.GERMAN,b,active,null).stream().map(e->e.id).collect(java.util.stream.Collectors.toList());
     assertEquals(Arrays.asList(bea,chris,zoe,alice,dan),ordered.apply(false));
     assertEquals(Arrays.asList(alice,bea,chris,zoe,dan),ordered.apply(true));
     b.delete("padel");
     assertEquals(Arrays.asList(alice,bea,chris,dan,zoe),ordered.apply(false));
 }
 @Test public void thirtyDayFilterUsesThePlayedDayAcrossAllSportsAndDoublesRoles(){
     OpponentDirectory d=new OpponentDirectory();
     String amy=d.add("Amy"),bea=d.add("Bea"),cal=d.add("Cal"),dan=d.add("Dan"),eve=d.add("Eve"),newName=d.add("Unplayed");
     MatchBook b=new MatchBook(new MatchState("ongoing",3));d.assign("ongoing",newName);
     java.time.LocalDate today=java.time.LocalDate.parse("2026-11-26");
     b.importResult(new MatchResult("team","2026-10-28",1,3,new int[][]{{6,1},{6,2}},0,0,"",-1,MatchFormat.defaults("padel"),null));
     d.assignTeam("team",cal,bea,amy);
     for(int i=0;i<3;i++){
         String id="entry-"+i;
         b.importResult(new MatchResult(id,new String[]{"2026-11-26","2026-10-27","2026-11-27"}[i],1,3,new int[][]{{11,4},{11,5}}));
         d.assign(id,i==0?dan:eve);
     }
     java.util.function.Function<String,List<String>> filtered=order->d.playerList(false,order,Locale.ENGLISH,b,true,today).stream().map(e->e.id).collect(java.util.stream.Collectors.toList());
     assertEquals(Arrays.asList(amy,bea,cal,dan),filtered.apply("az"));
     assertEquals(Arrays.asList(dan,cal,bea,amy),filtered.apply("za"));
     assertEquals(Arrays.asList(dan,amy,bea,cal),filtered.apply("recent"));
     b.delete("team");assertEquals(Collections.singletonList(dan),filtered.apply("az"));
 }
 @Test public void localizedAlphabetReversesCleanlyAndArchivedPlayersRemainSearchableInHistory(){
     OpponentDirectory d=new OpponentDirectory();MatchBook b=new MatchBook(new MatchState("idle",3));
     String zoe=d.add("Zoë"),anne=d.add("Änne"),m=d.add("Müller");d.archive(m);
     assertEquals(anne,d.playerList(false,"az",Locale.GERMAN,b,false,null).get(0).id);
     assertEquals(zoe,d.playerList(false,"za",Locale.GERMAN,b,false,null).get(0).id);
     assertEquals(m,d.playerList(true,"za",Locale.GERMAN,b,false,null).get(1).id);
     assertEquals("gross muller",OpponentDirectory.searchName("  Groß Müller ",Locale.GERMAN));
 }
 @Test public void renameUpdatesLinkedPastAndCurrentMatchesWithoutChangingIds(){OpponentDirectory d=new OpponentDirectory();String id=d.add("Martin");d.assign("past",id);d.assign("current",id);d.rename(id,"Mártin");assertEquals("Mártin",d.nameFor("past"));assertEquals(id,d.opponentId("current"));}
 @Test public void removalKeepsHistoryAndHistoricalFilterIdentity(){OpponentDirectory d=new OpponentDirectory();String id=d.add("Alice");d.assign("past",id);d.archive(id);assertTrue(d.list(false).isEmpty());assertEquals("Alice",d.nameFor("past"));assertEquals(id,d.list(true).get(0).id);}
 @Test public void addingRemovedNameRestoresSameIdentity(){OpponentDirectory d=new OpponentDirectory();String id=d.add("Martin");d.archive(id);assertEquals(id,d.add(" martin "));assertFalse(d.get(id).archived);}
 @Test public void unicodeNamesNormalizeAndDuplicateRenameIsRejected(){OpponentDirectory d=new OpponentDirectory();String id=d.add("Andre\u0301");assertEquals("André",d.get(id).name);d.add("Susi");assertThrows(IllegalArgumentException.class,()->d.rename(id,"susi"));assertEquals("André",d.get(id).name);}
 @Test public void catalogReplacementIsAtomicAndCannotDropHistory(){OpponentDirectory d=new OpponentDirectory();String id=d.add("Martin");d.assign("past",id);String before=d.encode();assertThrows(IllegalArgumentException.class,()->d.replaceCatalog(Collections.emptyList()));assertEquals(before,d.encode());}
 @Test public void roundtripRetainsArchivedPeopleAssignmentsAndNoName(){OpponentDirectory d=new OpponentDirectory();String id=d.add("李 \"A,B\" 🏸");d.assign("past",id);d.archive(id);d.assign("unnamed","");OpponentDirectory r=OpponentDirectory.decode(d.encode());assertEquals(d.encode(),r.encode());assertEquals("",r.opponentId("unnamed"));assertTrue(r.get(id).archived);}
 @Test public void invalidNamesAndAssignmentsAreRejected(){OpponentDirectory d=new OpponentDirectory();for(String n:new String[]{" ","A\nB","A\u0000","\ud800",String.join("",Collections.nCopies(61,"🏸"))})assertThrows(IllegalArgumentException.class,()->d.add(n));assertThrows(IllegalArgumentException.class,()->d.assign("match","unknown"));}
 @Test public void absentOldMetadataIsEmptyButDamagedMetadataIsRejected(){assertTrue(OpponentDirectory.decode(null).list(true).isEmpty());assertThrows(IllegalArgumentException.class,()->OpponentDirectory.decode("O1:bad"));assertThrows(IllegalArgumentException.class,()->OpponentDirectory.decode("O2:AAA"));}
}
