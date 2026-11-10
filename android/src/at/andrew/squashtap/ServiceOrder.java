package at.andrew.squashtap;

/** Derives the next server from real scoring, without recording serve attempts or lets.
 * Slots: Me=0, opponent=1, partner=2, other opponent=3; 4/5 mean team only. */
public final class ServiceOrder {
    private ServiceOrder(){}
    public static int partner(int slot){return slot<0?-1:slot^2;}
    public static int next(MatchState match){
        MatchFormat f=match.format();int index=match.gameCount()-1;ScoreState game=match.game();
        int first=match.firstServer(index),receiver=match.firstReceiver(index);
        if(f.sport.equals("squash")||f.sport.equals("badminton")){
            int side=game.lastRallyWinner();
            if(side<0&&index>0)side=match.game(index-1).winner();
            if(!f.doubles)return side>=0?side:first;
            if(first<0)return side>=0?side+4:-1;
            if(receiver<0)return (side>=0?side:first%2)+4;
            int[] right=new int[2],left=new int[2],scores=new int[2];right[first%2]=first;left[first%2]=partner(first);right[receiver%2]=receiver;left[receiver%2]=partner(receiver);
            int server=first;String rallies=game.rallyHistory();
            if(game.entered())return game.winner()+4;
            for(int i=0;i<rallies.length();i++){
                int won=rallies.charAt(i)=='B'?0:1;scores[won]++;
                if(server%2==won){int swap=right[won];right[won]=left[won];left[won]=swap;}
                else server=scores[won]%2==0?right[won]:left[won];
            }
            return server;
        }
        if(first<0)return -1;
        if(f.sport.equals("table-tennis")){
            if(f.doubles&&receiver<0){int count=game.rallies(),step=count<20?count/2:10+count-20;return step==0?first:4+(first%2+step)%2;}
            int server=first,receive=f.doubles?receiver:1-first,a=0,b=0;
            if(game.entered())return -1;
            String rallies=game.rallyHistory();boolean flipped=false;
            for(int i=0;i<rallies.length();i++){
                if(rallies.charAt(i)=='B')a++;else b++;
                int count=i+1;if(count>=20||count%2==0){int previous=server;server=receive;receive=f.doubles?partner(previous):previous;}
                if(f.doubles&&index==match.bestOf()-1&&!flipped&&Math.max(a,b)>=5){receive=partner(receive);flipped=true;}
            }
            return server;
        }
        int step=game.serviceStep();
        if(f.doubles&&receiver<0)return step==0?first:4+(first%2+step)%2;
        if(!f.doubles)return (first+step)%2;
        return new int[]{first,receiver,partner(first),partner(receiver)}[step%4];
    }
}
