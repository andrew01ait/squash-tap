package at.andrew.squashtap;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Unsaved score-only draft; historical entry never replaces the live match or invents timing. */
public final class ManualMatchEntry {
    private int bestOf=3;
    public final MatchFormat format;
    public String partnerId="",otherId="";
    private LocalDate date;
    private String opponentId="";
    private final List<int[]> scores=new ArrayList<>();
    public ManualMatchEntry(LocalDate today){this(today,MatchFormat.squash());}
    public ManualMatchEntry(LocalDate today,MatchFormat format){date=java.util.Objects.requireNonNull(today);this.format=format;}
    public boolean matchTie(int index){return format.finalTie&&index==bestOf-1;}
    public static int winningScore(MatchFormat format,boolean tie,int loser){
        int target=tie?10:format.target;if(loser<0||format.nested()&&!tie&&loser>6||!format.nested()&&loser>=format.cap())throw new IllegalArgumentException("Invalid losing score");
        return format.nested()&&!tie?(loser<=4?6:7):Math.min(tie?Integer.MAX_VALUE:format.cap(),loser<target-1?target:loser+2);
    }
    public static int winningScore(int loser){if(loser<0||loser>(ScoreState.MAX_RALLIES-2)/2)throw new IllegalArgumentException("Invalid losing score");return loser<10?11:loser+2;}
    public static LocalDate changeDate(LocalDate base,int year,int month,int day){return LocalDate.of(year,month,Math.min(day,YearMonth.of(year,month).lengthOfMonth()));}
    public int bestOf(){return bestOf;}
    public int size(){return scores.size();}
    public int points(int set,int player){return scores.get(set)[player];}
    public LocalDate date(){return date;}
    public void date(LocalDate value,LocalDate today){if(value==null||value.isAfter(today)||value.getYear()<1900)throw new IllegalArgumentException("Choose today or an earlier match date");date=value;}
    public String opponentId(){return opponentId;}
    public void opponentId(String id){opponentId=java.util.Objects.requireNonNull(id);}
    private MatchState validate(int best,List<int[]> values){MatchState m=new MatchState("manual-draft",best,format);for(int[] s:values)if(!m.enterCompletedSet(s[0],s[1]))throw new IllegalArgumentException("These scores would finish the match before a later set");return m;}
    public boolean canChooseFormat(int value){try{validate(value,scores);return true;}catch(IllegalArgumentException invalid){return false;}}
    public void chooseFormat(int value){validate(value,scores);bestOf=value;}
    public boolean complete(){return validate(bestOf,scores).complete();}
    public void setScore(int index,int me,int opponent){
        if(index<0||index>scores.size())throw new IllegalArgumentException("Invalid set");
        List<int[]> next=new ArrayList<>(scores);int[] score={me,opponent};if(index==next.size())next.add(score);else next.set(index,score);validate(bestOf,next);scores.clear();scores.addAll(next);
    }
    public void removeLast(){if(!scores.isEmpty())scores.remove(scores.size()-1);}
    public MatchResult result(String id){if(!complete())throw new IllegalArgumentException("Enter all sets of the completed match first");return new MatchResult(id,date.toString(),1,bestOf,scores.toArray(new int[0][]),0,0,"",-1,format,null);}
}
