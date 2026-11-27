package at.andrew.squashtap;

import java.io.*;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** One atomic persistence unit: active match, immutable results and daily numbering. */
public final class MatchBook {
    public static final int MAX_RESULTS=10000;
    private static final int MAX_BOOK_SIZE=5_000_000;
    private static final int MAX_NOTES_BYTES=512*1024;
    static final int MAX_ENCODED_SIZE=MAX_BOOK_SIZE+4*((MAX_NOTES_BYTES+4)/3+1);
    private MatchState current;
    private final Clock clock;
    private long startedAt,finishedAt;
    private String zoneId="";
    private boolean timingEligible;
    private final LinkedHashMap<String,MatchResult> results=new LinkedHashMap<>();
    private final LinkedHashMap<String,MatchNotes> notes=new LinkedHashMap<>();
    private final TreeMap<String,Integer> dailyNumbers=new TreeMap<>();
    private String completionDate="";
    private int completionNumber;
    private boolean deletedCurrent;
    public MatchBook(MatchState current){this(current,Clock.systemDefaultZone());}
    public MatchBook(MatchState current,Clock clock){this.current=Objects.requireNonNull(current);this.clock=Objects.requireNonNull(clock);timingEligible=!current.hasPoints();}
    public String currentZoneId(){return zoneId;}
    public long currentStartedAt(){return startedAt;}
    public long currentFinishedAt(){return finishedAt;}
    public String currentStartedText(){return startedAt>0?MatchResult.formatMoment(startedAt,zoneId,"dd.MM.yyyy HH:mm:ss z"):timingEligible?"Starts with first point":"Not recorded";}
    public MatchState current(){return current;}
    public int count(){return results.size();}
    public MatchResult result(String id){return results.get(id);}
    public boolean canAnnotate(String id){return results.containsKey(id)||current.id().equals(id)&&!deletedCurrent;}
    public MatchNotes notes(String id){return notes.getOrDefault(id,MatchNotes.EMPTY);}
    public void setNotes(String id,MatchNotes value){
        if(!canAnnotate(id))throw new IllegalArgumentException("This match is no longer available.");
        if(value.isEmpty()){notes.remove(id);return;}
        int bytes=4+noteBytes(id,value);
        for(Map.Entry<String,MatchNotes> e:notes.entrySet())
            if(!e.getKey().equals(id))bytes+=noteBytes(e.getKey(),e.getValue());
        if(bytes>MAX_NOTES_BYTES)throw new IllegalArgumentException("Match notes storage is full.");
        notes.put(id,value);
    }
    private static int noteBytes(String id,MatchNotes value){return 2+id.length()+value.storageEstimate();}
    public List<MatchResult> newestFirst(){List<MatchResult> list=new ArrayList<>(results.values());Collections.reverse(list);return Collections.unmodifiableList(list);}
    public boolean currentDeleted(){return deletedCurrent;}
    public boolean add(int player){Instant now=clock.instant();return addAt(player,now.atZone(clock.getZone()).toLocalDate(),now);}
    public boolean add(int player,LocalDate today){return addAt(player,today,clock.instant());}
    private boolean addAt(int player,LocalDate today,Instant now){
        if(!current.add(player))return false;
        long millis=Math.max(0,now.toEpochMilli());
        if(startedAt==0&&timingEligible){
            if(millis==0)timingEligible=false;
            else{startedAt=millis;zoneId=clock.getZone().getId();}
        }
        if(current.complete()){
            finishedAt=millis;if(zoneId.isEmpty()&&millis>0)zoneId=clock.getZone().getId();
            saveCompleted(today);
        }
        return true;
    }
    public boolean saveCompleted(LocalDate today){
        if(!current.complete()||deletedCurrent)return false;
        if(completionDate.isEmpty())completionDate=today.toString();
        if(!results.containsKey(current.id())&&results.size()>=MAX_RESULTS)return false;
        if(completionNumber==0){
            int old=dailyNumbers.containsKey(completionDate)?dailyNumbers.get(completionDate):0;
            if(old==Integer.MAX_VALUE)throw new IllegalStateException("Daily match number limit reached");
            completionNumber=old+1;dailyNumbers.put(completionDate,completionNumber);
        }
        results.put(current.id(),MatchResult.from(current,completionDate,completionNumber,startedAt,finishedAt,zoneId));
        return true;
    }
    public boolean retire(int player){
        if(!current.retire(player))return false;
        Instant now=clock.instant();finishedAt=Math.max(0,now.toEpochMilli());
        if(zoneId.isEmpty()&&finishedAt>0)zoneId=clock.getZone().getId();
        saveCompleted(now.atZone(clock.getZone()).toLocalDate());return true;
    }
    public boolean enterCompletedSet(int me,int opponent){
        if(!current.enterCompletedSet(me,opponent))return false;
        Instant now=clock.instant();
        if(startedAt==0){
            startedAt=Math.max(0,now.toEpochMilli());timingEligible=startedAt>0;
            zoneId=timingEligible?clock.getZone().getId():"";
        }
        finishedAt=0;
        if(current.complete())saveCompleted(now.atZone(clock.getZone()).toLocalDate());return true;
    }
    public boolean undo(){
        if(!current.undo())return false;
        finishedAt=0;if(startedAt==0)zoneId="";results.remove(current.id());return true;
    }
    public boolean nextGame(){return current.nextGame();}
    public boolean changeBestOf(int value){return current.changeBestOf(value);}
    /** Discard only an unfinished current match. No result or daily reference is created. */
    public void discardCurrent(String replacementId){
        if(current.complete())throw new IllegalStateException("Finished matches must be deleted from Results");
        newMatch(replacementId,current.bestOf(),current.format());
    }
    public void newMatch(String id,int bestOf){
        if(id.equals(current.id())||results.containsKey(id))throw new IllegalArgumentException("Match identifier already used");
        newMatch(id,bestOf,MatchFormat.squash());
    }
    public void newMatch(String id,int bestOf,MatchFormat format){
        if(id.equals(current.id())||results.containsKey(id))throw new IllegalArgumentException("Match identifier already used");
        MatchState replacement=new MatchState(id,bestOf,format);
        if(!results.containsKey(current.id()))notes.remove(current.id());
        current=replacement;completionDate="";completionNumber=0;deletedCurrent=false;startedAt=0;finishedAt=0;zoneId="";timingEligible=true;
    }
    public boolean delete(String id){
        MatchResult removed=results.remove(id);
        if(removed==null)return false;
        notes.remove(id);
        if(id.equals(current.id()))deletedCurrent=true;
        return true;
    }
    public void deleteAll(){
        results.clear();
        if(!completionDate.isEmpty())deletedCurrent=true;
        notes.keySet().removeIf(id->!canAnnotate(id));
    }
    /** Merge a foreign completed result without touching the current match or reusing a reference. */
    public MatchResult importResult(MatchResult result){
        if(result.id.equals(current.id())||results.containsKey(result.id))throw new IllegalArgumentException("Match ID already exists");
        if(results.size()>=MAX_RESULTS)throw new IllegalArgumentException("Result storage is full");
        MatchResult imported = result.renumber(nextNumber(result.date));
        results.put(imported.id, imported);
        return imported;
    }
    private int nextNumber(String date) {
        int last = dailyNumbers.getOrDefault(date, 0);
        if (last == Integer.MAX_VALUE) throw new IllegalArgumentException("Daily match number limit reached");
        if (last == 0 && dailyNumbers.size() >= 10000) throw new IllegalArgumentException("Date storage is full");
        dailyNumbers.put(date, last + 1);
        return last + 1;
    }
    /** A corrected result keeps its identity. Moving to a different day consumes a new daily reference. */
    void correctResult(MatchResult value) {
        MatchResult old = result(value.id);
        if (old == null) throw new IllegalArgumentException("This result was removed. Start a fresh phone transfer.");
        if (!old.format.equals(value.format) || old.bestOf != value.bestOf ||
                old.startedAt != value.startedAt || old.finishedAt != value.finishedAt || !old.zoneId.equals(value.zoneId) ||
                (old.startedAt != 0 || old.finishedAt != 0) && !old.date.equals(value.date))
            throw new IllegalArgumentException("Recorded rules and timing must be preserved");
        boolean onScoreboard = current.id().equals(value.id);
        MatchState corrected = onScoreboard ? current.corrected(value) : null;
        int number = old.date.equals(value.date) ? old.number : nextNumber(value.date);
        MatchResult saved = value.renumber(number);
        if (onScoreboard) {
            current = corrected;
            completionDate = saved.date;
            completionNumber = saved.number;
            saved = MatchResult.from(current, saved.date, saved.number, startedAt, finishedAt, zoneId);
        }
        results.put(saved.id, saved);
    }
    public String encode(){
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
            String currentState=current.encode();
            boolean multi=currentState.startsWith("M4|")||currentState.startsWith("M5|");for(MatchResult result:results.values())if(!result.format.legacy())multi=true;
            boolean annotated=!notes.isEmpty();
            if(annotated)out.writeBoolean(multi);
            if(multi){byte[] state=currentState.getBytes(java.nio.charset.StandardCharsets.UTF_8);out.writeInt(state.length);out.write(state);}else out.writeUTF(currentState);out.writeUTF(completionDate);out.writeInt(completionNumber);out.writeBoolean(deletedCurrent);
            out.writeLong(startedAt);out.writeLong(finishedAt);out.writeUTF(zoneId);out.writeBoolean(timingEligible);
            out.writeInt(dailyNumbers.size());for(Map.Entry<String,Integer> entry:dailyNumbers.entrySet()){out.writeUTF(entry.getKey());out.writeInt(entry.getValue());}
            out.writeInt(results.size());for(MatchResult result:results.values()){if(multi)result.writeExtended(out);else result.write(out);}
            if(annotated){
                out.writeInt(notes.size());
                for(Map.Entry<String,MatchNotes> entry:notes.entrySet()){
                    out.writeUTF(entry.getKey());
                    entry.getValue().write(out);
                }
            }
            out.flush();
            String prefix=annotated?"B6:":multi?"B5:":"B4:";
            return prefix+Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(IOException error){throw new IllegalStateException("Cannot serialize match history",error);}
    }
    public static MatchBook decode(String encoded){return decode(encoded,Clock.systemDefaultZone());}
    public static MatchBook decode(String encoded,Clock clock){
        try{
            if(encoded==null||encoded.length()<3||encoded.charAt(0)!='B'||encoded.charAt(1)<'1'
                ||encoded.charAt(1)>'6'||encoded.charAt(2)!=':')throw new IOException("Unknown match history");
            boolean annotated=encoded.startsWith("B6:");
            if(encoded.length()>(annotated?MAX_ENCODED_SIZE:MAX_BOOK_SIZE))throw new IOException("Oversized match history");
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded.substring(3))));
            boolean multi=annotated?in.readBoolean():encoded.startsWith("B5:");
            boolean entered=annotated||multi||encoded.startsWith("B4:");
            boolean retirements=entered||encoded.startsWith("B3:"),timed=!encoded.startsWith("B1:");
            String state;if(multi){int size=in.readInt();if(size<1||size>75000)throw new IOException("Invalid current match length");byte[] data=new byte[size];in.readFully(data);state=new String(data,java.nio.charset.StandardCharsets.UTF_8);}else state=in.readUTF();
            MatchBook book=new MatchBook(MatchState.decode(state),clock);book.completionDate=in.readUTF();book.completionNumber=in.readInt();book.deletedCurrent=in.readBoolean();
            if(!retirements&&book.current.retired())throw new IOException("Retirement requires B3 storage");
            if(!entered&&book.current.hasEnteredSets())throw new IOException("Entered scores require B4 storage");
            if(timed){book.startedAt=in.readLong();book.finishedAt=in.readLong();book.zoneId=in.readUTF();book.timingEligible=in.readBoolean();}
            MatchResult.validateTimes(book.startedAt,book.finishedAt,book.zoneId);
            if(book.finishedAt>0&&!book.current.complete()||book.startedAt>0&&!book.timingEligible)throw new IOException("Inconsistent active match timing");
            if(book.completionNumber<0||book.completionDate.isEmpty()&&(book.completionNumber!=0||book.deletedCurrent))throw new IOException("Invalid completion marker");
            if(!book.completionDate.isEmpty()&&!LocalDate.parse(book.completionDate).toString().equals(book.completionDate))throw new IOException("Invalid completion date");
            int days=in.readInt();if(days<0||days>10000)throw new IOException("Invalid date-counter count");
            for(int i=0;i<days;i++){
                String date=in.readUTF();int number=in.readInt();
                if(number<1||!LocalDate.parse(date).toString().equals(date)||book.dailyNumbers.put(date,number)!=null)throw new IOException("Invalid daily counter");
            }
            int count=in.readInt();if(count<0||count>MAX_RESULTS)throw new IOException("Invalid result count");
            HashSet<String> references=new HashSet<>();
            for(int i=0;i<count;i++){
                MatchResult result=MatchResult.read(in,timed,retirements,multi);
                if(book.results.put(result.id,result)!=null||!references.add(result.date+"/"+result.number)
                    ||!book.dailyNumbers.containsKey(result.date)||book.dailyNumbers.get(result.date)<result.number)throw new IOException("Invalid result identity");
            }
            if(book.completionNumber>0&&(!book.dailyNumbers.containsKey(book.completionDate)||book.dailyNumbers.get(book.completionDate)<book.completionNumber))throw new IOException("Missing completion counter");
            MatchResult active=book.result(book.current.id());
            if(active!=null&&(book.deletedCurrent||!active.sameScores(book.current)||!active.date.equals(book.completionDate)||active.number!=book.completionNumber||active.startedAt!=book.startedAt||active.finishedAt!=book.finishedAt||!active.zoneId.equals(book.zoneId)))throw new IOException("Inconsistent current result");
            if(annotated)book.readNotes(in);
            if(in.read()!=-1)throw new IOException("Trailing saved data");
            return book;
        }catch(Exception error){throw new IllegalArgumentException("Saved match history could not be read",error);}
    }
    private void readNotes(DataInputStream in)throws IOException{
        if(in.available()>MAX_NOTES_BYTES)throw new IOException("Oversized match notes");
        int count=in.readInt(),bytes=4;
        if(count<1||count>results.size()+1)throw new IOException("Invalid match note count");
        for(int i=0;i<count;i++){
            String id=in.readUTF();
            MatchNotes value=MatchNotes.read(in);
            if(!canAnnotate(id)||value.isEmpty()||notes.put(id,value)!=null)throw new IOException("Invalid match note");
            bytes+=noteBytes(id,value);
        }
        if(bytes>MAX_NOTES_BYTES)throw new IOException("Oversized match notes");
    }
    public static MatchBook migrateLegacy(String id,String savedScore){return new MatchBook(MatchState.fromLegacy(id,savedScore));}
}
