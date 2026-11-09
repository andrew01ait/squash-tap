package at.andrew.squashtap;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Foreground, explicit HTTPS actions only. No scheduler, retry loop or persistent credentials. */
final class TransferClient {
    static final class Session {
        final String id,key,owner,guest;long expiresAt;
        Session(){id=UUID.randomUUID().toString().replace("-","");key=TransferCrypto.random(32);owner=TransferCrypto.random(32);guest=TransferCrypto.random(32);}
        String link(){return BuildConfig.SITE_URL+"/transfer.html#id="+id+"&p="+guest+"&k="+key;}
        boolean expired(){return System.currentTimeMillis()>=expiresAt;}
    }
    static String base(MatchBook book,OpponentDirectory opponents){return TransferCrypto.fingerprint(opponents.encode()+"|"+book.current().id());}
    static JSONArray catalog(OpponentDirectory directory)throws Exception{
        JSONArray out=new JSONArray();for(OpponentDirectory.Entry e:directory.list(true))out.put(new JSONObject().put("id",e.id).put("name",e.name).put("archived",e.archived));return out;
    }
    static JSONObject snapshot(Session session,MatchBook book,OpponentDirectory directory,List<MatchResult> selected)throws Exception{
        return new JSONObject().put("schema",1).put("sessionId",session.id).put("baseDirectory",base(book,directory))
            .put("currentMatchId",book.current().id()).put("currentMatchComplete",book.current().complete()).put("supportsRetirement",true).put("totalResults",book.count()).put("currentOpponentId",directory.opponentId(book.current().id())).put("opponents",catalog(directory)).put("csv",MatchCsv.export(selected,directory));
    }
    static Session create(MatchBook book,OpponentDirectory directory,List<MatchResult> selected)throws Exception{
        Session session=new Session();JSONObject payload=snapshot(session,book,directory,selected);
        String cipher=TransferCrypto.seal(session.id,"out",session.key,payload.toString());
        JSONObject reply=request("POST","",null,new JSONObject().put("id",session.id).put("ownerToken",session.owner).put("guestToken",session.guest).put("cipher",cipher));
        session.expiresAt=reply.getLong("expiresAt");return session;
    }
    static JSONObject receive(Session session)throws Exception{
        if(session.expired())throw new IllegalArgumentException("This transfer expired. Start a new one.");
        JSONObject reply=request("GET","/"+session.id+"/incoming",session.owner,null);
        JSONObject payload=new JSONObject(TransferCrypto.open(session.id,"in",session.key,reply.getString("cipher")));
        if(payload.getInt("schema")!=1||!payload.getString("sessionId").equals(session.id))throw new IllegalArgumentException("Invalid transfer context");return payload;
    }
    static MatchCsv.Plan preview(JSONObject payload,MatchBook book,OpponentDirectory directory)throws Exception{
        if(!payload.getString("baseDirectory").equals(base(book,directory))||!payload.getString("currentMatchId").equals(book.current().id()))throw new IllegalArgumentException("Opponents or the current match changed. Start a fresh phone transfer.");
        JSONArray catalog=payload.getJSONArray("opponents");if(catalog.length()>OpponentDirectory.MAX_OPPONENTS)throw new IllegalArgumentException("Too many opponents");
        List<OpponentDirectory.Entry> entries=new ArrayList<>();for(int i=0;i<catalog.length();i++){JSONObject e=catalog.getJSONObject(i);entries.add(new OpponentDirectory.Entry(e.getString("id"),e.getString("name"),e.getBoolean("archived")));}
        OpponentDirectory modified=OpponentDirectory.decode(directory.encode());modified.replaceCatalog(entries);
        String id=Boolean.TRUE.equals(payload.opt("changeCurrentOpponent"))?payload.getString("currentOpponentId"):directory.opponentId(book.current().id());OpponentDirectory.Entry entry=modified.get(id);
        if(!id.isEmpty()&&(entry==null||entry.archived&&!directory.opponentId(book.current().id()).equals(id)))throw new IllegalArgumentException("Choose a saved opponent or No name");
        modified.assign(book.current().id(),id);String csv=payload.getString("csv");return MatchCsv.plan(book,modified,csv.isEmpty()?Collections.emptyList():MatchCsv.parse(csv));
    }
    static void end(Session session)throws Exception{request("DELETE","/"+session.id,session.owner,null);}
    private static JSONObject request(String method,String path,String token,JSONObject body)throws Exception{
        if(!path.matches("(?:/[a-f0-9]{32}(?:/incoming)?)?"))throw new IllegalArgumentException("Invalid transfer path");
        HttpURLConnection connection=(HttpURLConnection)new URL(BuildConfig.SITE_URL+"/api/transfer"+path).openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(12000);connection.setReadTimeout(20000);connection.setRequestMethod(method);connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("Cache-Control","no-store");if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
        try{
            if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>1500600)throw new IllegalArgumentException("Transfer is too large. Export a smaller date range.");connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
            int code=connection.getResponseCode();if(code>=300&&code<400)throw new IOException("The transfer website redirected unexpectedly");InputStream stream=code>=200&&code<300?connection.getInputStream():connection.getErrorStream();if(stream==null)throw new IOException("Transfer service returned "+code);
            ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=stream){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>1500600)throw new IOException("Transfer response is too large");out.write(buffer,0,n);}}
            JSONObject reply=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));if(code<200||code>=300)throw new IOException(reply.optString("error","Transfer failed ("+code+")"));return reply;
        }finally{connection.disconnect();}
    }
}
