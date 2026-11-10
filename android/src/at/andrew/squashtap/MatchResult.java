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
    public final MatchFormat format;
    private final int[][] details;
    public MatchResult(String id,String date,int number,int bestOf,int[][] scores){this(id,date,number,bestOf,scores,0,0,"");}
    public MatchResult(String id,String date,int number,int bestOf,int[][] scores,long startedAt,long finishedAt,String zoneId){
        this(id,date,number,bestOf,scores,startedAt,finishedAt,zoneId,-1);
    }
    public MatchResult(String id,String date,int number,int bestOf,int[][] scores,long startedAt,long finishedAt,String zoneId,int retiredPlayer){
        this(id,date,number,bestOf,scores,startedAt,finishedAt,zoneId,retiredPlayer,MatchFormat.squash(),null);
    }
    public MatchResult(String id,String date,int number,int bestOf,int[][] scores,long startedAt,long finishedAt,String zoneId,int retiredPlayer,MatchFormat format,int[][] details){
        validateTimes(startedAt,finishedAt,zoneId);format.validateBest(bestOf);this.format=format;
        this.startedAt=startedAt;this.finishedAt=finishedAt;this.zoneId=zoneId;
        if(id==null||!id.matches("[A-Za-z0-9-]{1,80}")||number<1)throw new IllegalArgumentException("Invalid result");
        if(!LocalDate.parse(date).toString().equals(date))throw new IllegalArgumentException("Invalid result date");
        if(retiredPlayer< -1||retiredPlayer>1)throw new IllegalArgumentException("Invalid retirement");
        this.retiredPlayer=retiredPlayer;
        if(scores==null||scores.length<(retired()?1:bestOf/2+1)||scores.length>bestOf)throw new IllegalArgumentException("Invalid game count");
        this.id=id;this.date=date;this.number=number;this.bestOf=bestOf;this.scores=new int[scores.length][2];this.details=new int[scores.length][6];
        if(details!=null&&details.length!=scores.length)throw new IllegalArgumentException("Invalid score details");
        int me=0,opponent=0;
        for(int i=0;i<scores.length;i++){
            if(me>=bestOf/2+1||opponent>=bestOf/2+1)throw new IllegalArgumentException("Games after match end");
            if(scores[i]==null||scores[i].length!=2)throw new IllegalArgumentException("Invalid game score");
            int a=scores[i][0],b=scores[i][1],high=Math.max(a,b),low=Math.min(a,b);
            boolean done=ScoreState.finalScore(format,isMatchTie(i),a,b);
            if(low<0||(long)a+b>ScoreState.MAX_RALLIES||!done&&(!retired()||i!=scores.length-1||!validPartial(format,isMatchTie(i),a,b)))throw new IllegalArgumentException("Invalid final game score");
            int[] d=details==null?new int[]{format.nested()?-1:a,format.nested()?-1:b,-1,-1,-1,-1}:details[i];
            if(d==null||(d.length!=4&&d.length!=6)||(d[0]<0)!=(d[1]<0)||(d[2]<0)!=(d[3]<0))throw new IllegalArgumentException("Invalid point details");
            for(int value:d)if(value< -1||value>ScoreState.MAX_RALLIES)throw new IllegalArgumentException("Invalid point detail");
            if(d[0]>=0&&(long)d[0]+d[1]>ScoreState.MAX_RALLIES)throw new IllegalArgumentException("Too many points");
            if(d[2]>=0&&(!format.nested()||isMatchTie(i)||!done||high!=7||low!=6||Math.max(d[2],d[3])<7||Math.abs(d[2]-d[3])<2||(d[2]>d[3])!=(a>b)))throw new IllegalArgumentException("Invalid tie-break details");
            if(d.length==6&&((d[4]<0)!=(d[5]<0)||d[4]>=0&&(!format.nested()||isMatchTie(i)||done)))throw new IllegalArgumentException("Invalid unfinished point score");
            this.details[i]=d.length==6?d.clone():new int[]{d[0],d[1],d[2],d[3],-1,-1};this.scores[i][0]=a;this.scores[i][1]=b;if(done){if(a>b)me++;else opponent++;}
        }
        if(retired()?(me>=bestOf/2+1||opponent>=bestOf/2+1):(me<bestOf/2+1&&opponent<bestOf/2+1))throw new IllegalArgumentException("Invalid match outcome");
    }
    public static MatchResult from(MatchState match,String date,int number){return from(match,date,number,0,0,"");}
    public static MatchResult from(MatchState match,String date,int number,long startedAt,long finishedAt,String zoneId){
        if(!match.complete())throw new IllegalArgumentException("Match is unfinished");
        int[][] scores=new int[match.gameCount()][2];
        for(int i=0;i<scores.length;i++){scores[i][0]=match.game(i).blue();scores[i][1]=match.game(i).red();}
        int[][] details=new int[scores.length][6];for(int i=0;i<scores.length;i++)details[i]=new int[]{match.game(i).totalPoints(0),match.game(i).totalPoints(1),match.game(i).tiePoints(0),match.game(i).tiePoints(1),match.game(i).currentGamePoints(0),match.game(i).currentGamePoints(1)};
        return new MatchResult(match.id(),date,number,match.bestOf(),scores,startedAt,finishedAt,zoneId,match.retiredPlayer(),match.format(),details);
    }
    public int gameCount(){return scores.length;}
    public int points(int game,int player){return scores[game][player];}
    private static boolean validPartial(MatchFormat f,boolean tie,int a,int b){
        if(a<0||b<0)return false;if(f.nested()&&!tie)return a<=6&&b<=6&&!ScoreState.finalScore(f,false,a,b);
        int target=tie?10:f.target,cap=tie?Integer.MAX_VALUE:f.cap();return Math.max(a,b)<cap&&(Math.max(a,b)<target||Math.abs(a-b)<2);
    }
    public boolean isMatchTie(int index){return format.finalTie&&index==bestOf-1;}
    public int rallyPoints(int set,int side){return details[set][side];}
    public int unfinishedPoints(int set,int side){return details[set][side+4];}
    public int tiePoints(int set,int side){return details[set][side+2];}
    public boolean setComplete(int game){return ScoreState.finalScore(format,isMatchTie(game),scores[game][0],scores[game][1]);}
    public MatchResult renumber(int value){return new MatchResult(id,date,value,bestOf,scores,startedAt,finishedAt,zoneId,retiredPlayer,format,details);}
    public boolean retired(){return retiredPlayer!=-1;}
    public int gamesWon(int player){int count=0;for(int i=0;i<scores.length;i++)if(setComplete(i)&&(scores[i][0]>scores[i][1]?0:1)==player)count++;return count;}
    public int winner(){return retired()?1-retiredPlayer:gamesWon(0)>gamesWon(1)?0:1;}
    public String outcomeText(){if(format.doubles)return retired()?(retiredPlayer==0?"Our team retired · Opposing team won":"Opposing team retired · Our team won"):winner()==0?"Our team won":"Opposing team won";return retired()?(retiredPlayer==0?"Me retired · Opponent won":"Opponent retired · You won"):winner()==0?"You won":"Opponent won";}
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
    public String gamesText(){StringBuilder out=new StringBuilder();for(int i=0;i<scores.length;i++){if(i>0)out.append('\n');out.append(isMatchTie(i)?"Match tie-break ":format.unit()+" ").append(i+1).append(": ").append(scores[i][0]).append("–").append(scores[i][1]);if(tiePoints(i,0)>=0)out.append(" (TB ").append(tiePoints(i,0)).append("–").append(tiePoints(i,1)).append(')');if(!setComplete(i)){out.append(" · unfinished");if(unfinishedPoints(i,0)>=0)out.append(" · points ").append(unfinishedPointText(i,0)).append("–").append(unfinishedPointText(i,1));}}return out.toString();}
    private String unfinishedPointText(int index,int side){int a=unfinishedPoints(index,side),b=unfinishedPoints(index,1-side);if(scores[index][0]==6&&scores[index][1]==6)return Integer.toString(a);if(a>=3&&b>=3)return a>b?"AD":"40";return new String[]{"0","15","30","40"}[Math.min(a,3)];}
    public boolean sameScores(MatchState match){
        if(!format.equals(match.format())||!id.equals(match.id())||bestOf!=match.bestOf()||retiredPlayer!=match.retiredPlayer()||scores.length!=match.gameCount()||!match.complete())return false;
        for(int i=0;i<scores.length;i++)if(scores[i][0]!=match.game(i).blue()||scores[i][1]!=match.game(i).red())return false;
        return true;
    }
    void writeExtended(DataOutputStream out)throws IOException{
        write(out);out.writeUTF(format.encode());for(int[] d:details)for(int n:d)out.writeInt(n);
    }
    void write(DataOutputStream out)throws IOException{
        out.writeUTF(id);out.writeUTF(date);out.writeInt(number);out.writeInt(bestOf);out.writeInt(scores.length);
        for(int[] score:scores){out.writeInt(score[0]);out.writeInt(score[1]);}
        out.writeLong(startedAt);out.writeLong(finishedAt);out.writeUTF(zoneId);
        out.writeInt(retiredPlayer);
    }
    static MatchResult read(DataInputStream in,boolean timed,boolean retirements)throws IOException{return read(in,timed,retirements,false);}
    static MatchResult read(DataInputStream in,boolean timed,boolean retirements,boolean multi)throws IOException{
        String id=in.readUTF(),date=in.readUTF();int number=in.readInt(),bestOf=in.readInt(),count=in.readInt();
        if(count<(retirements?1:2)||count>(multi?7:5))throw new IOException("Invalid saved result game count");
        int[][] scores=new int[count][2];for(int i=0;i<count;i++){scores[i][0]=in.readInt();scores[i][1]=in.readInt();}
        long start=timed?in.readLong():0,finish=timed?in.readLong():0;String zone=timed?in.readUTF():"";
        int retired=retirements?in.readInt():-1;MatchFormat f=multi?MatchFormat.decode(in.readUTF()):MatchFormat.squash();int[][] d=null;
        if(multi){d=new int[count][6];for(int i=0;i<count;i++)for(int j=0;j<6;j++)d[i][j]=in.readInt();}
        return new MatchResult(id,date,number,bestOf,scores,start,finish,zone,retired,f,d);
    }
}
