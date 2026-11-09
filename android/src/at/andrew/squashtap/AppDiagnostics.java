package at.andrew.squashtap;

import android.app.Application;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Private, bounded lifecycle/crash evidence. No telemetry or background upload. */
public final class AppDiagnostics extends Application {
    @Override public void onCreate(){
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{
                StringWriter stack=new StringWriter();error.printStackTrace(new PrintWriter(stack));
                String value=java.time.LocalDateTime.now()+" v"+BuildConfig.VERSION_NAME+" "+thread.getName()+"\n"+stack;
                getSharedPreferences("squash",MODE_PRIVATE).edit().putString("last_crash",value.substring(0,Math.min(value.length(),6000))).commit();
            }catch(Throwable ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);
            else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
        });
        record(this,"Process started v"+BuildConfig.VERSION_NAME+" pid="+android.os.Process.myPid());
    }
    public static synchronized void record(Context context,String event){
        try{
            SharedPreferences prefs=context.getSharedPreferences("squash",Context.MODE_PRIVATE);
            String trace=prefs.getString("lifecycle_trace","")+LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))+" "+event+"\n";
            trace=tailLines(trace,6000);
            prefs.edit().putString("lifecycle_trace",trace).apply();
        }catch(Exception ignored){}
    }
    public static String tailLines(String text,int limit){
        if(text.length()<=limit)return text;
        int start=text.length()-limit,newline=text.indexOf('\n',start);
        return text.substring(newline>=0?newline+1:start);
    }
    public static String recentExits(Context context){
        if(Build.VERSION.SDK_INT<30)return "Unavailable on this OS";
        try{
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            StringBuilder out=new StringBuilder();
            for(ApplicationExitInfo exit:manager.getHistoricalProcessExitReasons(context.getPackageName(),0,3)){
                String reason;
                switch(exit.getReason()){
                    case ApplicationExitInfo.REASON_CRASH:reason="Crash";break;
                    case ApplicationExitInfo.REASON_ANR:reason="Not responding";break;
                    case ApplicationExitInfo.REASON_LOW_MEMORY:reason="Low memory";break;
                    case ApplicationExitInfo.REASON_USER_REQUESTED:reason="Forced/user-requested stop";break;
                    case ApplicationExitInfo.REASON_PACKAGE_UPDATED:reason="App updated";break;
                    case ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE:reason="App/component state changed";break;
                    case ApplicationExitInfo.REASON_CRASH_NATIVE:reason="Native crash";break;
                    default:reason="System reason "+exit.getReason();
                }
                out.append(java.time.Instant.ofEpochMilli(exit.getTimestamp()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
                    .append(" · ").append(reason).append(" · ").append(exit.getDescription()).append('\n');
            }
            return out.length()==0?"No recorded process exits":out.toString();
        }catch(Exception error){return "Exit reasons unavailable: "+error.getClass().getSimpleName();}
    }
}
