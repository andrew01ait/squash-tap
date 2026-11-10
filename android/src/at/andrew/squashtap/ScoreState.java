package at.andrew.squashtap;

/** A rally-scored game or tennis/padel set, retaining actual rally order. */
public final class ScoreState {
    public static final int BLUE=0,RED=1,NONE=-1,MAX_RALLIES=10000;
    private final StringBuilder history=new StringBuilder();
    private final MatchFormat format;
    private final boolean matchTie;
    private int blue,red,pb,pr,totalBlue,totalRed,deuces,tb=-1,tr=-1;
    private boolean entered;
    public ScoreState(){this(MatchFormat.squash(),false);}
    public ScoreState(MatchFormat format,boolean matchTie){this.format=format;this.matchTie=matchTie;if(matchTie&&!format.nested())throw new IllegalArgumentException("Invalid match tie-break");}
    public int blue(){return blue;} public int red(){return red;}
    public int rallies(){return entered?format.nested()?1:blue+red:history.length();}
    public boolean entered(){return entered;}
    public boolean matchTie(){return matchTie;}
    public int currentGamePoints(int side){return format.nested()&&!matchTie&&!entered&&winner()==NONE?(side==0?pb:pr):-1;}
    public int tiePoints(int side){return side==0?tb:tr;}
    public int totalPoints(int side){return entered?(format.nested()?-1:side==0?blue:red):side==0?totalBlue:totalRed;}
    public static ScoreState completedSet(int a,int b){return completedSet(MatchFormat.squash(),false,a,b);}
    public static ScoreState completedSet(MatchFormat f,boolean tie,int a,int b){
        if(!finalScore(f,tie,a,b))throw new IllegalArgumentException("Enter a valid final "+f.unit().toLowerCase()+" score");
        ScoreState s=new ScoreState(f,tie);s.blue=a;s.red=b;s.entered=true;return s;
    }
    public static boolean finalScore(MatchFormat f,boolean tie,int a,int b){
        int high=Math.max(a,b),low=Math.min(a,b);if(low<0||(long)a+b>MAX_RALLIES)return false;
        if(tie)return low<9?high==10:high==low+2;
        if(f.nested())return high==6&&low<=4||high==7&&(low==5||low==6);
        return high==f.cap()&&low==high-1||(low<f.target-1?high==f.target:high==low+2&&high<=f.cap());
    }
    private boolean tieBreak(){return matchTie||format.nested()&&blue==6&&red==6;}
    public int winner(){
        if(entered)return blue>red?BLUE:RED;
        if(format.nested()&&!matchTie){if((blue>=6&&blue-red>=2)||blue==7)return BLUE;if((red>=6&&red-blue>=2)||red==7)return RED;return NONE;}
        int target=matchTie?10:format.target,cap=matchTie?Integer.MAX_VALUE:format.cap();
        if(Math.max(blue,red)<target||Math.abs(blue-red)<2&&Math.max(blue,red)<cap)return NONE;return blue>red?BLUE:RED;
    }
    public boolean deuce(){return winner()==NONE&&(format.nested()&&!matchTie&&!tieBreak()?pb>=3&&pr>=3:blue>=((matchTie?10:format.target)-1)&&red>=((matchTie?10:format.target)-1));}
    public String displayPoint(int side){
        if(!format.nested()||matchTie||entered)return Integer.toString(side==0?blue:red);
        if(tieBreak())return Integer.toString(side==0?pb:pr);
        int a=side==0?pb:pr,b=side==0?pr:pb;if(a>=3&&b>=3)return a>b?"AD":"40";
        return new String[]{"0","15","30","40"}[Math.min(a,3)];
    }
    public String status(){
        if(matchTie)return "MATCH TIE-BREAK · TO 10";
        if(format.nested())return tieBreak()?"TIE-BREAK · TO 7":pb>=3&&pr>=3?(format.deuce.equals("no-ad")||format.deuce.equals("star")&&deuces>=3?"DECIDING POINT":format.deuce.equals("star")?"DEUCE "+deuces:"DEUCE / ADVANTAGE"):"GAMES "+blue+"–"+red;
        return "TO "+format.target+" · WIN BY TWO"+(format.sport.equals("badminton")?" · CAP "+format.cap():"");
    }
    public boolean add(int player){
        if(player!=BLUE&&player!=RED)throw new IllegalArgumentException("Unknown player");
        if(winner()!=NONE||history.length()>=MAX_RALLIES)return false;
        history.append(player==BLUE?'B':'R');if(player==0)totalBlue++;else totalRed++;
        if(!format.nested()||matchTie){if(player==0)blue++;else red++;return true;}
        if(player==0)pb++;else pr++;
        if(tieBreak()){
            if(Math.max(pb,pr)>=7&&Math.abs(pb-pr)>=2){tb=pb;tr=pr;if(pb>pr)blue++;else red++;}
        }else{
            if(pb==pr&&pb>=3)deuces++;
            boolean deciding=format.deuce.equals("no-ad")||format.deuce.equals("star")&&deuces>=3;
            if(Math.max(pb,pr)>=4&&(Math.abs(pb-pr)>=2||deciding&&pb!=pr)){
                if(pb>pr)blue++;else red++;pb=pr=deuces=0;
            }
        }
        return true;
    }
    public boolean undo(){if(entered||history.length()==0)return false;String saved=history.substring(0,history.length()-1);reset();for(int i=0;i<saved.length();i++)add(saved.charAt(i)=='B'?BLUE:RED);return true;}
    public void reset(){history.setLength(0);blue=red=pb=pr=totalBlue=totalRed=deuces=0;tb=tr=-1;entered=false;}
    public String encode(){
        if(format.legacy())return entered?"2|"+blue+"|"+red:"1|"+history;
        return "3|"+format.encode()+"|"+(matchTie?1:0)+"|"+(entered?"E"+blue+","+red:"L"+history);
    }
    public static ScoreState decode(String encoded){
        if(encoded!=null&&encoded.startsWith("3|")){
            String[] p=encoded.split("\\|",-1);if(p.length!=4||!p[2].matches("[01]")||p[3].length()<1)throw new IllegalArgumentException("Invalid saved score");
            MatchFormat f=MatchFormat.decode(p[1]);boolean tie=p[2].equals("1");
            if(p[3].startsWith("E")){String[] v=p[3].substring(1).split(",",-1);if(v.length!=2)throw new IllegalArgumentException("Invalid entered score");return completedSet(f,tie,Integer.parseInt(v[0]),Integer.parseInt(v[1]));}
            if(!p[3].startsWith("L"))throw new IllegalArgumentException("Invalid rally encoding");return replay(new ScoreState(f,tie),p[3].substring(1));
        }
        if(encoded!=null&&encoded.startsWith("2|")){if(encoded.length()>16)throw new IllegalArgumentException("Invalid entered score");String[] p=encoded.split("\\|",-1);if(p.length!=3)throw new IllegalArgumentException("Invalid entered score");return completedSet(Integer.parseInt(p[1]),Integer.parseInt(p[2]));}
        if(encoded==null||!encoded.startsWith("1|"))throw new IllegalArgumentException("Invalid saved score");return replay(new ScoreState(),encoded.substring(2));
    }
    private static ScoreState replay(ScoreState s,String rallies){if(rallies.length()>MAX_RALLIES)throw new IllegalArgumentException("Too many rallies");for(int i=0;i<rallies.length();i++){char p=rallies.charAt(i);if(p!='B'&&p!='R'||!s.add(p=='B'?BLUE:RED))throw new IllegalArgumentException("Invalid rally progression");}return s;}
    public String rallyHistory(){return history.toString();}
    public int lastRallyWinner(){return entered?winner():history.length()==0?-1:history.charAt(history.length()-1)=='B'?0:1;}
    public int serviceStep(){return matchTie?(pb+pr==0?(blue+red+1)/2:(pb+pr+1)/2):blue+red+(tieBreak()?(pb+pr+1)/2:0);}
    public int gameBall(){if(format.nested()||winner()!=NONE)return NONE;int target=matchTie?10:format.target;if(blue>=target-1&&blue>red||blue==format.cap()-1&&red==blue)return BLUE;if(red>=target-1&&red>blue)return RED;return NONE;}
    public boolean uses(MatchFormat f,boolean tie){return format.equals(f)&&matchTie==tie;}
}
