package at.andrew.squashtap;

/** Immutable rules saved with every match; default changes never reinterpret history. */
public final class MatchFormat {
    public static final String[] SPORTS={"squash","tennis","badminton","padel","table-tennis","pickleball"};
    public final String sport,deuce;
    public final boolean doubles,finalTie;
    public final int target;
    public MatchFormat(String sport,boolean doubles,int target,String deuce,boolean finalTie){
        if(!java.util.Arrays.asList(SPORTS).contains(sport))throw new IllegalArgumentException("Unknown sport");
        boolean nested=sport.equals("tennis")||sport.equals("padel");
        if(sport.equals("squash")&&doubles||sport.equals("padel")&&!doubles)throw new IllegalArgumentException("Invalid player mode");
        boolean pickle=sport.equals("pickleball");
        if(pickle?(finalTie||(target!=11&&target!=15&&target!=21)||!(deuce.equals("side-out")||deuce.equals("rally-2025")||deuce.equals("rally-2026"))):nested?(target!=6||!(deuce.equals("advantage")||deuce.equals("no-ad")||sport.equals("padel")&&deuce.equals("star"))):(finalTie||!deuce.equals("advantage")||(sport.equals("badminton")?target!=15&&target!=21:target!=11)))throw new IllegalArgumentException("Invalid scoring rules");
        this.sport=sport;this.doubles=doubles;this.target=target;this.deuce=deuce;this.finalTie=finalTie;
    }
    public static MatchFormat squash(){return defaults("squash");}
    public static MatchFormat defaults(String sport){return new MatchFormat(sport,sport.equals("padel")||sport.equals("pickleball"),sport.equals("badminton")?21:sport.equals("tennis")||sport.equals("padel")?6:11,sport.equals("pickleball")?"side-out":sport.equals("padel")?"star":"advantage",false);}
    public boolean pickleball(){return sport.equals("pickleball");}
    public boolean sideOut(){return pickleball()&&deuce.equals("side-out");}
    public boolean nested(){return sport.equals("tennis")||sport.equals("padel");}
    public boolean legacy(){return sport.equals("squash");}
    public String name(){return name(sport);}
    public static String name(String sport){switch(sport){case "squash":return "Squash";case "tennis":return "Tennis";case "badminton":return "Badminton";case "padel":return "Padel";case "table-tennis":return "Table tennis";case "pickleball":return "Pickleball";default:throw new IllegalArgumentException("Unknown sport");}}
    public String mode(){return doubles?"Doubles":"Singles";}
    public String unit(){return nested()?"Set":"Game";}
    public int cap(){return sport.equals("badminton")?(target==21?30:21):Integer.MAX_VALUE;}
    public int[] bestOptions(){return pickleball()?new int[]{1,3,5}:sport.equals("table-tennis")?new int[]{3,5,7}:sport.equals("badminton")||sport.equals("padel")?new int[]{3}:new int[]{3,5};}
    public void validateBest(int best){for(int value:bestOptions())if(value==best)return;throw new IllegalArgumentException("Unsupported match length for "+name());}
    public String description(){return mode()+" · "+(nested()?(deuce.equals("star")?"Star Point":deuce.equals("no-ad")?"No-Ad / Golden Point":"Advantage")+" · "+(finalTie?"deciding match tie-break to 10":"full deciding set"):(pickleball()?(sideOut()?"Traditional side-out":deuce.equals("rally-2025")?"Rally 2025 · win on serve":"Rally 2026 · every rally")+" · ":"")+"to "+target+", win by two"+(sport.equals("badminton")?", cap "+cap():""));}
    public MatchFormat mode(boolean value){return new MatchFormat(sport,value,target,deuce,finalTie);}
    public MatchFormat target(int value){return new MatchFormat(sport,doubles,value,deuce,finalTie);}
    public MatchFormat deuce(String value){return new MatchFormat(sport,doubles,target,value,finalTie);}
    public MatchFormat finalTie(boolean value){return new MatchFormat(sport,doubles,target,deuce,value);}
    public String encode(){return sport+","+(doubles?1:0)+","+target+","+deuce+","+(finalTie?1:0);}
    public static MatchFormat decode(String value){String[] p=value.split(",",-1);if(p.length!=5||!p[1].matches("[01]")||!p[4].matches("[01]"))throw new IllegalArgumentException("Invalid match rules");return new MatchFormat(p[0],p[1].equals("1"),Integer.parseInt(p[2]),p[3],p[4].equals("1"));}
    @Override public boolean equals(Object other){return other instanceof MatchFormat&&encode().equals(((MatchFormat)other).encode());}
    @Override public int hashCode(){return encode().hashCode();}
}
