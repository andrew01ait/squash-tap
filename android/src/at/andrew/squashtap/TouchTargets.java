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
    public static boolean isTap(float startX, float startY, float endX, float endY, long durationMs, float slop) {
        return durationMs >= 0 && durationMs <= 1000 && Math.hypot(endX - startX, endY - startY) <= slop;
    }
}
