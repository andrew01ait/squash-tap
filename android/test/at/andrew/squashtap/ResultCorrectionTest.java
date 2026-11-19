package at.andrew.squashtap;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResultCorrectionTest {
    private static MatchResult score(String id, String date, int last) {
        return new MatchResult(id, date, 1, 3, new int[][]{{11, 5}, {11, last}});
    }

    @Test public void correctionMovesDayWithoutReusingEitherDaysReferences() {
        MatchBook book = new MatchBook(new MatchState("live", 3));
        book.add(0);
        String current = book.current().encode();
        book.importResult(score("old", "2026-01-01", 4));
        book.importResult(score("first", "2026-02-01", 6));
        book.correctResult(score("old", "2026-02-01", 9));
        book = MatchBook.decode(book.encode());
        assertEquals(2, book.count());
        assertEquals(2, book.result("old").number);
        assertEquals(9, book.result("old").points(1, 1));
        assertEquals(2, book.importResult(score("later", "2026-01-01", 3)).number);
        assertEquals(current, book.current().encode());
    }

    @Test public void correctingLatestRetainsRalliesInUnchangedSetsAndUsesEnteredTotalsForCorrectedSets() {
        MatchBook book = new MatchBook(new MatchState("recent", 3));
        for (int i=0; i<11; i++) book.add(0);
        book.nextGame();
        for (int i=0; i<11; i++) book.add(0);
        MatchResult old = book.result("recent");
        book.correctResult(new MatchResult(old.id, old.date, old.number, 3, new int[][]{{11,0},{11,9}},old.startedAt,old.finishedAt,old.zoneId));
        book = MatchBook.decode(book.encode());
        assertEquals("BBBBBBBBBBB",book.current().game(0).rallyHistory());
        assertTrue(book.current().game(1).entered());
        assertEquals(old.startedAt,book.result("recent").startedAt);
        assertTrue(book.undo());assertNull(book.result("recent"));
        assertTrue(book.enterCompletedSet(11,7));assertEquals(old.number,book.result("recent").number);
    }

    @Test public void correctedRetirementPersistsSeparatelyFromRecordedRalliesForUndo() {
        for (String sport : new String[]{"squash","tennis","pickleball"}) {
            MatchBook book = new MatchBook(new MatchState("recent",3,MatchFormat.defaults(sport)));
            if (sport.equals("pickleball")) book.current().serving(0,1);
            book.add(0);book.add(0);book.add(1);
            String recorded = book.current().game().encode();
            book.retire(1);
            MatchResult old = book.result("recent");
            MatchResult corrected = new MatchResult(old.id,old.date,old.number,3,new int[][]{{4,2}},old.startedAt,old.finishedAt,old.zoneId,0,old.format,null);
            book.correctResult(corrected);
            String wire=book.encode();
            book=MatchBook.decode(wire);
            assertTrue(book.current().encode().startsWith("M5|"));
            assertEquals(4,book.current().game().blue());assertEquals(2,book.current().game().red());
            assertEquals(-1,book.current().game().winner());assertEquals(1,book.result("recent").winner());
            assertEquals(0,book.result("recent").gamesWon(0));
            assertTrue(book.undo());assertEquals(recorded,book.current().game().encode());
            assertNull(book.result("recent"));assertFalse(book.current().complete());
        }
        assertThrows(IllegalArgumentException.class,()->MatchState.decode("M5|5|2|M1|m|3|1|B"));
        assertThrows(IllegalArgumentException.class,()->MatchState.decode("M5|11|2|M2|m|3|0|1|B"));
    }

    @Test public void ordinaryCsvStillSkipsConflictsAndExplicitCorrectionsRejectStaleOrDeletedResults() {
        MatchBook book=new MatchBook(new MatchState("live",3));
        MatchResult old=book.importResult(score("id","2026-03-01",5));
        OpponentDirectory names=new OpponentDirectory();
        java.util.List<MatchCsv.Row> before=MatchCsv.parse(MatchCsv.export(Collections.singletonList(old),names));
        java.util.List<MatchCsv.Row> after=MatchCsv.parse(MatchCsv.export(Collections.singletonList(score("id",old.date,8)),names));
        MatchCsv.Plan plan=MatchCsv.plan(book,names,after);
        assertEquals(1,plan.conflicts);assertEquals(5,plan.book.result("id").points(1,1));
        MatchCsv.correct(plan,book,names,before,after);
        assertEquals(1,plan.book.count());assertEquals(8,plan.book.result("id").points(1,1));
        book.correctResult(score("id",old.date,3));
        assertThrows(IllegalArgumentException.class,()->MatchCsv.correct(plan,book,names,before,after));
        book.delete("id");
        assertThrows(IllegalArgumentException.class,()->MatchCsv.correct(plan,book,names,before,after));
        assertEquals(0,book.count());
    }
}
