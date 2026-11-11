package at.andrew.squashtap;

/** Normalized hit areas are shared with the canvas and executable interaction tests. */
public final class TouchTargets {
    public static final int OUTSIDE = -1, BLUE = 0, RED = 1, UNDO = 2, NEW = 3, MENU = 4;
    public static int hit(float x, float y) {
        if (x < 0 || y < 0 || x > 1 || y > 1) return OUTSIDE;
        if (y >= .055f && y <= .225f && x >= .18f && x <= .82f) return MENU;
        if (y >= .245f && y <= .67f) {
            if (x >= .075f && x <= .48f) return BLUE;
            if (x >= .52f && x <= .925f) return RED;
        }
        if (y >= .74f && y <= .93f) {
            if (x >= .15f && x <= .46f) return UNDO;
            if (x >= .54f && x <= .85f) return NEW;
        }
        return OUTSIDE;
    }
    /** Real watch dimensions keep every action at least 48dp high without stealing score taps. */
    public static float[] area(int target,float widthDp,float heightDp){
        float menuBottom=Math.max(.225f,48f/heightDp),cardTop=Math.max(.245f,menuBottom+.005f);
        if(target==MENU)return new float[]{.15f,0,.85f,menuBottom};
        if(target==BLUE)return new float[]{.075f,cardTop,.48f,.67f};
        if(target==RED)return new float[]{.52f,cardTop,.925f,.67f};
        float half=Math.max(.095f,24f/heightDp),halfWidth=Math.max(.155f,24f/widthDp);
        if(target==UNDO)return new float[]{.305f-halfWidth,.82f-half,.305f+halfWidth,Math.min(1,.82f+half)};
        if(target==NEW)return new float[]{.695f-halfWidth,.82f-half,.695f+halfWidth,Math.min(1,.82f+half)};
        return new float[]{0,0,0,0};
    }
    public static int hit(float x,float y,float widthDp,float heightDp){
        if(widthDp<=0||heightDp<=0||x<0||y<0||x>1||y>1)return OUTSIDE;
        for(int target:new int[]{MENU,BLUE,RED,UNDO,NEW}){float[] r=area(target,widthDp,heightDp);if(x>=r[0]&&x<=r[2]&&y>=r[1]&&y<=r[3])return target;}
        return OUTSIDE;
    }
    public static boolean isTap(float startX, float startY, float endX, float endY, long durationMs, float slop) {
        return durationMs >= 0 && durationMs <= 1000 && Math.hypot(endX - startX, endY - startY) <= slop;
    }
}
