package at.andrew.squashtap;

import java.util.ArrayList;
import java.util.List;

/** Best-of-three/five match, retaining every game's rally history for Undo. */
public final class MatchState {
    private final String id;
    private int bestOf;
    private final MatchFormat format;
    private int retiredPlayer=-1;
    private final int[] firstServers={-1,-1,-1,-1,-1,-1,-1},firstReceivers={-1,-1,-1,-1,-1,-1,-1};
    private final List<ScoreState> games=new ArrayList<>();
    public MatchState(String id,int bestOf){this(id,bestOf,MatchFormat.squash());}
    public MatchState(String id,int bestOf,MatchFormat format){
        if(id==null||!id.matches("[A-Za-z0-9-]{1,80}"))throw new IllegalArgumentException("Invalid match identifier");
        format.validateBest(bestOf);
        this.id=id;this.bestOf=bestOf;this.format=format;games.add(new ScoreState(format,false));
    }
    public String id(){return id;}
    public MatchFormat format(){return format;}
    public int firstServer(int index){return firstServers[index];}
    public int firstReceiver(int index){return firstReceivers[index];}
    public void serving(int server,int receiver){int max=format.doubles?3:1;if(server< -1||server>max||receiver< -1||receiver>max||server>=0&&receiver>=0&&server%2==receiver%2)throw new IllegalArgumentException("Choose players on opposite teams");firstServers[games.size()-1]=server;firstReceivers[games.size()-1]=receiver;}
    public int server(){return ServiceOrder.next(this);}
    private boolean hasService(){for(int n:firstServers)if(n>=0)return true;return false;}
    public boolean matchTie(int index){return format.finalTie&&index==bestOf-1;}
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
        ScoreState entered=ScoreState.completedSet(format,matchTie(nextReady()?games.size():games.size()-1),me,opponent);if(complete())return false;
        if(nextReady())games.add(entered);else games.set(games.size()-1,entered);return true;
    }
    public boolean canChangeBestOf(int value){
        if(format.finalTie&&hasPoints())return false;
        boolean allowed=false;for(int option:format.bestOptions())if(value==option)allowed=true;
        return allowed&&value!=bestOf&&!complete()
            &&games.size()<=value&&gamesWon(0)<value/2+1&&gamesWon(1)<value/2+1;
    }
    public boolean changeBestOf(int value){
        format.validateBest(value);
        if(format.finalTie&&hasPoints())return false;
        if(!canChangeBestOf(value))return false;
        bestOf=value;return true;
    }
    public boolean add(int player){return !complete()&&game().add(player);}
    public boolean nextGame(){if(!nextReady())return false;int index=games.size();
        if(!format.doubles&&format.sport.equals("badminton"))firstServers[index]=game().winner();
        else if(format.sport.equals("table-tennis")&&firstServers[index-1]>=0){if(!format.doubles)firstServers[index]=1-firstServers[index-1];else if(firstReceivers[index-1]>=0){firstServers[index]=firstReceivers[index-1];firstReceivers[index]=firstServers[index-1];}}
        else if(format.nested()&&firstServers[index-1]>=0){int step=game().serviceStep();int old=firstServers[index-1],receive=firstReceivers[index-1];if(format.doubles&&receive>=0){int[] order={old,receive,ServiceOrder.partner(old),ServiceOrder.partner(receive)};firstServers[index]=order[step%4];firstReceivers[index]=order[(step+1)%4];}else if(!format.doubles)firstServers[index]=(old+step)%2;}
        games.add(new ScoreState(format,matchTie(index)));return true;}
    public boolean undo(){
        if(retired()){retiredPlayer=-1;return true;}
        if(game().rallies()==0&&games.size()>1)games.remove(games.size()-1);
        if(game().entered()){games.set(games.size()-1,new ScoreState(format,matchTie(games.size()-1)));return true;}
        return game().undo();
    }
    public String encode(){
        if(!format.legacy()||hasService()){StringBuilder out=new StringBuilder("M4|").append(id).append('|').append(bestOf).append('|').append(retiredPlayer).append('|').append(format.encode()).append('|');for(int i=0;i<7;i++){if(i>0)out.append(',');out.append(firstServers[i]).append(',').append(firstReceivers[i]);}out.append('|');for(int i=0;i<games.size();i++){if(i>0)out.append('~');out.append(games.get(i).encode());}return out.toString();}
        boolean entered=hasEnteredSets();StringBuilder out=new StringBuilder(entered?"M3|":retired()?"M2|":"M1|").append(id).append('|').append(bestOf).append('|');
        if(entered||retired())out.append(retiredPlayer).append('|');
        for(int i=0;i<games.size();i++){if(i>0)out.append('~');out.append(games.get(i).encode());}
        return out.toString();
    }
    public static MatchState decode(String text){
        if(text!=null&&text.startsWith("M4|")){
            if(text.length()>75000)throw new IllegalArgumentException("Oversized match");
            String[] p=text.split("\\|",7);if(p.length!=7)throw new IllegalArgumentException("Invalid match");
            MatchState m=new MatchState(p[1],Integer.parseInt(p[2]),MatchFormat.decode(p[4]));m.games.clear();
            String[] service=p[5].split(",",-1);if(service.length!=14)throw new IllegalArgumentException("Invalid serving setup");for(int i=0;i<7;i++){int a=Integer.parseInt(service[i*2]),b=Integer.parseInt(service[i*2+1]),max=m.format.doubles?3:1;if(a< -1||a>max||b< -1||b>max||a>=0&&b>=0&&a%2==b%2)throw new IllegalArgumentException("Invalid serving setup");m.firstServers[i]=a;m.firstReceivers[i]=b;}
            String[] states=p[6].split("~",-1);if(states.length<1||states.length>m.bestOf)throw new IllegalArgumentException("Invalid set count");
            for(String item:states){if(!m.games.isEmpty()&&(m.complete()||m.game().winner()==-1))throw new IllegalArgumentException("Invalid set progression");ScoreState state=ScoreState.decode(item);if(!state.uses(m.format,m.matchTie(m.games.size())))throw new IllegalArgumentException("Inconsistent set rules");m.games.add(state);}
            int retired=Integer.parseInt(p[3]);if(retired< -1||retired>1||retired!=-1&&!m.retire(retired))throw new IllegalArgumentException("Invalid retirement");return m;
        }
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
