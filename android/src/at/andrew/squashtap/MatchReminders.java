package at.andrew.squashtap;

/** Non-blocking sporting prompts; computed from transitions, so Undo can safely cross them. */
public final class MatchReminders {
    private MatchReminders(){}
    public static String transition(MatchState before,MatchState after){
        if(!before.id().equals(after.id())||after.complete()||before.gameCount()!=after.gameCount()||before.game().entered()||after.game().entered())return "";
        ScoreState a=before.game(),b=after.game();MatchFormat f=after.format();
        if(a.winner()<0&&b.winner()>=0)return f.sport.equals("squash")?"INTERVAL":f.nested()&&(b.blue()+b.red())%2==0?"SET INTERVAL":"CHANGE ENDS · INTERVAL";
        int old=Math.max(a.blue(),a.red()),now=Math.max(b.blue(),b.red());
        if(f.sport.equals("badminton")){int point=f.target==21?11:8;if(old<point&&now>=point)return after.gameCount()==after.bestOf()?"INTERVAL · CHANGE ENDS":"INTERVAL";}
        if(f.sport.equals("table-tennis")&&after.gameCount()==after.bestOf()&&old<5&&now>=5)return "CHANGE ENDS";
        if(f.pickleball()&&after.gameCount()==after.bestOf()){int point=f.target==11?6:f.target==15?8:11;if(old<point&&now>=point)return "CHANGE ENDS";}
        if(f.nested()){
            if(b.matchTie()){if(b.rallies()>a.rallies()&&b.rallies()%6==0)return "CHANGE ENDS";return "";}
            int games=a.blue()+a.red(),next=b.blue()+b.red();if(games!=next&&next%2==1)return "CHANGE ENDS";
            if(b.blue()==6&&b.red()==6&&b.rallies()>a.rallies()){int points=b.currentGamePoints(0)+b.currentGamePoints(1);if(points>0&&points%6==0)return "CHANGE ENDS";}
        }
        return "";
    }
    public static String milestone(MatchState before,MatchState after){if(after.complete()&&!before.complete())return "match";if(after.game().winner()>=0&&before.game().winner()<0)return "set";if(after.format().nested()&&after.game().blue()+after.game().red()!=before.game().blue()+before.game().red())return "game";return "point";}
}
