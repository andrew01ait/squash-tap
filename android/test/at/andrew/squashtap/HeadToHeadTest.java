package at.andrew.squashtap;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class HeadToHeadTest {
    private MatchResult result(String id,String date,MatchFormat f,boolean win){MatchState m=new MatchState(id,3,f);int target=f.nested()?6:f.target;for(int i=0;i<2;i++)m.enterCompletedSet(win?target:2,win?2:target);return MatchResult.from(m,date,1);}
    @Test public void singlesUsesStableIdentitiesAndTheCurrentSportAndMode(){
        OpponentDirectory d=new OpponentDirectory();String p=d.add("Pat"),other=d.add("Other");MatchState current=new MatchState("current",3);d.assign(current.id(),p);
        List<MatchResult> results=Arrays.asList(result("win","2026-11-01",MatchFormat.squash(),true),result("loss","2026-11-02",MatchFormat.squash(),false),result("other","2026-11-03",MatchFormat.squash(),false),result("tennis","2026-11-04",MatchFormat.defaults("tennis"),false));
        for(MatchResult r:results)d.assign(r.id,r.id.equals("other")?other:p);d.rename(p,"Pat renamed");d.archive(p);String before=d.encode();HeadToHead h=HeadToHead.from(current,results,d);
        assertEquals(1,h.won);assertEquals(1,h.lost);assertEquals("loss",h.last.id);assertEquals(before,d.encode());
    }
    @Test public void doublesMatchesBothTeamsAndAcceptsSwappedOpposingPositions(){
        OpponentDirectory d=new OpponentDirectory();String a=d.add("Alex"),b=d.add("Bob"),p=d.add("Partner"),q=d.add("Other partner");MatchFormat f=MatchFormat.defaults("padel");MatchState current=new MatchState("current",3,f);d.assignTeam(current.id(),a,p,b);
        List<MatchResult> results=Arrays.asList(result("same","2026-11-01",f,true),result("swapped","2026-11-02",f,false),result("other","2026-11-03",f,false));d.assignTeam("same",a,p,b);d.assignTeam("swapped",b,p,a);d.assignTeam("other",a,q,b);
        HeadToHead h=HeadToHead.from(current,results,d);assertEquals(1,h.won);assertEquals(1,h.lost);assertEquals("swapped",h.last.id);
    }
    @Test public void unnamedPlayersAndIncompleteTeamsNeverGuessHistory(){
        OpponentDirectory d=new OpponentDirectory();List<MatchResult> results=Collections.singletonList(result("old","2026-11-01",MatchFormat.squash(),true));assertNull(HeadToHead.from(new MatchState("current",3),results,d));String a=d.add("Alex"),b=d.add("Bob");MatchState doubles=new MatchState("double",3,MatchFormat.defaults("padel"));d.assignTeam(doubles.id(),a,"",b);assertNull(HeadToHead.from(doubles,results,d));
    }
    @Test public void lastMeetingUsesRecordedDateRatherThanImportOrder(){
        OpponentDirectory d=new OpponentDirectory();String p=d.add("Pat");MatchState current=new MatchState("current",3);d.assign(current.id(),p);List<MatchResult> results=Arrays.asList(result("latest","2026-11-09",MatchFormat.squash(),true),result("backfill","2026-01-01",MatchFormat.squash(),false));for(MatchResult r:results)d.assign(r.id,p);assertEquals("latest",HeadToHead.from(current,results,d).last.id);
    }
    @Test public void currentCompletedResultIsExcludedAndDeletedHistoryDisappears(){
        MatchBook b=new MatchBook(new MatchState("current",3));OpponentDirectory d=new OpponentDirectory();String p=d.add("Pat");d.assign("current",p);b.importResult(result("old","2026-11-01",MatchFormat.squash(),true));d.assign("old",p);b.enterCompletedSet(11,2);b.enterCompletedSet(11,3);HeadToHead h=HeadToHead.from(b.current(),b.newestFirst(),d);assertEquals(1,h.won);assertEquals(0,h.lost);assertEquals("old",h.last.id);b.delete("old");assertNull(HeadToHead.from(b.current(),b.newestFirst(),d));
    }
    @Test public void retirementUsesTheMatchWinnerRatherThanTheLeadingSetScore(){
        OpponentDirectory d=new OpponentDirectory();String p=d.add("Pat");MatchState current=new MatchState("current",3),retired=new MatchState("retired",3);d.assign("current",p);d.assign("retired",p);retired.enterCompletedSet(11,2);retired.retire(0);HeadToHead h=HeadToHead.from(current,Collections.singletonList(MatchResult.from(retired,"2026-11-01",1)),d);assertEquals(0,h.won);assertEquals(1,h.lost);assertEquals(1,h.last.gamesWon(0));assertEquals(1,h.last.winner());
    }
}
