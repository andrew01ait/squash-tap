package at.andrew.squashtap;

import android.app.Service;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;

/** One quiet, user-initiated ongoing match; no sensors, polling or CPU wake lock. */
public final class MatchSessionService extends Service {
    private static final String CHANNEL="match_session";
    private static final int NOTIFICATION=41;
    private OngoingActivity ongoing;
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        SharedPreferences preferences=getSharedPreferences("squash",MODE_PRIVATE);
        try{
            MatchState match=MatchBook.decode(preferences.getString("match_book","")).current();
            if(!MatchDisplayPolicy.ongoing(match,preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""),preferences.getBoolean("match_paused",false))){finishSession();return START_NOT_STICKY;}
            NotificationManager manager=getSystemService(NotificationManager.class);
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Match in progress",NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null,null);channel.enableVibration(false);manager.createNotificationChannel(channel);
            PendingIntent touch=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            String score=(match.format().doubles?"Our team ":"Me ")+match.game().displayPoint(0)+" – "+match.game().displayPoint(1)+(match.format().doubles?" Opponents · ":" Opponent · ")+ (match.format().nested()?"Sets ":"Games ")+match.gamesWon(0)+"–"+match.gamesWon(1);
            if(ongoing==null){
                NotificationCompat.Builder notification=new NotificationCompat.Builder(this,CHANNEL)
                    .setSmallIcon(R.drawable.ic_match).setContentTitle(match.format().name()+" match in progress")
                    .setContentText(score).setContentIntent(touch).setCategory("workout")
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setSilent(true).setOngoing(true);
                ongoing=new OngoingActivity.Builder(this,NOTIFICATION,notification).setStaticIcon(R.drawable.ic_match)
                    .setTouchIntent(touch).setStatus(Status.forPart(new Status.TextPart(score))).build();
                ongoing.apply(this);startForeground(NOTIFICATION,notification.build());
                AppDiagnostics.record(this,"Ongoing match session started");
            }else ongoing.update(this,Status.forPart(new Status.TextPart(score)));
            return START_STICKY;
        }catch(Exception error){
            AppDiagnostics.record(this,"Ongoing session unavailable: "+error.getClass().getSimpleName()+" "+error.getMessage());
            finishSession();return START_NOT_STICKY;
        }
    }
    private void finishSession(){stopForeground(STOP_FOREGROUND_REMOVE);getSystemService(NotificationManager.class).cancel(NOTIFICATION);stopSelf();}
    @Override public void onTaskRemoved(Intent rootIntent){AppDiagnostics.record(this,"Task removed; pausing match; battery "+BatterySnapshot.describe(this));getSharedPreferences("squash",MODE_PRIVATE).edit().remove(MatchDisplayPolicy.ACTIVE_MATCH).putBoolean("match_paused",true).commit();LiveScoreClient.stopIfPresent();finishSession();super.onTaskRemoved(rootIntent);}
    @Override public void onDestroy(){AppDiagnostics.record(this,"Ongoing match session stopped");super.onDestroy();}
}
