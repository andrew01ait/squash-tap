package at.andrew.squashtap;

/** USAP traditional doubles rotation and the explicitly selected 2025/2026 rally variants. */
public final class PickleballGame {
    private final MatchFormat format;
    private final int[] points={0,0},right=new int[2];
    private int server,number;
    public PickleballGame(MatchFormat f,int first,int other){
        if(!f.pickleball()||first<0||first>(f.doubles?3:1)||other<0||other>(f.doubles?3:1)||first%2==other%2)throw new IllegalArgumentException("Choose the starting player on each team");
        format=f;server=first;right[first%2]=first;right[other%2]=other;number=f.sideOut()&&f.doubles?2:1;
    }
    public int score(int side){return points[side];}
    public int server(){return server;}
    public int serverNumber(){return number;}
    public String court(){return format.doubles?(server==right[server%2]?"RIGHT":"LEFT"):points[server%2]%2==0?"RIGHT":"LEFT";}
    public void add(int won){
        if(won<0||won>1)throw new IllegalArgumentException("Unknown rally winner");
        int serving=server%2;
        boolean point=!format.sideOut()||won==serving;
        if(format.deuce.equals("rally-2025")&&won!=serving&&points[won]+1>=format.target&&points[won]+1-points[1-won]>=2)point=false;
        if(point){points[won]++;if(format.doubles)right[won]=ServiceOrder.partner(right[won]);}
        if(won==serving)return;
        if(format.sideOut()&&format.doubles&&number==1){server=ServiceOrder.partner(server);number=2;}
        else{server=format.doubles?right[won]:won;number=1;}
    }
    public String call(){int side=server%2;return points[side]+"–"+points[1-side]+(format.sideOut()&&format.doubles?"–"+number:"");}
}
