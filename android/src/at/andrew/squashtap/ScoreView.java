package at.andrew.squashtap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public final class ScoreView extends View {
    public interface Actions { void action(int target); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Actions actions;
    private MatchState match;
    private float downX, downY;
    private long downTime;
    private int pressed = TouchTargets.OUTSIDE;
    private boolean updateAvailable;
    private boolean ambient,lowBit;
    private boolean paused;
    private boolean sessionActive;
    private String opponentLabel="OPPONENT";

    public ScoreView(Context context, MatchState match, Actions actions) {
        super(context);
        this.match = match;
        this.actions = actions;
        setFocusable(true);
        refresh();
    }
    public void available(boolean available) { updateAvailable = available; invalidate(); }
    public void opponent(String name){opponentLabel=name==null||name.isEmpty()?"OPPONENT":name;refresh();}
    public void match(MatchState value){match=value;refresh();}
    public void session(boolean active,boolean paused){sessionActive=active;this.paused=paused;invalidate();}
    public void ambient(boolean value,boolean lowBit){ambient=value;this.lowBit=lowBit;pressed=TouchTargets.OUTSIDE;invalidate();}
    public void refresh() {
        setContentDescription("Squash Tap. Me " + match.game().blue()+", "+opponentLabel+" "+match.game().red()
            +". Sets "+match.gamesWon(0)+" to "+match.gamesWon(1)+", best of "+match.bestOf()+". Bottom left Undo; bottom right "+(match.nextReady()?"Next set":"New match")+". Top opens Menu: Current match, Results, Statistics, Opponents and Settings.");
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        ScoreState state=match.game();
        paint.setAntiAlias(!ambient||!lowBit);
        if(ambient){drawAmbient(canvas,w,h,s);return;}
        canvas.drawColor(Color.rgb(8, 13, 23));
        text(canvas, "SETS "+match.gamesWon(0)+"–"+match.gamesWon(1)+" · BO"+match.bestOf(), w * .5f, h * .112f, s * .045f, 0xffe5ecf7, true);
        text(canvas, "SET "+match.gameCount()+" · MENU"+(updateAvailable?" •":""),
            w * .5f, h * .183f, s * .042f, updateAvailable ? 0xff94edb4 : 0xff9daec4, false);
        card(canvas, w * .075f, h * .245f, w * .48f, h * .67f, TouchTargets.BLUE,
            "ME", state.blue(), 0xff1668cb, 0xff2489f5, s);
        card(canvas, w * .52f, h * .245f, w * .925f, h * .67f, TouchTargets.RED,
            opponentLabel, state.red(), 0xffb82e4c, 0xfff34b65, s);
        String status = paused?"PAUSED · TAP MENU":match.retired()?(match.winner()==0?"WON · RETIREMENT":"LOST · RETIREMENT"):match.complete()?(match.winner()==0?"MATCH WON":"MATCH LOST")
            :!sessionActive?"TAP +1 TO START"
            :state.winner() == ScoreState.BLUE ? "SET WON · NEXT TO PLAY"
            : state.winner() == ScoreState.RED ? "SET LOST · NEXT TO PLAY"
            : state.deuce() ? "DEUCE · WIN BY TWO" : "FIRST TO 11 · WIN BY TWO";
        text(canvas, status, w * .5f, h * .717f, s * .035f,
            state.winner() == ScoreState.NONE ? 0xffa7b6c9 : 0xffd1f5df, true);
        button(canvas, w*.18f, h*.765f, w*.46f, h*.875f, "UNDO", TouchTargets.UNDO, match.hasPoints()||match.retired(), s);
        button(canvas, w*.54f, h*.765f, w*.82f, h*.875f, match.nextReady()?"NEXT":"NEW", TouchTargets.NEW, true, s);
    }
    private void card(Canvas c, float l, float t, float r, float b, int target, String label, int value, int fill, int accent, float s) {
        ScoreState state=match.game();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed == target ? accent : fill);
        c.drawRoundRect(new RectF(l,t,r,b), s*.07f, s*.07f, paint);
        if ((match.retired()?match.winner():state.winner()) == target) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(s*.012f); paint.setColor(0xffebf8ef);
            c.drawRoundRect(new RectF(l+s*.006f,t+s*.006f,r-s*.006f,b-s*.006f), s*.07f,s*.07f,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        String shown=label;while(shown.codePointCount(0,shown.length())>12)shown=shown.substring(0,shown.offsetByCodePoints(0,11))+"…";
        text(c,shown,(l+r)/2,t+(b-t)*.20f,s*.042f,0xfff5f8ff,true);
        text(c,Integer.toString(value),(l+r)/2,t+(b-t)*.59f,
            s*(value<100?.225f:value<1000?.17f:.125f),Color.WHITE,true);
        text(c,match.retired()?(match.retiredPlayer()==target?"RETIRED":"MATCH WINNER"):state.winner()==ScoreState.NONE?"TAP +1":state.winner()==target?"WINNER":"GAME OVER",
            (l+r)/2,t+(b-t)*.86f,s*.033f,0xffe4eafa,false);
    }
    private void drawAmbient(Canvas c,float w,float h,float s){
        c.drawColor(Color.BLACK);int shift=(int)((System.currentTimeMillis()/60000)%5)-2;c.save();c.translate(shift,-shift);
        text(c,java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),w*.5f,h*.115f,s*.065f,0xffaaaaaa,false);
        text(c,"SETS "+match.gamesWon(0)+"–"+match.gamesWon(1)+" · BO"+match.bestOf(),w*.5f,h*.23f,s*.04f,0xff999999,false);
        text(c,"ME",w*.27f,h*.36f,s*.04f,0xffaaaaaa,false);String label=opponentLabel.codePointCount(0,opponentLabel.length())>12?opponentLabel.substring(0,opponentLabel.offsetByCodePoints(0,11))+"…":opponentLabel;text(c,label,w*.73f,h*.36f,s*.04f,0xffaaaaaa,false);
        text(c,Integer.toString(match.game().blue()),w*.27f,h*.51f,s*.20f,0xffbbbbbb,false);
        text(c,Integer.toString(match.game().red()),w*.73f,h*.51f,s*.20f,0xffbbbbbb,false);
        text(c,paused?"MATCH PAUSED":match.complete()?"MATCH COMPLETE":sessionActive?"MATCH IN PROGRESS":"READY TO PLAY",w*.5f,h*.72f,s*.037f,0xff999999,false);
        text(c,"Wake to score",w*.5f,h*.82f,s*.035f,0xff888888,false);c.restore();
    }
    private void button(Canvas c,float l,float t,float r,float b,String label,int target,boolean enabled,float s) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(pressed==target?0xff2489f5:enabled?0xff1668cb:0xff123f70);
        c.drawRoundRect(new RectF(l,t,r,b),s*.05f,s*.05f,paint);
        text(c,label,(l+r)/2,(t+b)/2,s*.038f,enabled?Color.WHITE:0xffa5cdf7,true);
    }
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setTextSize(size);
        paint.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(value,x,y-(paint.ascent()+paint.descent())/2,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(ambient)return true;
        if (event.getPointerCount() > 1) { pressed = TouchTargets.OUTSIDE; invalidate(); return true; }
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=event.getX(); downY=event.getY(); downTime=event.getEventTime();
                pressed=TouchTargets.hit(downX/getWidth(),downY/getHeight()); invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX()-downX,event.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop()*2)
                    pressed=TouchTargets.OUTSIDE;
                invalidate(); return true;
            case MotionEvent.ACTION_UP:
                int target=TouchTargets.hit(event.getX()/getWidth(),event.getY()/getHeight());
                boolean tap=target==pressed && target!=TouchTargets.OUTSIDE && TouchTargets.isTap(downX,downY,
                    event.getX(),event.getY(),event.getEventTime()-downTime,ViewConfiguration.get(getContext()).getScaledTouchSlop()*2);
                pressed=TouchTargets.OUTSIDE; invalidate();
                if(tap){ performClick(); performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); actions.action(target); }
                return true;
            case MotionEvent.ACTION_CANCEL: pressed=TouchTargets.OUTSIDE; invalidate(); return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){ super.performClick(); return true; }
}
