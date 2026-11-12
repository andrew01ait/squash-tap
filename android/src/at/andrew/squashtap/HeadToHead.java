package at.andrew.squashtap;

/** Previous meetings of the same saved players, in the current sport and mode. */
public final class HeadToHead {
    public final int won,lost;
    public final MatchResult last;
    private HeadToHead(int won,int lost,MatchResult last){this.won=won;this.lost=lost;this.last=last;}
    public static HeadToHead from(MatchState current,Iterable<MatchResult> results,OpponentDirectory directory){
        String first=directory.roleId(current.id(),0),partner=directory.roleId(current.id(),1),second=directory.roleId(current.id(),2);
        if(first.isEmpty()||current.format().doubles&&(partner.isEmpty()||second.isEmpty()))return null;
        int won=0,lost=0;MatchResult last=null;
        for(MatchResult result:results){
            if(result.id.equals(current.id())||!result.format.sport.equals(current.format().sport)||result.format.doubles!=current.format().doubles)continue;
            String a=directory.roleId(result.id,0),b=directory.roleId(result.id,2);
            if(current.format().doubles){if(!partner.equals(directory.roleId(result.id,1))||!(first.equals(a)&&second.equals(b)||first.equals(b)&&second.equals(a)))continue;}
            else if(!first.equals(a))continue;
            if(result.winner()==0)won++;else lost++;
            if(last==null||compare(result,last)>0)last=result;
        }
        return last==null?null:new HeadToHead(won,lost,last);
    }
    private static int compare(MatchResult a,MatchResult b){int day=a.date.compareTo(b.date);if(day!=0)return day;if(a.startedAt>0&&b.startedAt>0&&a.startedAt!=b.startedAt)return Long.compare(a.startedAt,b.startedAt);return Integer.compare(a.number,b.number);}
}
