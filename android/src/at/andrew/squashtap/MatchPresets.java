package at.andrew.squashtap;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small user-named format collection, independent of people and active matches. */
public final class MatchPresets {
    public static final int MAX=24;
    public static final class Entry {
        public final String id,name;public final MatchFormat format;public final int best;
        Entry(String id,String name,MatchFormat format,int best){OpponentDirectory.validId(id);this.id=id;this.name=OpponentDirectory.validName(name);format.validateBest(best);this.format=format;this.best=best;}
    }
    private final List<Entry> entries=new ArrayList<>();
    public List<Entry> list(String sport){List<Entry> out=new ArrayList<>();for(Entry e:entries)if(sport==null||sport.equals(e.format.sport))out.add(e);return out;}
    public Entry add(String name,MatchFormat format,int best){if(entries.size()>=MAX)throw new IllegalArgumentException("Keep up to 24 favourite formats. Remove one before adding another.");Entry e=new Entry(UUID.randomUUID().toString(),name,format,best);entries.add(e);return e;}
    public void remove(String id){entries.removeIf(e->e.id.equals(id));}
    public String encode(){StringBuilder b=new StringBuilder("P1");for(Entry e:entries)b.append('\n').append(e.id).append('\t').append(Base64.getEncoder().encodeToString(e.name.getBytes(StandardCharsets.UTF_8))).append('\t').append(e.format.encode()).append('\t').append(e.best);return b.toString();}
    public static MatchPresets decode(String value){MatchPresets p=new MatchPresets();if(value==null||value.isEmpty())return p;if(value.length()>24000)throw new IllegalArgumentException("Favourite formats are too large");String[] lines=value.split("\n",-1);if(!lines[0].equals("P1")||lines.length>MAX+1)throw new IllegalArgumentException("Invalid favourite formats");Set<String> ids=new HashSet<>();for(int i=1;i<lines.length;i++){String[] c=lines[i].split("\t",-1);if(c.length!=4||!ids.add(c[0]))throw new IllegalArgumentException("Invalid favourite format");p.entries.add(new Entry(c[0],new String(Base64.getDecoder().decode(c[1]),StandardCharsets.UTF_8),MatchFormat.decode(c[2]),Integer.parseInt(c[3])));}return p;}
}
