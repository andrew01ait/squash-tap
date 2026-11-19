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
    static final int MAX_TRACE_EVENTS = 12;
    private static final int MAX_EVENT_LENGTH = 160;
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

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
            String line = LocalTime.now().format(EVENT_TIME) + " " + event.replace('\n', ' ').replace('\r', ' ');
            String previous = prefs.getString("lifecycle_trace", "");
            if (!previous.isEmpty() && !previous.endsWith("\n")) previous += "\n";
            String trace = recentTrace(previous + line);
            prefs.edit().putString("lifecycle_trace", trace).apply();
        }catch(Exception ignored){}
    }

    static String recentTrace(String trace) {
        String[] lines = trace.split("\n");
        StringBuilder recent = new StringBuilder();
        for (int i = Math.max(0, lines.length - MAX_TRACE_EVENTS); i < lines.length; i++) {
            if (!lines[i].isEmpty()) recent.append(DiagnosticTransfer.excerpt(lines[i], MAX_EVENT_LENGTH)).append('\n');
        }
        return recent.toString();
    }

    static String recentExits(Context context, boolean compact) {
        if(Build.VERSION.SDK_INT<30)return compact ? "" : "Unavailable on this OS";
        try{
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            StringBuilder out=new StringBuilder();
            for(ApplicationExitInfo exit:manager.getHistoricalProcessExitReasons(context.getPackageName(),0,compact ? 1 : 3)){
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
                java.time.LocalDateTime time = java.time.Instant.ofEpochMilli(exit.getTimestamp()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                if (compact) return time.withNano(0) + " · " + reason;
                out.append(time)
                    .append(" · ").append(reason).append(" · ").append(exit.getDescription()).append('\n');
            }
            return out.length()==0 && !compact ? "No recorded process exits" : out.toString();
        }catch(Exception error){return compact ? "" : "Exit reasons unavailable: "+error.getClass().getSimpleName();}
    }
}
