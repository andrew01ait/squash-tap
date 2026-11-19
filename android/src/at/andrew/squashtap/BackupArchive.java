package at.andrew.squashtap;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;

/** Complete, checksummed user-data archive. No keys, diagnostics, network sessions or permissions. */
public final class BackupArchive {
    public static final int MAX_JSON=20*1024*1024;
    public final MatchBook book;public final OpponentDirectory directory;public final Map<String,String> settings;public final String createdAt;
    private BackupArchive(String created,MatchBook b,OpponentDirectory d,Map<String,String> s){createdAt=created;book=b;directory=d;settings=Collections.unmodifiableMap(new TreeMap<>(s));validateSettings(s);String ready=s.get("match_ready_id");if(ready!=null&&!ready.equals(b.current().id()))throw new IllegalArgumentException("Backup prepared match does not match its score");}
    public static boolean settingKey(String key){if(Arrays.asList("my_name","score_palette","favourite_formats","selected_sport","match_keep_awake_v3","haptic_mode","match_reminders","match_paused","match_ready_id","last_best_of").contains(key))return true;for(String sport:MatchFormat.SPORTS)if(key.equals("last_format_"+sport)||key.equals("last_best_"+sport))return true;return false;}
    public static void validateSettings(Map<String,String> s){if(s.size()>32)throw new IllegalArgumentException("Too many backup settings");for(Map.Entry<String,String> e:s.entrySet()){
        String k=e.getKey(),v=e.getValue();if(!settingKey(k)||v==null||v.length()>24000)throw new IllegalArgumentException("Unsupported backup setting");
        if(k.equals("my_name")&&!v.isEmpty())OpponentDirectory.validName(v);
        else if(k.equals("score_palette")&&!ScorePalette.valid(v))throw new IllegalArgumentException("Unsupported colour scheme");
        else if(k.equals("favourite_formats"))MatchPresets.decode(v);
        else if(k.equals("selected_sport")&&!Arrays.asList(MatchFormat.SPORTS).contains(v))throw new IllegalArgumentException("Unsupported selected sport");
        else if(k.equals("haptic_mode")&&!Arrays.asList("off","points","milestones").contains(v))throw new IllegalArgumentException("Unsupported vibration setting");
        else if((k.equals("match_keep_awake_v3")||k.equals("match_reminders")||k.equals("match_paused"))&&!v.equals("true")&&!v.equals("false"))throw new IllegalArgumentException("Invalid display setting");
        else if(k.equals("match_ready_id"))OpponentDirectory.validId(v);
        else if(k.equals("last_best_of")){if(!Arrays.asList("1","3","5","7").contains(v))throw new IllegalArgumentException("Invalid match length");}
        else if(k.startsWith("last_format_")){MatchFormat f=MatchFormat.decode(v);if(!k.equals("last_format_"+f.sport))throw new IllegalArgumentException("Inconsistent format setting");}
        else if(k.startsWith("last_best_")){String sport=k.substring(10);MatchFormat.defaults(sport).validateBest(Integer.parseInt(v));}
    }}
    private static void text(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    private static String text(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max||n>in.available())throw new IOException("Invalid backup field size");byte[] bytes=new byte[n];in.readFully(bytes);return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}
    public static String create(MatchBook b,OpponentDirectory d,Map<String,String> settings){try{
        String created=Instant.now().toString();new BackupArchive(created,b,d,settings);ByteArrayOutputStream raw=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(raw);out.writeInt(0x4b534231);text(out,b.encode());text(out,d.encode());out.writeInt(settings.size());for(Map.Entry<String,String> e:new TreeMap<>(settings).entrySet()){text(out,e.getKey());text(out,e.getValue());}out.close();String payload=Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray());String json="{\"application\":\"Keep Score\",\"package\":\"at.andrew.squashtap\",\"schema\":1,\"createdAt\":\""+created+"\",\"payload\":\""+payload+"\",\"sha256\":\""+TransferCrypto.fingerprint(payload)+"\"}";if(json.length()>MAX_JSON)throw new IllegalArgumentException("The full backup exceeds the supported size. Nothing was omitted.");return json;
    }catch(IOException e){throw new IllegalArgumentException("Cannot create backup",e);}}
    public static BackupArchive parse(String json){try{
        if(json==null||json.length()>MAX_JSON)throw new IllegalArgumentException("Backup is too large");
        Pattern p=Pattern.compile("\\s*\\{\\s*\"application\"\\s*:\\s*\"Keep Score\"\\s*,\\s*\"package\"\\s*:\\s*\"at.andrew.squashtap\"\\s*,\\s*\"schema\"\\s*:\\s*1\\s*,\\s*\"createdAt\"\\s*:\\s*\"([0-9TZ:.+\\-]+)\"\\s*,\\s*\"payload\"\\s*:\\s*\"([A-Za-z0-9_\\-]+)\"\\s*,\\s*\"sha256\"\\s*:\\s*\"([A-Za-z0-9_\\-]{43})\"\\s*\\}\\s*");
        Matcher m=p.matcher(json);if(!m.matches())throw new IllegalArgumentException("Use a complete Keep Score backup JSON file");Instant.parse(m.group(1));if(!java.security.MessageDigest.isEqual(TransferCrypto.fingerprint(m.group(2)).getBytes(StandardCharsets.US_ASCII),m.group(3).getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException("Backup checksum does not match");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(m.group(2))));if(in.readInt()!=0x4b534231)throw new IllegalArgumentException("Unsupported backup version");MatchBook book=MatchBook.decode(text(in,5000000));OpponentDirectory dir=OpponentDirectory.decode(text(in,8000000));int count=in.readInt();if(count<0||count>32)throw new IllegalArgumentException("Invalid backup settings");Map<String,String> settings=new TreeMap<>();for(int i=0;i<count;i++){String k=text(in,80),v=text(in,96000);if(settings.put(k,v)!=null)throw new IllegalArgumentException("Duplicate backup setting");}if(in.available()!=0)throw new IllegalArgumentException("Unexpected backup data");return new BackupArchive(m.group(1),book,dir,settings);
    }catch(Exception e){throw new IllegalArgumentException(e.getMessage()==null?"Invalid backup":e.getMessage(),e);}}
}
