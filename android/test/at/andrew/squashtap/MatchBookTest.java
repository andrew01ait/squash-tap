package at.andrew.squashtap;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDate;
public class MatchBookTest {
    private static final LocalDate DAY=LocalDate.of(2026,11,3);
    private MatchBook book(){return new MatchBook(new MatchState("first",3),java.time.Clock.fixed(java.time.Instant.parse("2026-11-03T09:00:00Z"),java.time.ZoneId.of("Europe/Vienna")));}
    private void finish(MatchBook b,int player,LocalDate day){while(!b.current().complete()){for(int i=0;i<11;i++)b.add(player,day);if(!b.current().complete())b.nextGame();}}
    @Test public void unfinishedMatchIsNotArchived(){MatchBook b=book();b.add(0,DAY);assertEquals(0,b.count());}
    @Test public void completedMatchIsSavedAutomatically(){MatchBook b=book();finish(b,0,DAY);assertEquals(1,b.count());assertEquals("Me 2–0 Opponent",b.result("first").summary());}
    @Test public void completionDateAndDailyNumberAreSaved(){MatchBook b=book();finish(b,0,DAY);MatchResult r=b.result("first");assertEquals("2026-11-03",r.date);assertEquals(1,r.number);assertEquals("03.11.2026 · 10:00",r.title());}
    @Test public void everySetScoreIsRetained(){MatchBook b=book();finish(b,1,DAY);MatchResult r=b.result("first");assertEquals(2,r.gameCount());assertEquals(0,r.points(0,0));assertEquals(11,r.points(1,1));assertEquals(1,r.winner());}
    @Test public void secondMatchGetsNextNumber(){MatchBook b=book();finish(b,0,DAY);b.newMatch("second",3);finish(b,1,DAY);assertEquals(2,b.result("second").number);}
    @Test public void nextDayRestartsAtOne(){MatchBook b=book();finish(b,0,DAY);b.newMatch("tomorrow",3);finish(b,0,DAY.plusDays(1));assertEquals(1,b.result("tomorrow").number);assertEquals("2026-11-04",b.result("tomorrow").date);}
    @Test public void newestResultsAreFirst(){MatchBook b=book();finish(b,0,DAY);b.newMatch("second",3);finish(b,1,DAY);assertEquals("second",b.newestFirst().get(0).id);}
    @Test public void deletingResultDoesNotRenumberOrReuse(){MatchBook b=book();finish(b,0,DAY);b.newMatch("second",3);finish(b,0,DAY);b.delete("second");b.newMatch("third",3);finish(b,0,DAY);assertEquals(1,b.result("first").number);assertEquals(3,b.result("third").number);}
    @Test public void deleteOldResultKeepsActivePoints(){MatchBook b=book();finish(b,0,DAY);b.newMatch("second",3);b.add(1,DAY);b.delete("first");assertEquals(1,b.current().game().red());assertEquals(0,b.count());}
    @Test public void repeatedSaveDoesNotDuplicateResult(){MatchBook b=book();finish(b,0,DAY);b.saveCompleted(DAY);b.saveCompleted(DAY.plusDays(1));assertEquals(1,b.count());assertEquals(1,b.result("first").number);assertEquals(DAY.toString(),b.result("first").date);}
    @Test public void undoCompletedMatchRemovesOldResult(){MatchBook b=book();finish(b,0,DAY);b.undo();assertEquals(0,b.count());assertFalse(b.current().complete());}
    @Test public void correctingCompletionReusesSameReference(){MatchBook b=book();finish(b,0,DAY);b.undo();b.add(0,DAY);assertEquals(1,b.count());assertEquals(1,b.result("first").number);}
    @Test public void correctedMatchKeepsOriginalDateAcrossMidnight(){MatchBook b=book();finish(b,0,DAY);b.undo();b.add(0,DAY.plusDays(1));assertEquals(DAY.toString(),b.result("first").date);}
    @Test public void deletingCurrentResultDoesNotClearScore(){MatchBook b=book();finish(b,0,DAY);b.delete("first");assertEquals(11,b.current().game().blue());assertTrue(b.current().complete());assertTrue(b.currentDeleted());}
    @Test public void deletedCurrentResultIsNotRecreated(){MatchBook b=book();finish(b,0,DAY);b.delete("first");assertFalse(b.saveCompleted(DAY));b=MatchBook.decode(b.encode());assertFalse(b.saveCompleted(DAY));assertEquals(0,b.count());}
    @Test public void deletedResultDoesNotReturnAfterUndoAndRewin(){MatchBook b=book();finish(b,0,DAY);b.delete("first");b.undo();b.add(0,DAY);assertTrue(b.current().complete());assertEquals(0,b.count());}
    @Test public void deleteAllKeepsScoreAndNumbers(){MatchBook b=book();finish(b,0,DAY);b.deleteAll();assertEquals(0,b.count());assertTrue(b.current().complete());b.newMatch("second",3);finish(b,1,DAY);assertEquals(2,b.result("second").number);}
    @Test public void newMatchAfterDeletingCanBeArchivedNormally(){MatchBook b=book();finish(b,0,DAY);b.delete("first");b.newMatch("second",5);finish(b,1,DAY);assertEquals(3,b.result("second").gamesWon(1));assertEquals(2,b.result("second").number);}
    @Test public void fullStoreRoundTripKeepsCompletedAndActiveData(){MatchBook b=book();finish(b,0,DAY);b.newMatch("second",5);b.add(1,DAY);MatchBook restored=MatchBook.decode(b.encode());assertEquals(1,restored.count());assertEquals(1,restored.current().game().red());assertEquals(5,restored.current().bestOf());assertEquals(b.encode(),restored.encode());}
    @Test public void completedCurrentResultRoundTrips(){MatchBook b=book();finish(b,0,DAY);MatchBook restored=MatchBook.decode(b.encode());assertEquals(1,restored.count());assertEquals(1,restored.result("first").number);assertTrue(restored.current().complete());}
    @Test public void migrationPreservesLegacyScoreWithoutInventingResults(){MatchBook b=MatchBook.migrateLegacy("legacy","1|BBR");assertEquals(2,b.current().game().blue());assertEquals(1,b.current().game().red());assertEquals(0,b.count());assertEquals(b.encode(),MatchBook.decode(b.encode()).encode());}
    @Test public void storageFullKeepsCompletedScoreUntilSpaceFreed(){MatchBook b=book();for(int i=0;i<MatchBook.MAX_RESULTS;i++){if(i>0)b.newMatch("m"+i,3);finish(b,0,DAY);}b.newMatch("waiting",3);finish(b,1,DAY);assertEquals(MatchBook.MAX_RESULTS,b.count());assertNull(b.result("waiting"));assertTrue(b.current().complete());b.delete("first");assertTrue(b.saveCompleted(DAY));assertEquals(MatchBook.MAX_RESULTS+1,b.result("waiting").number);}
    @Test public void deletingUnknownIdIsNoOp(){assertFalse(book().delete("missing"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsCorruptStorage(){MatchBook.decode("B1:garbage!");}
    @Test(expected=IllegalArgumentException.class)public void rejectsUnknownSchema(){MatchBook.decode("B9:whatever");}
    @Test(expected=IllegalArgumentException.class)public void rejectsDuplicateCurrentId(){MatchBook b=book();b.newMatch("first",3);}
    @Test(expected=IllegalArgumentException.class)public void unfinishedResultIsRejected(){MatchResult.from(book().current(),DAY.toString(),1);}
    @Test(expected=IllegalArgumentException.class)public void impossibleSetScoreIsRejected(){new MatchResult("r",DAY.toString(),1,3,new int[][]{{20,1},{11,0}});}
    @Test(expected=IllegalArgumentException.class)public void overflowingSetScoreIsRejected(){new MatchResult("r",DAY.toString(),1,3,new int[][]{{Integer.MAX_VALUE,Integer.MAX_VALUE-2},{11,0}});}
    @Test public void resultDoesNotExposeMutableScoreArrays(){int[][] points={{11,5},{11,8}};MatchResult r=new MatchResult("r",DAY.toString(),1,3,points);points[0][0]=0;assertEquals(11,r.points(0,0));}
    @Test public void formatChangeRoundTripsWithoutChangingPastResults(){MatchBook b=book();finish(b,0,DAY);b.newMatch("second",3);b.add(0,DAY);b.add(1,DAY);assertTrue(b.changeBestOf(5));b=MatchBook.decode(b.encode());assertEquals("second",b.current().id());assertEquals(5,b.current().bestOf());assertEquals(1,b.current().game().blue());assertEquals(1,b.current().game().red());assertEquals(3,b.result("first").bestOf);assertEquals(1,b.result("first").number);assertTrue(b.undo());assertEquals(0,b.current().game().red());}
    @Test public void extendingSavesOnlyAfterThirdWin(){MatchBook b=book();for(int i=0;i<11;i++)b.add(0,DAY);assertTrue(b.changeBestOf(5));b.nextGame();for(int i=0;i<11;i++)b.add(0,DAY);assertEquals(0,b.count());assertFalse(b.current().complete());b.nextGame();for(int i=0;i<11;i++)b.add(0,DAY);assertEquals(1,b.count());assertEquals(5,b.result("first").bestOf);assertEquals("Me 3–0 Opponent",b.result("first").summary());}
    @Test public void shorteningSavesAfterSecondWin(){MatchBook b=new MatchBook(new MatchState("short",5));for(int i=0;i<11;i++)b.add(1,DAY);b.nextGame();b.add(1,DAY);assertTrue(b.changeBestOf(3));for(int i=0;i<10;i++)b.add(1,DAY);assertEquals(3,b.result("short").bestOf);assertEquals("Me 0–2 Opponent",b.result("short").summary());}
    @Test public void finishedResultCannotBeChangedThroughFormatSelector(){MatchBook b=book();finish(b,0,DAY);String before=b.encode();assertFalse(b.changeBestOf(5));assertEquals(before,b.encode());}
    @Test public void deletedResultStaysDeletedAfterFormatCorrection(){MatchBook b=book();finish(b,0,DAY);b.delete("first");b.undo();assertTrue(b.changeBestOf(5));b=MatchBook.decode(b.encode());b.add(0,DAY);b.nextGame();for(int i=0;i<11;i++)b.add(0,DAY);assertTrue(b.current().complete());assertEquals(0,b.count());assertTrue(b.currentDeleted());}
    @Test public void correctionWithFormatChangeKeepsOriginalReference(){MatchBook b=book();finish(b,0,DAY);b.undo();assertTrue(b.changeBestOf(5));b.add(0,DAY.plusDays(1));b.nextGame();for(int i=0;i<11;i++)b.add(0,DAY.plusDays(1));assertEquals(1,b.result("first").number);assertEquals(DAY.toString(),b.result("first").date);assertEquals(5,b.result("first").bestOf);}
    @Test public void notesStayWithTheMatchThroughCompletionUndoAndNewMatch(){
        MatchBook b=book();
        assertTrue(b.encode().startsWith("B4:"));
        MatchNotes note=new MatchNotes("Served short\r\n📝 Watch positioning","Landesliga");
        b.setNotes("first",note);
        finish(b,0,DAY);
        assertTrue(b.encode().startsWith("B6:"));
        MatchBook restored=MatchBook.decode(b.encode());
        assertEquals(note,restored.notes("first"));
        assertTrue(restored.undo());
        assertNull(restored.result("first"));
        assertEquals(note,restored.notes("first"));
        assertTrue(restored.add(0,DAY));
        assertEquals(1,restored.result("first").number);
        restored.newMatch("second",3,MatchFormat.defaults("tennis"));
        restored.setNotes("second",new MatchNotes("", "Autumn cup"));
        MatchBook multi=MatchBook.decode(restored.encode());
        assertEquals("tennis",multi.current().format().sport);
        assertEquals(note,multi.notes("first"));
        assertEquals("Autumn cup",multi.notes("second").event);
        multi.discardCurrent("replacement");
        assertTrue(multi.notes("second").isEmpty());
        assertTrue(multi.delete("first"));
        assertTrue(multi.notes("first").isEmpty());
        assertTrue(multi.encode().startsWith("B5:"));
    }
    @Test public void annotationsRejectInvalidTextAndCannotReviveDeletedResults(){
        MatchBook b=book();
        assertEquals("A\nB",new MatchNotes(" A\r\nB ","").text);
        assertThrows(IllegalArgumentException.class,()->new MatchNotes("","A\nB"));
        assertThrows(IllegalArgumentException.class,()->new MatchNotes("","A\u0000B"));
        assertThrows(IllegalArgumentException.class,()->new MatchNotes(String.join("",java.util.Collections.nCopies(601,"x")),""));
        assertThrows(IllegalArgumentException.class,()->b.setNotes("missing",new MatchNotes("text","")));
        finish(b,0,DAY);
        b.setNotes("first",new MatchNotes("","Test league"));
        b.deleteAll();
        assertTrue(b.notes("first").isEmpty());
        assertThrows(IllegalArgumentException.class,()->b.setNotes("first",new MatchNotes("text","")));
        assertTrue(b.encode().startsWith("B4:"));
    }
    @Test public void legacyTeamDataIsDroppedWhenLoadingOrRewritingB6()throws Exception{
        MatchBook original=book();
        byte[] base=java.util.Base64.getDecoder().decode(original.encode().substring(3));
        for(MatchNotes expected:new MatchNotes[]{MatchNotes.EMPTY,new MatchNotes("Serve short","Landesliga")}){
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            try(java.io.DataOutputStream out=new java.io.DataOutputStream(bytes)){
                out.writeBoolean(false);
                out.write(base);
                out.writeInt(1);
                out.writeUTF("first");
                out.writeUTF(expected.text);
                out.writeUTF("Legacy club");
                out.writeUTF(expected.event);
            }
            MatchBook decoded=MatchBook.decode("B6:"+java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
            assertEquals(expected,decoded.notes("first"));
            assertEquals(original.current().encode(),decoded.current().encode());
            assertEquals(expected,MatchBook.decode(decoded.encode()).notes("first"));
            String payload=new String(java.util.Base64.getDecoder().decode(decoded.encode().substring(3)),
                java.nio.charset.StandardCharsets.ISO_8859_1);
            assertFalse(payload.contains("Legacy club"));
            if(expected.isEmpty())assertEquals(original.encode(),decoded.encode());
        }
    }
}
