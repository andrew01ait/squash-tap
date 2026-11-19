package at.andrew.squashtap;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, application=Application.class, qualifiers="en")
public class SharedWidgetsUiTest {
    private static final class Store implements RoomClient.Vault {
        private String data="{}";
        public JSONObject read() throws Exception {return new JSONObject(data);}
        public void write(JSONObject value){data=value.toString();}
    }

    private final Store vault=new Store();
    private final RoomClient rooms=new RoomClient(vault,(method,path,token,body)->{
        throw new AssertionError("The score widget must never use HTTP");
    });
    private Context context;
    private SharedPreferences prefs;

    @Before public void prepare(){
        context=RuntimeEnvironment.getApplication();
        MatchBook local=new MatchBook(new MatchState("local",3));
        local.add(0);
        prefs=context.getSharedPreferences("squash",Context.MODE_PRIVATE);
        prefs.edit().clear().putString("match_book",local.encode()).commit();
    }

    private JSONObject score(MatchState match,boolean confirmed) throws Exception {
        return new JSONObject().put("schema",1).put("match",match.encode()).put("final",confirmed)
            .put("labels",new JSONArray().put("First").put("Second").put("").put(""))
            .put("startedAt",1L).put("finishedAt",confirmed?2L:0L).put("zone","UTC");
    }

    @Test public void hostAndFollowerReadTheLastDurableRemoteRevisionUntilLeaving() throws Exception {
        final String local=prefs.getString("match_book","");
        MatchState away=new MatchState("external",3,MatchFormat.defaults("table-tennis"));
        for(int i=0;i<6;i++)away.add(1);
        RoomClient.Session ready=rooms.prepare(score(away,false),true,true);
        MatchWidgets.Data host=MatchWidgets.read(context,rooms);
        assertEquals("Table tennis",host.sport);
        assertEquals("0–6",host.score);
        assertEquals("Referee scoring",host.state);
        assertEquals("match",host.action);

        JSONObject disk=vault.read(),room=disk.getJSONObject(ready.id);
        room.put("role","follow").put("created",true).put("seen",1L).put("local",1L);
        room.remove("writer");room.remove("admin");
        vault.write(disk);
        MatchWidgets.Data following=MatchWidgets.read(context,rooms);
        assertEquals("0–6",following.score);
        assertEquals("Following",following.state);
        assertTrue(following.description.contains("Read-only score."));

        while(!away.nextReady())away.add(1);
        away.nextGame();
        while(!away.complete())away.add(1);
        room.put("snapshot",score(away,false).toString()).put("seen",2L);
        vault.write(disk);
        assertEquals("Awaiting confirmation",MatchWidgets.read(context,rooms).state);

        room.put("snapshot",score(away,true).toString()).put("seen",3L);
        vault.write(disk);
        MatchWidgets.Data finished=MatchWidgets.read(context,rooms);
        assertEquals("Finished",finished.state);
        assertEquals("0–2",finished.score);
        assertEquals(local,prefs.getString("match_book",""));
        rooms.leave();
        assertEquals("1–0",MatchWidgets.read(context,rooms).score);
    }

    @Test public void locallyScoredRoomUsesTheLocalScoreDuringAnOfflineUpload() throws Exception {
        MatchState oldSnapshot=new MatchState("previous",3);
        rooms.prepare(score(oldSnapshot,false),false,true);
        MatchWidgets.Data widget=MatchWidgets.read(context,rooms);
        assertEquals("1–0",widget.score);
        assertFalse(widget.description.contains("Read-only"));
    }
}
