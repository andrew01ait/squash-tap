package at.andrew.squashtap;
import androidx.wear.watchface.complications.data.*;
import androidx.wear.watchface.complications.datasource.*;

public final class ScoreComplicationService extends ComplicationDataSourceService {
    private PlainComplicationText text(String s){return new PlainComplicationText.Builder(s).build();}
    private ComplicationData data(ComplicationType type,MatchWidgets.Data d){
        if(type==ComplicationType.LONG_TEXT)return new LongTextComplicationData.Builder(text(d.present?d.sport+" · "+d.score:"Open Keep Score"),text(d.description)).setTitle(text(d.present?d.state:"Choose sport")).setTapAction(MatchWidgets.tap(this)).build();
        if(type==ComplicationType.SHORT_TEXT)return new ShortTextComplicationData.Builder(text(d.present?d.score.length()<=7?d.score:"…":"Open"),text(d.description)).setTitle(text(d.present?d.sport.substring(0,Math.min(7,d.sport.length())):"Score")).setTapAction(MatchWidgets.tap(this)).build();
        return null;
    }
    @Override public void onComplicationRequest(ComplicationRequest request,ComplicationRequestListener listener){try{listener.onComplicationData(data(request.getComplicationType(),MatchWidgets.read(this)));}catch(android.os.RemoteException disconnected){/* The watch-face host has gone away. Scoring is unaffected. */}}
    @Override public ComplicationData getPreviewData(ComplicationType type){MatchWidgets.Data d=new MatchWidgets.Data();d.present=true;d.sport="Squash";d.score="9–10";d.state="Playing";d.description="Keep Score. Squash. 9 to 10.";return data(type,d);}
}
