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
    static void stopIfPresent(){if(instance!=null)instance.stop(true);}
    static final class Session {
        final String id=UUID.randomUUID().toString().replace("-",""),key=TransferCrypto.random(32),owner=TransferCrypto.random(32),viewer=TransferCrypto.random(32),matchId;
        volatile long expiresAt;int revision=1;volatile boolean ready;String last="",pending;boolean draining;
        Session(String matchId){this.matchId=matchId;}
        String link(){return BuildConfig.SITE_URL+"/live.html#id="+id+"&p="+viewer+"&k="+key;}
    }
    private final ExecutorService network;
    private final Transport transport;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ConnectivityManager connectivity;private ConnectivityManager.NetworkCallback reconnect;
    private Session session;private Listener listener;private String status="Not sharing";
    private LiveScoreClient(Context context){this(context,LiveScoreClient::request,Executors.newSingleThreadExecutor());}
    LiveScoreClient(Context context,Transport transport,ExecutorService network){this.transport=transport;this.network=network;connectivity=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);}
    synchronized void listener(Listener next){listener=next;}
    synchronized String status(){return status;}
    synchronized Session session(){return session;}
    private void status(String value){synchronized(this){status=value;}main.post(()->{Listener l;synchronized(this){l=listener;}if(l!=null)l.status(value);});}
    private void status(Session expected,String value){synchronized(this){if(session!=expected)return;status=value;}main.post(()->{Listener l;synchronized(this){if(session!=expected)return;l=listener;}if(l!=null)l.status(value);});}
    synchronized void start(JSONObject score){
        if(session!=null)return;Session s=new Session(score.optString("matchId"));s.last=score.toString();session=s;status("Starting live link…");
        if(connectivity!=null&&reconnect==null){reconnect=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network n){retry();}};try{connectivity.registerDefaultNetworkCallback(reconnect);}catch(RuntimeException e){reconnect=null;}}
        network.execute(()->{try{JSONObject body=new JSONObject().put("id",s.id).put("ownerToken",s.owner).put("viewerToken",s.viewer).put("cipher",TransferCrypto.seal(s.id,"out",s.key,s.last));JSONObject result=transport.send("POST","",null,body);s.expiresAt=result.getLong("expiresAt");s.ready=true;boolean current;synchronized(this){current=session==s;}if(!current){transport.send("DELETE","/"+s.id,s.owner,null);return;}status(s,"Live · audience link ready");drain(s);}catch(Exception e){boolean current;synchronized(this){current=session==s;if(current)session=null;}if(current){unregister();status("Could not start sharing. Check the connection and try again.");}}});
    }
    synchronized void offer(JSONObject score){
        Session s=session;if(s==null)return;String next=score.toString();if(!s.matchId.equals(score.optString("matchId"))){stop(!s.last.contains("\"phase\":\"finished\""));return;}
        if(next.equals(s.last))return;s.last=next;s.pending=next;if(s.ready)schedule(s);
    }
    private synchronized void retry(){Session s=session;if(s!=null&&s.ready&&s.pending!=null)schedule(s);}
    private synchronized void schedule(Session s){if(s.draining)return;s.draining=true;network.execute(()->drain(s));}
    private void drain(Session s){
        while(true){String score;int revision;synchronized(this){if(session!=s||s.pending==null){s.draining=false;return;}score=s.pending;s.pending=null;revision=++s.revision;}
            try{if(System.currentTimeMillis()>=s.expiresAt)throw new IOException("Expired");transport.send("PUT","/"+s.id,s.owner,new JSONObject().put("revision",revision).put("cipher",TransferCrypto.seal(s.id,"out",s.key,score)));status(s,"Live · latest score shared");}
            catch(Exception e){synchronized(this){if(session==s&&s.pending==null)s.pending=score;s.draining=false;}status(s,System.currentTimeMillis()>=s.expiresAt?"Live link expired. Stop and create a new link.":"Offline · latest score queued");return;}
        }
    }
    void stop(boolean revoke){final Session ending;final String last;final int revision;synchronized(this){ending=session;if(ending==null)return;last=ending.last;revision=++ending.revision;session=null;}unregister();status("Sharing stopped");network.execute(()->{try{if(!ending.ready)return;if(revoke)transport.send("DELETE","/"+ending.id,ending.owner,null);else transport.send("PUT","/"+ending.id,ending.owner,new JSONObject().put("revision",revision).put("cipher",TransferCrypto.seal(ending.id,"out",ending.key,last)));}catch(Exception e){synchronized(this){if(revoke&&session==null)status("Stopped on watch · audience link expires automatically");}}});}
    private synchronized void unregister(){if(reconnect!=null&&connectivity!=null){try{connectivity.unregisterNetworkCallback(reconnect);}catch(RuntimeException ignored){}reconnect=null;}}
    private static JSONObject request(String method,String path,String token,JSONObject body)throws Exception{
        if(!path.matches("(?:/[a-f0-9]{32})?"))throw new IllegalArgumentException("Invalid live path");HttpURLConnection c=(HttpURLConnection)new URL(BuildConfig.SITE_URL+"/api/live"+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(12000);c.setRequestMethod(method);c.setRequestProperty("Cache-Control","no-store");if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);
        try{if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>48000)throw new IllegalArgumentException("Live score is too large");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("Live service returned "+code);ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] bytes=new byte[1024];int n;while((n=in.read(bytes))>=0){if(out.size()+n>48000)throw new IOException("Live response is too large");out.write(bytes,0,n);}}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }finally{c.disconnect();}
    }
}
