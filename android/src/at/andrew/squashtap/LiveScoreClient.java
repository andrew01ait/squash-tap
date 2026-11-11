package at.andrew.squashtap;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

/** Explicit live sharing: coalesced score events and reconnect retries, never a scoring dependency. */
final class LiveScoreClient {
    interface Listener{void status(String value);}
    interface Transport{JSONObject send(String method,String path,String token,JSONObject body)throws Exception;}
    private static LiveScoreClient instance;
    static synchronized LiveScoreClient get(Context context){if(instance==null)instance=new LiveScoreClient(context.getApplicationContext());return instance;}
    static void stopIfPresent(){if(instance!=null)instance.stop(!instance.finished());}
    static final class Session {
        final String id=UUID.randomUUID().toString().replace("-",""),key=TransferCrypto.random(32),owner=TransferCrypto.random(32),viewer=TransferCrypto.random(32),matchId;
        volatile long expiresAt;long completedAt;int revision=1;volatile boolean ready,discarded;boolean discardSending,completeSending;String last="",pending;boolean draining;
        Session(String matchId){this.matchId=matchId;}
        String link(){return BuildConfig.SITE_URL+"/live.html#id="+id+"&p="+viewer+"&k="+key;}
    }
    private final ExecutorService network;
    private final Transport transport;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ConnectivityManager connectivity;private ConnectivityManager.NetworkCallback reconnect;
    private final java.util.LinkedHashMap<String,Session> discarded=new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<String,Session> completed=new java.util.LinkedHashMap<>();
    private Session session;private Listener listener;private String status="Not sharing";
    private LiveScoreClient(Context context){this(context,LiveScoreClient::request,Executors.newSingleThreadExecutor());}
    LiveScoreClient(Context context,Transport transport,ExecutorService network){this.transport=transport;this.network=network;connectivity=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);}
    synchronized void listener(Listener next){listener=next;}
    synchronized String status(){return status;}
    synchronized Session session(){return session;}
    private synchronized boolean finished(){return session!=null&&session.last.contains("\"phase\":\"finished\"");}
    private String snapshot(Session s,JSONObject score,boolean initial){
        try{JSONObject copy=new JSONObject(score.toString());if("finished".equals(copy.optString("phase"))){if(s.completedAt==0){s.completedAt=initial?System.currentTimeMillis():copy.optLong("finishedAt",0);if(s.completedAt<=0)s.completedAt=System.currentTimeMillis();}}else s.completedAt=0;return copy.put("completedAt",s.completedAt).toString();}
        catch(Exception e){throw new IllegalArgumentException("Invalid live snapshot",e);}
    }
    private JSONObject update(Session s,String score,int revision)throws Exception{return new JSONObject().put("revision",revision).put("cipher",TransferCrypto.seal(s.id,"out",s.key,score)).put("completedAt",new JSONObject(score).optLong("completedAt",0));}
    private void status(String value){synchronized(this){status=value;}main.post(()->{Listener l;synchronized(this){l=listener;}if(l!=null)l.status(value);});}
    private void status(Session expected,String value){synchronized(this){if(session!=expected)return;status=value;}main.post(()->{Listener l;synchronized(this){if(session!=expected)return;l=listener;}if(l!=null)l.status(value);});}
    synchronized void start(JSONObject score){
        if(session!=null)return;Session s=new Session(score.optString("matchId"));s.last=snapshot(s,score,true);final String initial=s.last;session=s;status("Starting live link…");
        if(connectivity!=null&&reconnect==null){reconnect=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network n){retry();}};try{connectivity.registerDefaultNetworkCallback(reconnect);}catch(RuntimeException e){reconnect=null;}}
        network.execute(()->{try{JSONObject body=new JSONObject().put("id",s.id).put("ownerToken",s.owner).put("viewerToken",s.viewer).put("cipher",TransferCrypto.seal(s.id,"out",s.key,s.discarded?discardNotice():initial)).put("completedAt",s.discarded?0:new JSONObject(initial).optLong("completedAt",0));JSONObject result=transport.send("POST","",null,body);s.expiresAt=result.getLong("expiresAt");s.ready=true;boolean current,finalPending;synchronized(this){current=session==s;finalPending=completed.containsKey(s.id);}if(!current){if(s.discarded)scheduleDiscard(s);else if(finalPending)scheduleCompleted(s);else transport.send("DELETE","/"+s.id,s.owner,null);return;}status(s,"Live · audience link ready");drain(s);}catch(Exception e){boolean current;synchronized(this){current=session==s;if(current)session=null;completed.remove(s.id);}if(current){unregister();status("Could not start sharing. Check the connection and try again.");}else if(s.discarded){synchronized(this){discarded.remove(s.id);if(session==null)status("Match discarded");}unregister();}else unregister();}});
    }
    synchronized void offer(JSONObject score){
        Session s=session;if(s==null)return;if(!s.matchId.equals(score.optString("matchId"))){stop(!s.last.contains("\"phase\":\"finished\""));return;}String next=snapshot(s,score,false);
        if(next.equals(s.last))return;s.last=next;s.pending=next;if(s.ready)schedule(s);
    }
    private synchronized void retry(){Session s=session;if(s!=null&&s.ready&&s.pending!=null)schedule(s);for(Session ending:new java.util.ArrayList<>(discarded.values()))if(ending.ready)scheduleDiscard(ending);for(Session ending:new java.util.ArrayList<>(completed.values()))if(ending.ready)scheduleCompleted(ending);}
    private synchronized void schedule(Session s){if(s.draining)return;s.draining=true;network.execute(()->drain(s));}
    private void drain(Session s){
        while(true){String score;int revision;synchronized(this){if(session!=s||s.pending==null){s.draining=false;return;}score=s.pending;s.pending=null;revision=++s.revision;}
            try{if(System.currentTimeMillis()>=s.expiresAt)throw new IOException("Expired");JSONObject result=transport.send("PUT","/"+s.id,s.owner,update(s,score,revision));s.expiresAt=result.optLong("expiresAt",s.expiresAt);status(s,new JSONObject(score).optString("phase").equals("finished")?"Final result · audience link lasts ten minutes":"Live · latest score shared");}
            catch(Exception e){synchronized(this){if(session==s&&s.pending==null)s.pending=score;s.draining=false;}status(s,System.currentTimeMillis()>=s.expiresAt?"Live link expired. Stop and create a new link.":"Offline · latest score queued");return;}
        }
    }
    void stop(boolean revoke){final Session ending;synchronized(this){ending=session;if(ending==null)return;session=null;if(!revoke){completed.put(ending.id,ending);while(completed.size()>8)completed.remove(completed.keySet().iterator().next());}}status("Sharing stopped");if(!revoke){if(ending.ready)scheduleCompleted(ending);return;}unregister();network.execute(()->{try{if(ending.ready)transport.send("DELETE","/"+ending.id,ending.owner,null);}catch(Exception e){synchronized(this){if(session==null)status("Stopped on watch · audience link expires automatically");}}});}
    private synchronized void scheduleCompleted(Session s){if(completed.get(s.id)!=s||s.completeSending)return;s.completeSending=true;network.execute(()->{try{final int revision;synchronized(this){revision=++s.revision;}if(System.currentTimeMillis()<s.expiresAt)transport.send("PUT","/"+s.id,s.owner,update(s,s.last,revision));synchronized(this){completed.remove(s.id);s.completeSending=false;}unregister();}catch(Exception e){synchronized(this){s.completeSending=false;if(System.currentTimeMillis()>=s.expiresAt)completed.remove(s.id);}unregister();}});}
    private static String discardNotice()throws Exception{return new JSONObject().put("schema",1).put("phase","discarded").toString();}
    /** Replace the shared snapshot with a notice containing no names, scores or history. */
    void discard(){
        final Session ending;synchronized(this){ending=session;if(ending==null)return;ending.discarded=true;ending.last="";ending.pending=null;session=null;discarded.put(ending.id,ending);while(discarded.size()>8)discarded.remove(discarded.keySet().iterator().next());}
        status("Match discarded · updating audience…");if(ending.ready)scheduleDiscard(ending);
    }
    private synchronized void scheduleDiscard(Session s){if(discarded.get(s.id)!=s||s.discardSending)return;s.discardSending=true;network.execute(()->publishDiscard(s));}
    private void publishDiscard(Session s){
        final int revision;synchronized(this){if(discarded.get(s.id)!=s){s.discardSending=false;return;}revision=++s.revision;}
        try{
            if(System.currentTimeMillis()>=s.expiresAt)throw new IOException("Expired");
            String notice=discardNotice();transport.send("PUT","/"+s.id,s.owner,new JSONObject().put("revision",revision).put("cipher",TransferCrypto.seal(s.id,"out",s.key,notice)));
            synchronized(this){discarded.remove(s.id);s.discardSending=false;if(session==null)status("Match discarded · audience updated");}unregister();
        }catch(Exception e){
            synchronized(this){s.discardSending=false;boolean expired=System.currentTimeMillis()>=s.expiresAt;if(expired)discarded.remove(s.id);if(session==null)status(expired?"Match discarded · audience link expired":"Offline · discard notice queued");}unregister();
        }
    }
    private synchronized void unregister(){if(session!=null||!discarded.isEmpty()||!completed.isEmpty())return;if(reconnect!=null&&connectivity!=null){try{connectivity.unregisterNetworkCallback(reconnect);}catch(RuntimeException ignored){}reconnect=null;}}
    private static JSONObject request(String method,String path,String token,JSONObject body)throws Exception{
        if(!path.matches("(?:/[a-f0-9]{32})?"))throw new IllegalArgumentException("Invalid live path");HttpURLConnection c=(HttpURLConnection)new URL(BuildConfig.SITE_URL+"/api/live"+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(12000);c.setRequestMethod(method);c.setRequestProperty("Cache-Control","no-store");if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);
        try{if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>48000)throw new IllegalArgumentException("Live score is too large");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("Live service returned "+code);ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] bytes=new byte[1024];int n;while((n=in.read(bytes))>=0){if(out.size()+n>48000)throw new IOException("Live response is too large");out.write(bytes,0,n);}}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }finally{c.disconnect();}
    }
}
