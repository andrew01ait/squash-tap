package at.andrew.squashtap;
import android.app.PendingIntent;
import android.content.*;
import android.net.Uri;
import androidx.wear.tiles.TileService;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;

/** Read-only, event-updated watch surfaces. Opening one never awards a point or resumes play. */
final class MatchWidgets {
    static final class Data {
        String sport="Keep Score",score="—",sets="Choose a sport",state="Ready",description="Open Keep Score",action="sports";
        boolean present;
    }
    static Data read(Context c){Data d=new Data();SharedPreferences p=c.getSharedPreferences("squash",Context.MODE_PRIVATE);try{
        String encoded=p.getString("match_book",null);if(encoded==null)return d;MatchBook b=MatchBook.decode(encoded);MatchState m=b.current();boolean present=m.hasPoints()||m.complete()||m.id().equals(p.getString("match_ready_id",""));if(!present)return d;d.present=true;d.sport=m.format().name();d.score=m.complete()?m.gamesWon(0)+"–"+m.gamesWon(1):m.game().displayPoint(0)+"–"+m.game().displayPoint(1);d.sets=(m.format().nested()?"Sets ":"Games ")+m.gamesWon(0)+"–"+m.gamesWon(1);d.state=m.complete()?"Finished":MatchDisplayPolicy.ongoing(m,p.getString(MatchDisplayPolicy.ACTIVE_MATCH,""),p.getBoolean("match_paused",false))?"Playing":m.hasPoints()?"Paused":"Ready";d.action="match";d.description="Keep Score. "+d.sport+". "+d.score+". "+d.sets+". "+d.state+". Tap to open match.";
    }catch(RuntimeException invalid){d.description="Open Keep Score to review saved data";}return d;}
    static PendingIntent tap(Context c){Intent intent=new Intent(c,MainActivity.class).setData(Uri.parse("keepscore://widget/match")).putExtra("keep_score_widget","match").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);return PendingIntent.getActivity(c,141,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void update(Context c){try{TileService.getUpdater(c).requestUpdate(KeepScoreTileService.class);ComplicationDataSourceUpdateRequester.create(c,new ComponentName(c,ScoreComplicationService.class)).requestUpdateAll();}catch(RuntimeException|LinkageError ignored){}}
}
