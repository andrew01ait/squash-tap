package at.andrew.squashtap;

/** Fixed scoreboard palettes. Brightness remains under the watch system's control. */
public final class ScorePalette {
    public static final String[] IDS={"classic","neon-lime","sunlight"};
    public final String id,name,help;public final int left,right,leftInk,rightInk,leftPressed,rightPressed,serve,serveInk,outline,button,buttonInk,muted;
    private ScorePalette(String id,String name,String help,int left,int right,int leftInk,int rightInk,int leftPressed,int rightPressed,int serve,int serveInk,int outline,int button,int buttonInk,int muted){this.id=id;this.name=name;this.help=help;this.left=left;this.right=right;this.leftInk=leftInk;this.rightInk=rightInk;this.leftPressed=leftPressed;this.rightPressed=rightPressed;this.serve=serve;this.serveInk=serveInk;this.outline=outline;this.button=button;this.buttonInk=buttonInk;this.muted=muted;}
    public static boolean valid(String id){for(String value:IDS)if(value.equals(id))return true;return false;}
    public static ScorePalette get(String id){
        if("neon-lime".equals(id))return new ScorePalette(id,"Neon Lime","Electric lime and bright pink, with dark scores.",0xffc5ff00,0xffff65d3,0xff102000,0xff210b22,0xffaee300,0xffdf51b8,0xff102000,0xffc5ff00,0xff102000,0xffc5ff00,0xff102000,0xffd2e3bd);
        if("sunlight".equals(id))return new ScorePalette(id,"Sunlight","Black scores on white and yellow for strong contrast outdoors.",0xffffffff,0xffffec66,0xff000000,0xff000000,0xffdddddd,0xffe2cf50,0xff000000,0xffffffff,0xff000000,0xffffffff,0xff000000,0xffffffff);
        return new ScorePalette("classic","Classic","The original blue and red score cards.",0xff1668cb,0xffb82e4c,0xffffffff,0xffffffff,0xff2489f5,0xfff34b65,0xfff6e7ab,0xff172031,0xfff6e7ab,0xff1668cb,0xffffffff,0xffcbd5e1);
    }
}
