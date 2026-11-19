package at.andrew.squashtap;

import java.util.Objects;

/** Derived, read-only Me totals from saved completed results; never persists a second counter. */
public final class MatchStatistics {
    public final long matchesWon,matchesLost,setsWon,setsLost,pointsFor,pointsAgainst;
    public long gamesFor,gamesAgainst,unknownPointMatches;
    private MatchStatistics(long wins,long losses,long setsFor,long setsAgainst,long pointsFor,long pointsAgainst){
        this.matchesWon=wins;this.matchesLost=losses;this.setsWon=setsFor;this.setsLost=setsAgainst;
        this.pointsFor=pointsFor;this.pointsAgainst=pointsAgainst;
    }
    public static MatchStatistics from(Iterable<MatchResult> results){
        long wins=0,losses=0,setsFor=0,setsAgainst=0,pointsFor=0,pointsAgainst=0,gamesFor=0,gamesAgainst=0,unknown=0;
        for(MatchResult result:Objects.requireNonNull(results)){
            if(result.winner()==0)wins++;else losses++;
            setsFor+=result.gamesWon(0);setsAgainst+=result.gamesWon(1);
            boolean missing=false;for(int game=0;game<result.gameCount();game++){
                if(result.rallyPoints(game,0)>=0){pointsFor+=result.rallyPoints(game,0);pointsAgainst+=result.rallyPoints(game,1);}else missing=true;
                if(result.format.nested()&&!result.isMatchTie(game)){gamesFor+=result.points(game,0);gamesAgainst+=result.points(game,1);}
            }if(missing)unknown++;
        }
        MatchStatistics totals=new MatchStatistics(wins,losses,setsFor,setsAgainst,pointsFor,pointsAgainst);totals.gamesFor=gamesFor;totals.gamesAgainst=gamesAgainst;totals.unknownPointMatches=unknown;return totals;
    }
    public long matchesPlayed(){return matchesWon+matchesLost;}
    public long setsPlayed(){return setsWon+setsLost;}
    public long pointsPlayed(){return pointsFor+pointsAgainst;}
    public long matchBalance(){return matchesWon-matchesLost;}
    public long setBalance(){return setsWon-setsLost;}
    public long pointBalance(){return pointsFor-pointsAgainst;}
    public double matchWinRate(){return matchesPlayed()==0?0:100.0*matchesWon/matchesPlayed();}
}
