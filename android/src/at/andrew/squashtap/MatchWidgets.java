package at.andrew.squashtap;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.wear.tiles.TileService;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;
import org.json.JSONObject;

/** Local, event-updated projections. The tile and complication never ask the network for a score. */
final class MatchWidgets {
    static final class Data {
        String sport="Keep Score",score="—",sets="Choose a sport",state="Ready",description="Open Keep Score",action="sports";
        boolean present;
    }

    static Data read(Context context) {
        return read(context,new RoomClient(context));
    }

    static Data read(Context context,RoomClient rooms) {
        WatchLanguage language=WatchLanguage.get(context);
        Data data=new Data();
        data.sets=language.text(data.sets);
        data.state=language.text(data.state);
        data.description=language.text(data.description);
        try {
            // current() only reads the encrypted device vault. It ignores closed rooms in the upload queue.
            RoomClient.Session room=rooms.current();
            boolean readOnly=room!=null&&!"score".equals(room.role);
            MatchState match;
            if(readOnly) {
                JSONObject saved=room.snapshot();
                match=MatchState.decode(saved.getString("match"));
                data.state=saved.getBoolean("final")?language.text("Finished"):match.complete()
                    ?language.pick("Awaiting confirmation","Bestätigung ausstehend","À confirmer","Por confirmar")
                    :"host".equals(room.role)
                    ?language.pick("Referee scoring","Schiedsrichter zählt","Arbitrage","Arbitraje")
                    :language.pick("Following","Zum Mitlesen","En lecture seule","Solo lectura");
            } else {
                SharedPreferences settings=context.getSharedPreferences("squash",Context.MODE_PRIVATE);
                String encoded=settings.getString("match_book",null);
                if(encoded==null)return data;
                MatchBook book=MatchBook.decode(encoded);
                match=book.current();
                if(match.complete()&&!book.currentDeleted()&&book.result(match.id())!=null
                        &&(!FinishedMatchReview.available(settings,book)||!match.id().equals(FinishedMatchReview.result(settings,book).id)
                        ||FinishedMatchReview.expired(settings,book,System.currentTimeMillis())))return data;
                if(!match.hasPoints()&&!match.complete()&&!match.id().equals(settings.getString("match_ready_id","")))
                    return data;
                boolean ongoing=MatchDisplayPolicy.ongoing(match,settings.getString(MatchDisplayPolicy.ACTIVE_MATCH,""),settings.getBoolean("match_paused",false));
                data.state=language.text(match.complete()?"Finished":ongoing?"Playing":match.hasPoints()?"Paused":"Ready");
            }
            int left=match.gamesWon(0),right=match.gamesWon(1);
            data.sport=language.text(match.format().name());
            data.score=match.complete()?left+"–"+right:
                language.text(match.game().displayPoint(0))+"–"+language.text(match.game().displayPoint(1));
            data.sets=language.text(match.format().nested()?"Sets ":"Games ")+left+"–"+right;
            data.present=true;
            data.action="match";
            data.description="Keep Score. "+(readOnly?language.text("Read-only score.")+" ":"")+data.sport+". "+
                data.score+". "+data.sets+". "+data.state+language.text(". Tap to open match.");
        } catch(Exception unavailable) {
            data.description=language.text("Open Keep Score to review saved data");
        }
        return data;
    }

    static PendingIntent tap(Context c){
        Intent intent=new Intent(c,MainActivity.class).setData(Uri.parse("keepscore://widget/match")).putExtra("keep_score_widget","match")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c,141,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    static void update(Context c){
        try{
            TileService.getUpdater(c).requestUpdate(KeepScoreTileService.class);
            ComplicationDataSourceUpdateRequester.create(c,new ComponentName(c,ScoreComplicationService.class)).requestUpdateAll();
        }catch(RuntimeException|LinkageError ignored){}
    }
}
