package at.andrew.squashtap;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class OpponentDirectoryTest {
 @Test public void renameUpdatesLinkedPastAndCurrentMatchesWithoutChangingIds(){OpponentDirectory d=new OpponentDirectory();String id=d.add("Martin");d.assign("past",id);d.assign("current",id);d.rename(id,"Mártin");assertEquals("Mártin",d.nameFor("past"));assertEquals(id,d.opponentId("current"));}
 @Test public void removalKeepsHistoryAndHistoricalFilterIdentity(){OpponentDirectory d=new OpponentDirectory();String id=d.add("Alice");d.assign("past",id);d.archive(id);assertTrue(d.list(false).isEmpty());assertEquals("Alice",d.nameFor("past"));assertEquals(id,d.list(true).get(0).id);}
 @Test public void addingRemovedNameRestoresSameIdentity(){OpponentDirectory d=new OpponentDirectory();String id=d.add("Martin");d.archive(id);assertEquals(id,d.add(" martin "));assertFalse(d.get(id).archived);}
 @Test public void unicodeNamesNormalizeAndDuplicateRenameIsRejected(){OpponentDirectory d=new OpponentDirectory();String id=d.add("Andre\u0301");assertEquals("André",d.get(id).name);d.add("Susi");assertThrows(IllegalArgumentException.class,()->d.rename(id,"susi"));assertEquals("André",d.get(id).name);}
 @Test public void catalogReplacementIsAtomicAndCannotDropHistory(){OpponentDirectory d=new OpponentDirectory();String id=d.add("Martin");d.assign("past",id);String before=d.encode();assertThrows(IllegalArgumentException.class,()->d.replaceCatalog(Collections.emptyList()));assertEquals(before,d.encode());}
 @Test public void roundtripRetainsArchivedPeopleAssignmentsAndNoName(){OpponentDirectory d=new OpponentDirectory();String id=d.add("李 \"A,B\" 🏸");d.assign("past",id);d.archive(id);d.assign("unnamed","");OpponentDirectory r=OpponentDirectory.decode(d.encode());assertEquals(d.encode(),r.encode());assertEquals("",r.opponentId("unnamed"));assertTrue(r.get(id).archived);}
 @Test public void invalidNamesAndAssignmentsAreRejected(){OpponentDirectory d=new OpponentDirectory();for(String n:new String[]{" ","A\nB","A\u0000","\ud800",String.join("",Collections.nCopies(61,"🏸"))})assertThrows(IllegalArgumentException.class,()->d.add(n));assertThrows(IllegalArgumentException.class,()->d.assign("match","unknown"));}
 @Test public void absentOldMetadataIsEmptyButDamagedMetadataIsRejected(){assertTrue(OpponentDirectory.decode(null).list(true).isEmpty());assertThrows(IllegalArgumentException.class,()->OpponentDirectory.decode("O1:bad"));assertThrows(IllegalArgumentException.class,()->OpponentDirectory.decode("O2:AAA"));}
}
