package at.andrew.squashtap;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/** Separate, bounded metadata; deleting a picker entry retains historical identity. */
public final class OpponentDirectory {
    public static final int MAX_OPPONENTS=1000;
    public static final class Entry {
        public final String id,name;public final boolean archived;
        public Entry(String id,String name,boolean archived){validId(id);this.id=id;this.name=validName(name);this.archived=archived;}
    }
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    private final LinkedHashMap<String,String> assignments=new LinkedHashMap<>();
    static void validId(String id){if(id==null||!id.matches("[A-Za-z0-9-]{1,80}"))throw new IllegalArgumentException("Invalid identifier");}
    public static String validName(String value){
        if(value==null)throw new IllegalArgumentException("Enter a name");
        for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))throw new IllegalArgumentException("Names cannot contain control characters");
        String name=Normalizer.normalize(value.trim(),Normalizer.Form.NFC);
        if(name.isEmpty()||name.codePointCount(0,name.length())>60)throw new IllegalArgumentException("Use a name between 1 and 60 characters");
        for(int i=0;i<name.length();i++){char c=name.charAt(i);if(Character.isISOControl(c))throw new IllegalArgumentException("Names cannot contain control characters");if(Character.isHighSurrogate(c)){if(i+1>=name.length()||!Character.isLowSurrogate(name.charAt(++i)))throw new IllegalArgumentException("Invalid name text");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid name text");}
        return name;
    }
    private String key(String name){return name.toLowerCase(Locale.ROOT);}
    public Entry get(String id){return entries.get(id);}
    public String opponentId(String matchId){String id=assignments.get(matchId);return id==null?"":id;}
    public String nameFor(String matchId){Entry e=get(opponentId(matchId));return e==null?"":e.name;}
    public List<Entry> list(boolean includeArchived){List<Entry> out=new ArrayList<>();for(Entry e:entries.values())if(includeArchived||!e.archived)out.add(e);Collections.sort(out,(a,b)->a.name.compareToIgnoreCase(b.name));return out;}
    public String add(String name){
        name=validName(name);for(Entry e:entries.values())if(key(e.name).equals(key(name))){entries.put(e.id,new Entry(e.id,e.name,false));return e.id;}
        String id=UUID.randomUUID().toString();put(new Entry(id,name,false));return id;
    }
    public void put(Entry entry){
        if(!entries.containsKey(entry.id)&&entries.size()>=MAX_OPPONENTS)throw new IllegalArgumentException("Opponent storage is full");
        for(Entry e:entries.values())if(!e.id.equals(entry.id)&&key(e.name).equals(key(entry.name)))throw new IllegalArgumentException("That name is already saved");
        entries.put(entry.id,entry);
    }
    public void rename(String id,String name){Entry old=get(id);if(old==null)throw new IllegalArgumentException("Opponent not found");put(new Entry(id,name,old.archived));}
    public void archive(String id){Entry old=get(id);if(old==null)throw new IllegalArgumentException("Opponent not found");entries.put(id,new Entry(id,old.name,true));}
    public void assign(String matchId,String opponentId){
        validId(matchId);if(opponentId==null||opponentId.isEmpty()){assignments.remove(matchId);return;}
        if(get(opponentId)==null)throw new IllegalArgumentException("Opponent not found");
        if(!assignments.containsKey(matchId)&&assignments.size()>=20000)throw new IllegalArgumentException("Opponent assignment storage is full");
        assignments.put(matchId,opponentId);
    }
    public void replaceCatalog(List<Entry> catalog){
        OpponentDirectory replacement=new OpponentDirectory();for(Entry e:catalog){if(replacement.get(e.id)!=null)throw new IllegalArgumentException("Duplicate opponent ID");replacement.put(e);}
        for(String id:entries.keySet())if(replacement.get(id)==null)throw new IllegalArgumentException("Remove names using Delete, so past results retain their identity");
        entries.clear();entries.putAll(replacement.entries);
    }
    public String importOpponent(String id,String name){
        if(name.isEmpty())return "";
        if(id.isEmpty())id=UUID.nameUUIDFromBytes(validName(name).toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)).toString();
        if(get(id)!=null)return id;
        for(Entry e:entries.values())if(key(e.name).equals(key(validName(name))))return e.id;
        put(new Entry(id,name,false));return id;
    }
    public String encode(){
        try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(entries.size());
            for(Entry e:entries.values()){out.writeUTF(e.id);out.writeUTF(e.name);out.writeBoolean(e.archived);}out.writeInt(assignments.size());
            for(Map.Entry<String,String> e:assignments.entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}out.flush();return "O1:"+Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(IOException error){throw new IllegalStateException(error);}
    }
    public static OpponentDirectory decode(String encoded){
        if(encoded==null||encoded.isEmpty())return new OpponentDirectory();
        try{if(!encoded.startsWith("O1:")||encoded.length()>4000000)throw new IOException();DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded.substring(3))));OpponentDirectory d=new OpponentDirectory();
            int n=in.readInt();if(n<0||n>MAX_OPPONENTS)throw new IOException();for(int i=0;i<n;i++){Entry e=new Entry(in.readUTF(),in.readUTF(),in.readBoolean());if(d.get(e.id)!=null)throw new IOException();d.put(e);}
            n=in.readInt();if(n<0||n>20000)throw new IOException();for(int i=0;i<n;i++){String match=in.readUTF(),id=in.readUTF();if(d.assignments.containsKey(match)||id.isEmpty())throw new IOException();d.assign(match,id);}if(in.read()!=-1)throw new IOException();return d;
        }catch(Exception e){throw new IllegalArgumentException("Saved opponents could not be read",e);}
    }
}
