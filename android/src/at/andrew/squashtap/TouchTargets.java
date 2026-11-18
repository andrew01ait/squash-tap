package at.andrew.squashtap;

/** Normalized hit areas are shared with the canvas and executable interaction tests. */
public final class TouchTargets {
    public static final int OUTSIDE = -1, BLUE = 0, RED = 1, UNDO = 2, NEW = 3, MENU = 4;
    public static int hit(float x, float y) {
        return hit(x,y,233,233,true);
    }
    /** Real watch dimensions keep every action at least 48dp high without stealing score taps. */
    public static float[] area(int target,float widthDp,float heightDp){
        return area(target,widthDp,heightDp,true);
    }
    public static float[] area(int target,float widthDp,float heightDp,boolean round){
        float side=Math.min(widthDp,heightDp);
        float menuBottom=Math.max(48f,side*(round?.225f:.205f))/heightDp;
        float cardTop=menuBottom+Math.max(2f,side*.012f)/heightDp;
        float footerHeight=Math.max(48f,side*.19f)/heightDp;
        float footerBottom=round?.956f:1f-6f/heightDp;
        float footerTop=footerBottom-footerHeight;
        float cardBottom=footerTop-(round?Math.max(5f,side*.028f):Math.max(8f,side*.04f))/heightDp;
        float outer=round?.065f:Math.max(5f,widthDp*.03f)/widthDp;
        float halfGap=Math.max(4f,widthDp*.025f)/(2*widthDp);
        if(target==MENU)return new float[]{round?.15f:outer,0,round?.85f:1f-outer,menuBottom};
        if(target==BLUE)return new float[]{outer,cardTop,.5f-halfGap,cardBottom};
        if(target==RED)return new float[]{.5f+halfGap,cardTop,1f-outer,cardBottom};
        float footerEdge=round?.15f:outer;
        if(target==UNDO)return new float[]{footerEdge,footerTop,.5f-halfGap,footerBottom};
        if(target==NEW)return new float[]{.5f+halfGap,footerTop,1f-footerEdge,footerBottom};
        return new float[]{0,0,0,0};
    }
    public static int hit(float x,float y,float widthDp,float heightDp){
        return hit(x,y,widthDp,heightDp,true);
    }
    public static int hit(float x,float y,float widthDp,float heightDp,boolean round){
        if(widthDp<=0||heightDp<=0||x<0||y<0||x>1||y>1)return OUTSIDE;
        for(int target:new int[]{MENU,BLUE,RED,UNDO,NEW}){float[] r=area(target,widthDp,heightDp,round);if(x>=r[0]&&x<=r[2]&&y>=r[1]&&y<=r[3])return target;}
        return OUTSIDE;
    }
    public static boolean isTap(float startX, float startY, float endX, float endY, long durationMs, float slop) {
        return durationMs >= 0 && durationMs <= 1000 && Math.hypot(endX - startX, endY - startY) <= slop;
    }
}
