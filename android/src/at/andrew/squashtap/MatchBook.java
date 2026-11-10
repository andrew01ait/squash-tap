package at.andrew.squashtap;

import java.io.*;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** One atomic persistence unit: active match, immutable results and daily numbering. */
public final class MatchBook {
    public static final int MAX_RESULTS=10000;
    private MatchState current;
    private final Clock clock;
    private long startedAt,finishedAt;
    private String zoneId="";
    private boolean timingEligible;
    private final LinkedHashMap<String,MatchResult> results=new LinkedHashMap<>();
    private final TreeMap<String,Integer> dailyNumbers=new TreeMap<>();
    private String completionDate="";
    private int completionNumber;
    private boolean deletedCurrent;
    public MatchBook(MatchState current){this(current,Clock.systemDefaultZone());}
    public MatchBook(MatchState current,Clock clock){this.current=Objects.requireNonNull(current);this.clock=Objects.requireNonNull(clock);timingEligible=!current.hasPoints();}
    public long currentStartedAt(){return startedAt;}
    public long currentFinishedAt(){return finishedAt;}
    public String currentStartedText(){return startedAt>0?MatchResult.formatMoment(startedAt,zoneId,"dd.MM.yyyy HH:mm:ss z"):timingEligible?"Starts with first point":"Not recorded";}
    public MatchState current(){return current;}
    public int count(){return results.size();}
    public MatchResult result(String id){return results.get(id);}
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
        current=replacement;completionDate="";completionNumber=0;deletedCurrent=false;startedAt=0;finishedAt=0;zoneId="";timingEligible=true;
    }
    public boolean delete(String id){
        MatchResult removed=results.remove(id);
        if(removed==null)return false;
        if(id.equals(current.id()))deletedCurrent=true;
        return true;
    }
    public void deleteAll(){results.clear();if(!completionDate.isEmpty())deletedCurrent=true;}
    /** Merge a foreign completed result without touching the current match or reusing a reference. */
    public MatchResult importResult(MatchResult result){
        if(result.id.equals(current.id())||results.containsKey(result.id))throw new IllegalArgumentException("Match ID already exists");
        if(results.size()>=MAX_RESULTS)throw new IllegalArgumentException("Result storage is full");
        int old=dailyNumbers.containsKey(result.date)?dailyNumbers.get(result.date):0;
        if(old==Integer.MAX_VALUE)throw new IllegalArgumentException("Daily match number limit reached");
        int number=old+1;
        int[][] scores=new int[result.gameCount()][2];for(int i=0;i<scores.length;i++){scores[i][0]=result.points(i,0);scores[i][1]=result.points(i,1);}
        MatchResult imported=result.renumber(number);
        if(!dailyNumbers.containsKey(result.date)&&dailyNumbers.size()>=10000)throw new IllegalArgumentException("Date storage is full");
        dailyNumbers.put(result.date,number);results.put(result.id,imported);return imported;
    }
    public String encode(){
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
            boolean multi=current.encode().startsWith("M4|");for(MatchResult result:results.values())if(!result.format.legacy())multi=true;
            if(multi){byte[] state=current.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8);out.writeInt(state.length);out.write(state);}else out.writeUTF(current.encode());out.writeUTF(completionDate);out.writeInt(completionNumber);out.writeBoolean(deletedCurrent);
            out.writeLong(startedAt);out.writeLong(finishedAt);out.writeUTF(zoneId);out.writeBoolean(timingEligible);
            out.writeInt(dailyNumbers.size());for(Map.Entry<String,Integer> entry:dailyNumbers.entrySet()){out.writeUTF(entry.getKey());out.writeInt(entry.getValue());}
            out.writeInt(results.size());for(MatchResult result:results.values()){if(multi)result.writeExtended(out);else result.write(out);}out.flush();
            return (multi?"B5:":"B4:")+Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(IOException error){throw new IllegalStateException("Cannot serialize match history",error);}
    }
    public static MatchBook decode(String encoded){return decode(encoded,Clock.systemDefaultZone());}
    public static MatchBook decode(String encoded,Clock clock){
        try{
            if(encoded==null||!(encoded.startsWith("B1:")||encoded.startsWith("B2:")||encoded.startsWith("B3:")||encoded.startsWith("B4:")||encoded.startsWith("B5:"))||encoded.length()>5000000)throw new IOException("Unknown or oversized match history");
            boolean multi=encoded.startsWith("B5:"),entered=multi||encoded.startsWith("B4:"),retirements=entered||encoded.startsWith("B3:"),timed=!encoded.startsWith("B1:");
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded.substring(3))));
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
            if(in.read()!=-1)throw new IOException("Trailing saved data");
            return book;
        }catch(Exception error){throw new IllegalArgumentException("Saved match history could not be read",error);}
    }
    public static MatchBook migrateLegacy(String id,String savedScore){return new MatchBook(MatchState.fromLegacy(id,savedScore));}
}
