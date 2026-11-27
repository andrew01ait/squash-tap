package at.andrew.squashtap;
import static org.junit.Assert.*;
import org.junit.Test;

public class SharedResultTest {
    private final String[] labels={"Alex","Lea","Val","Sam"};
    private MatchState played(String id){
        MatchState m=new MatchState(id,3,MatchFormat.defaults("padel"));
        m.enterCompletedSet(6,4);m.nextGame();m.add(0);m.add(0);m.add(1);m.retire(1);return m;
    }
    @Test public void doublesSeatThreeHasOwnTeamPointsRolesAndRetirement(){
        MatchBook local=new MatchBook(new MatchState("local",3));local.add(0);
        OpponentDirectory dir=new OpponentDirectory();MatchState remote=played("remote");
        MatchResult r=SharedResult.save(local,dir,remote,labels,1000,60000,"UTC",3,false);
        assertEquals(1,r.winner());assertEquals(0,r.retiredPlayer);assertEquals(4,r.points(0,0));assertEquals(1,r.unfinishedPoints(1,0));
        assertEquals("Alex",dir.roleName(r.id,0));assertEquals("Lea",dir.roleName(r.id,1));assertEquals("Val",dir.roleName(r.id,2));
        assertEquals(1,local.current().game().blue());assertEquals("local",local.current().id());
        assertEquals(1,SharedResult.save(local,dir,remote,labels,1000,60000,"UTC",3,false).number);
        assertEquals(1,MatchBook.decode(local.encode()).count());
    }
    @Test public void hostSavesExplicitlyKeepsIdentitiesAndLeavesANewLocalScoreboard(){
        MatchBook local=new MatchBook(new MatchState("host",3,MatchFormat.defaults("padel")));
        OpponentDirectory dir=new OpponentDirectory();String id=dir.importOpponent("","Lea");dir.assign("host",id);
        MatchState finalMatch=played("host");assertEquals(0,local.count());
        MatchResult saved=SharedResult.save(local,dir,finalMatch,labels,1000,60000,"UTC",0,true);
        assertEquals("host",saved.id);assertEquals(id,dir.roleId("host",0));
        assertNotEquals(saved.id,local.current().id());assertFalse(local.undo());assertEquals(1,MatchBook.decode(local.encode()).count());
    }
    @Test public void identicalNamesDoNotBlockSavingOrMergeDifferentPeople(){
        MatchBook b=new MatchBook(new MatchState("local",3));OpponentDirectory dir=new OpponentDirectory();
        SharedResult.save(b,dir,played("shared"),new String[]{"Alex","Lee","Lee","Sam"},1000,2000,"UTC",0,false);
        assertEquals(1,b.count());assertEquals("",dir.roleId("shared",0));assertEquals("",dir.roleId("shared",1));
        assertEquals("Sam",dir.roleName("shared",2));
    }
    @Test public void unfinishedMatchCannotEnterHistory(){MatchBook b=new MatchBook(new MatchState("local",3));
        try{SharedResult.save(b,new OpponentDirectory(),new MatchState("r",3),labels,0,0,"UTC",0,false);fail();}catch(IllegalArgumentException expected){}
        assertEquals(0,b.count());}
}
