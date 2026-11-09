package at.andrew.squashtap;

import java.util.Objects;

/** Derived, read-only Me totals from saved completed results; never persists a second counter. */
public final class MatchStatistics {
    public final long matchesWon,matchesLost,setsWon,setsLost,pointsFor,pointsAgainst;
    private MatchStatistics(long wins,long losses,long setsFor,long setsAgainst,long pointsFor,long pointsAgainst){
        this.matchesWon=wins;this.matchesLost=losses;this.setsWon=setsFor;this.setsLost=setsAgainst;
        this.pointsFor=pointsFor;this.pointsAgainst=pointsAgainst;
    }
    public static MatchStatistics from(Iterable<MatchResult> results){
        long wins=0,losses=0,setsFor=0,setsAgainst=0,pointsFor=0,pointsAgainst=0;
        for(MatchResult result:Objects.requireNonNull(results)){
            if(result.winner()==0)wins++;else losses++;
            setsFor+=result.gamesWon(0);setsAgainst+=result.gamesWon(1);
            for(int game=0;game<result.gameCount();game++){
                pointsFor+=result.points(game,0);pointsAgainst+=result.points(game,1);
            }
        }
        return new MatchStatistics(wins,losses,setsFor,setsAgainst,pointsFor,pointsAgainst);
    }
    public long matchesPlayed(){return matchesWon+matchesLost;}
    public long setsPlayed(){return setsWon+setsLost;}
    public long pointsPlayed(){return pointsFor+pointsAgainst;}
    public long matchBalance(){return matchesWon-matchesLost;}
    public long setBalance(){return setsWon-setsLost;}
    public long pointBalance(){return pointsFor-pointsAgainst;}
    public double matchWinRate(){return matchesPlayed()==0?0:100.0*matchesWon/matchesPlayed();}
}
