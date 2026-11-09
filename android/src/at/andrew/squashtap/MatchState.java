package at.andrew.squashtap;

import java.util.ArrayList;
import java.util.List;

/** Best-of-three/five match, retaining every game's rally history for Undo. */
public final class MatchState {
    private final String id;
    private int bestOf;
    private int retiredPlayer=-1;
    private final List<ScoreState> games=new ArrayList<>();
    public MatchState(String id,int bestOf){
        if(id==null||!id.matches("[A-Za-z0-9-]{1,80}"))throw new IllegalArgumentException("Invalid match identifier");
        if(bestOf!=3&&bestOf!=5)throw new IllegalArgumentException("Choose best of 3 or 5");
        this.id=id;this.bestOf=bestOf;games.add(new ScoreState());
    }
    public String id(){return id;}
    public int bestOf(){return bestOf;}
    public int needed(){return bestOf/2+1;}
    public int gameCount(){return games.size();}
    public ScoreState game(){return games.get(games.size()-1);}
    public ScoreState game(int index){return games.get(index);}
    public int gamesWon(int player){int count=0;for(ScoreState game:games)if(game.winner()==player)count++;return count;}
    public int retiredPlayer(){return retiredPlayer;}
    public boolean retired(){return retiredPlayer!=-1;}
    public boolean retire(int player){
        if(player!=0&&player!=1)throw new IllegalArgumentException("Choose the player who quit");
        if(complete())return false;retiredPlayer=player;return true;
    }
    public int winner(){if(retired())return 1-retiredPlayer;if(gamesWon(0)>=needed())return 0;if(gamesWon(1)>=needed())return 1;return -1;}
    public boolean complete(){return winner()!=-1;}
    public boolean nextReady(){return !complete()&&game().winner()!=-1;}
    public boolean hasPoints(){return game().rallies()>0||games.size()>1;}
    public boolean hasEnteredSets(){for(ScoreState game:games)if(game.entered())return true;return false;}
    public boolean undoEnteredSet(){return !retired()&&(game().entered()||game().rallies()==0&&games.size()>1&&games.get(games.size()-2).entered());}
    public boolean enterCompletedSet(int me,int opponent){
        ScoreState entered=ScoreState.completedSet(me,opponent);if(complete())return false;
        if(nextReady())games.add(entered);else games.set(games.size()-1,entered);return true;
    }
    public boolean canChangeBestOf(int value){
        return (value==3||value==5)&&value!=bestOf&&!complete()
            &&games.size()<=value&&gamesWon(0)<value/2+1&&gamesWon(1)<value/2+1;
    }
    public boolean changeBestOf(int value){
        if(value!=3&&value!=5)throw new IllegalArgumentException("Choose best of 3 or 5");
        if(!canChangeBestOf(value))return false;
        bestOf=value;return true;
    }
    public boolean add(int player){return !complete()&&game().add(player);}
    public boolean nextGame(){if(!nextReady())return false;games.add(new ScoreState());return true;}
    public boolean undo(){
        if(retired()){retiredPlayer=-1;return true;}
        if(game().rallies()==0&&games.size()>1)games.remove(games.size()-1);
        if(game().entered()){games.set(games.size()-1,new ScoreState());return true;}
        return game().undo();
    }
    public String encode(){
        boolean entered=hasEnteredSets();StringBuilder out=new StringBuilder(entered?"M3|":retired()?"M2|":"M1|").append(id).append('|').append(bestOf).append('|');
        if(entered||retired())out.append(retiredPlayer).append('|');
        for(int i=0;i<games.size();i++){if(i>0)out.append('~');out.append(games.get(i).encode());}
        return out.toString();
    }
    public static MatchState decode(String text){
        if(text==null||text.length()>51000)throw new IllegalArgumentException("Invalid saved match");
        boolean entered=text.startsWith("M3|"),retired=text.startsWith("M2|"),extended=entered||retired;String[] parts=text.split("\\|",extended?5:4);
        if(parts.length!=(extended?5:4)||!parts[0].equals(entered?"M3":retired?"M2":"M1"))throw new IllegalArgumentException("Unknown match format");
        MatchState match=new MatchState(parts[1],Integer.parseInt(parts[2]));match.games.clear();
        String[] saved=parts[extended?4:3].split("~",-1);
        if(saved.length<1||saved.length>match.bestOf)throw new IllegalArgumentException("Invalid number of games");
        for(String item:saved){
            if(!match.games.isEmpty()&&(match.complete()||match.game().winner()==-1))throw new IllegalArgumentException("Invalid game progression");
            match.games.add(ScoreState.decode(item));
        }
        if(entered!=match.hasEnteredSets())throw new IllegalArgumentException("Entered scores require the M3 format");
        int quitter=extended?Integer.parseInt(parts[3]):-1;
        if(quitter< -1||quitter>1||retired&&quitter==-1)throw new IllegalArgumentException("Invalid retired player");
        if(quitter!=-1&&!match.retire(quitter))throw new IllegalArgumentException("Cannot retire an already completed match");
        return match;
    }
    public static MatchState fromLegacy(String id,String savedScore){
        MatchState match=new MatchState(id,3);match.games.set(0,ScoreState.decode(savedScore));return match;
    }
}
