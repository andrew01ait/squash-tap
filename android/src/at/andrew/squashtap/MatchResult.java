package at.andrew.squashtap;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Immutable completed-match summary; historical games need no full rally logs. */
public final class MatchResult {
    public final String id,date,zoneId;
    public final long startedAt,finishedAt;
    public final int number,bestOf,retiredPlayer;
    private final int[][] scores;
    public MatchResult(String id,String date,int number,int bestOf,int[][] scores){this(id,date,number,bestOf,scores,0,0,"");}
    public MatchResult(String id,String date,int number,int bestOf,int[][] scores,long startedAt,long finishedAt,String zoneId){
        this(id,date,number,bestOf,scores,startedAt,finishedAt,zoneId,-1);
    }
    public MatchResult(String id,String date,int number,int bestOf,int[][] scores,long startedAt,long finishedAt,String zoneId,int retiredPlayer){
        validateTimes(startedAt,finishedAt,zoneId);
        this.startedAt=startedAt;this.finishedAt=finishedAt;this.zoneId=zoneId;
        if(id==null||!id.matches("[A-Za-z0-9-]{1,80}")||number<1||(bestOf!=3&&bestOf!=5))throw new IllegalArgumentException("Invalid result");
        if(!LocalDate.parse(date).toString().equals(date))throw new IllegalArgumentException("Invalid result date");
        if(retiredPlayer< -1||retiredPlayer>1)throw new IllegalArgumentException("Invalid retirement");
        this.retiredPlayer=retiredPlayer;
        if(scores==null||scores.length<(retired()?1:bestOf/2+1)||scores.length>bestOf)throw new IllegalArgumentException("Invalid game count");
        this.id=id;this.date=date;this.number=number;this.bestOf=bestOf;this.scores=new int[scores.length][2];
        int me=0,opponent=0;
        for(int i=0;i<scores.length;i++){
            if(me>=bestOf/2+1||opponent>=bestOf/2+1)throw new IllegalArgumentException("Games after match end");
            if(scores[i]==null||scores[i].length!=2)throw new IllegalArgumentException("Invalid game score");
            int a=scores[i][0],b=scores[i][1],high=Math.max(a,b),low=Math.min(a,b);
            boolean done=finalSet(a,b);
            if(low<0||(long)a+b>ScoreState.MAX_RALLIES||!done&&(!retired()||i!=scores.length-1||high>=11&&high-low>=2))throw new IllegalArgumentException("Invalid final game score");
            this.scores[i][0]=a;this.scores[i][1]=b;if(done){if(a>b)me++;else opponent++;}
        }
        if(retired()?(me>=bestOf/2+1||opponent>=bestOf/2+1):(me<bestOf/2+1&&opponent<bestOf/2+1))throw new IllegalArgumentException("Invalid match outcome");
    }
    public static MatchResult from(MatchState match,String date,int number){return from(match,date,number,0,0,"");}
    public static MatchResult from(MatchState match,String date,int number,long startedAt,long finishedAt,String zoneId){
        if(!match.complete())throw new IllegalArgumentException("Match is unfinished");
        int[][] scores=new int[match.gameCount()][2];
        for(int i=0;i<scores.length;i++){scores[i][0]=match.game(i).blue();scores[i][1]=match.game(i).red();}
        return new MatchResult(match.id(),date,number,match.bestOf(),scores,startedAt,finishedAt,zoneId,match.retiredPlayer());
    }
    public int gameCount(){return scores.length;}
    public int points(int game,int player){return scores[game][player];}
    private static boolean finalSet(int a,int b){int high=Math.max(a,b),low=Math.min(a,b);return low<10?high==11:high==low+2;}
    public boolean setComplete(int game){return finalSet(scores[game][0],scores[game][1]);}
    public boolean retired(){return retiredPlayer!=-1;}
    public int gamesWon(int player){int count=0;for(int i=0;i<scores.length;i++)if(setComplete(i)&&(scores[i][0]>scores[i][1]?0:1)==player)count++;return count;}
    public int winner(){return retired()?1-retiredPlayer:gamesWon(0)>gamesWon(1)?0:1;}
    public String outcomeText(){return retired()?(retiredPlayer==0?"Me retired · Opponent won":"Opponent retired · You won"):winner()==0?"You won":"Opponent won";}
    static void validateTimes(long startedAt,long finishedAt,String zoneId){
        if(startedAt<0||finishedAt<0||zoneId==null||zoneId.length()>80)throw new IllegalArgumentException("Invalid match timing");
        if(startedAt>0||finishedAt>0){try{ZoneId.of(zoneId);}catch(java.time.DateTimeException invalid){throw new IllegalArgumentException("Invalid match timezone",invalid);}}
        else if(!zoneId.isEmpty())throw new IllegalArgumentException("Timezone without a recorded time");
    }
    static String formatMoment(long millis,String zoneId,String pattern){return Instant.ofEpochMilli(millis).atZone(ZoneId.of(zoneId)).format(DateTimeFormatter.ofPattern(pattern));}
    public String heading(){return startedAt>0?formatMoment(startedAt,zoneId,"HH:mm"):"Match result";}
    public String title(){return startedAt>0?formatMoment(startedAt,zoneId,"dd.MM.yyyy · HH:mm"):LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))+" · Start not recorded";}
    public String startedText(){return startedAt>0?formatMoment(startedAt,zoneId,"dd.MM.yyyy HH:mm:ss z"):"Not recorded";}
    public String finishedText(){return finishedAt>0?formatMoment(finishedAt,zoneId,"dd.MM.yyyy HH:mm:ss z"):"Not recorded";}
    public long durationMillis(){return startedAt>0&&finishedAt>0&&finishedAt>=startedAt?finishedAt-startedAt:-1;}
    public String durationText(){
        long millis=durationMillis();
        if(millis<0)return startedAt>0&&finishedAt>0?"Unavailable (clock changed)":"Not recorded";
        long seconds=millis/1000,hours=seconds/3600,minutes=seconds/60%60;
        return hours>0?hours+" h "+minutes+" min "+seconds%60+" sec":minutes>0?minutes+" min "+seconds%60+" sec":seconds+" sec";
    }
    public String referenceText(){return "Match "+number+" · "+LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));}
    /** Compact watch display; preserve the recorded timezone and show a second date only when needed. */
    public String timingText(){
        long known=startedAt>0?startedAt:finishedAt;
        LocalDate day=known>0?Instant.ofEpochMilli(known).atZone(ZoneId.of(zoneId)).toLocalDate():LocalDate.parse(date);
        String start=startedAt>0?formatMoment(startedAt,zoneId,"HH:mm"):"Not recorded";
        String end="Not recorded";
        if(finishedAt>0){
            LocalDate endDay=Instant.ofEpochMilli(finishedAt).atZone(ZoneId.of(zoneId)).toLocalDate();
            end=formatMoment(finishedAt,zoneId,endDay.equals(day)?"HH:mm":"dd.MM.yyyy · HH:mm");
        }
        return "Date: "+day.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))+"\nStart: "+start+"\nEnd: "+end;
    }
    public String summary(){return "Me "+gamesWon(0)+"–"+gamesWon(1)+" Opponent";}
    public String gamesText(){StringBuilder out=new StringBuilder();for(int i=0;i<scores.length;i++){if(i>0)out.append('\n');out.append("Set ").append(i+1).append(": ").append(scores[i][0]).append("–").append(scores[i][1]);if(!setComplete(i))out.append(" · unfinished");}return out.toString();}
    public boolean sameScores(MatchState match){
        if(!id.equals(match.id())||bestOf!=match.bestOf()||retiredPlayer!=match.retiredPlayer()||scores.length!=match.gameCount()||!match.complete())return false;
        for(int i=0;i<scores.length;i++)if(scores[i][0]!=match.game(i).blue()||scores[i][1]!=match.game(i).red())return false;
        return true;
    }
    void write(DataOutputStream out)throws IOException{
        out.writeUTF(id);out.writeUTF(date);out.writeInt(number);out.writeInt(bestOf);out.writeInt(scores.length);
        for(int[] score:scores){out.writeInt(score[0]);out.writeInt(score[1]);}
        out.writeLong(startedAt);out.writeLong(finishedAt);out.writeUTF(zoneId);
        out.writeInt(retiredPlayer);
    }
    static MatchResult read(DataInputStream in,boolean timed,boolean retirements)throws IOException{
        String id=in.readUTF(),date=in.readUTF();int number=in.readInt(),bestOf=in.readInt(),count=in.readInt();
        if(count<(retirements?1:2)||count>5)throw new IOException("Invalid saved result game count");
        int[][] scores=new int[count][2];for(int i=0;i<count;i++){scores[i][0]=in.readInt();scores[i][1]=in.readInt();}
        long start=timed?in.readLong():0,finish=timed?in.readLong():0;String zone=timed?in.readUTF():"";
        return new MatchResult(id,date,number,bestOf,scores,start,finish,zone,retirements?in.readInt():-1);
    }
}
