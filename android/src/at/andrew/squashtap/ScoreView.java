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
    private String opponentLabel="OPPONENT",partnerLabel="Partner",primaryLabel="Opponent 1",otherLabel="Opponent 2";
    public void players(String partner,String primary,String other){partnerLabel=partner==null||partner.isEmpty()?"Partner":partner;primaryLabel=primary==null||primary.isEmpty()?"Opponent 1":primary;otherLabel=other==null||other.isEmpty()?"Opponent 2":other;refresh();}

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
        setContentDescription("Squash Tap. "+match.format().name()+". "+(match.format().doubles?"Our team ":"Me ") + match.game().displayPoint(0)+", "+opponentLabel+" "+match.game().displayPoint(1)
            +". "+(match.format().nested()?"Sets ":"Games ")+match.gamesWon(0)+" to "+match.gamesWon(1)+", best of "+match.bestOf()+". "+serviceAnnouncement()+" Bottom left Undo; bottom right "+(match.nextReady()?"Next "+match.format().unit().toLowerCase(java.util.Locale.ROOT):"New match")+". Top opens the "+match.format().name()+" menu: Current match, Results, Statistics, Players and Settings.");
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        ScoreState state=match.game();
        paint.setAntiAlias(!ambient||!lowBit);
        if(ambient){drawAmbient(canvas,w,h,s);return;}
        canvas.drawColor(Color.rgb(8, 13, 23));
        int server=knownServer();
        sportHeading(canvas,w,h,s);
        text(canvas,(match.format().nested()?"SETS ":"GAMES ")+match.gamesWon(0)+"–"+match.gamesWon(1)+" · BO"+match.bestOf()+(match.format().nested()?" · GAMES "+state.blue()+"–"+state.red():" · GAME "+match.gameCount())+(updateAvailable?" •":""),
            w * .5f, h * .183f, s * .033f, updateAvailable ? 0xff94edb4 : 0xffcbd5e1, false);
        card(canvas, w * .075f, h * .245f, w * .48f, h * .67f, TouchTargets.BLUE,
            match.format().doubles?"OUR TEAM":"ME", state.displayPoint(0), 0xff1668cb, 0xff2489f5, s,server);
        card(canvas, w * .52f, h * .245f, w * .925f, h * .67f, TouchTargets.RED,
            opponentLabel, state.displayPoint(1), 0xffb82e4c, 0xfff34b65, s,server);
        String status = paused?"PAUSED · TAP MENU":match.retired()?(match.winner()==0?"WON · RETIREMENT":"LOST · RETIREMENT"):match.complete()?(match.winner()==0?"MATCH WON":"MATCH LOST")
            :!sessionActive?"TAP +1 TO START"
            :state.winner() == ScoreState.BLUE ? "SET WON · NEXT TO PLAY"
            : state.winner() == ScoreState.RED ? "SET LOST · NEXT TO PLAY"
            :match.format().sport.equals("squash")&&state.gameBall()!=-1?(match.gamesWon(state.gameBall())==match.needed()-1?"MATCH BALL":"GAME BALL")+" · "+(state.gameBall()==0?"ME":"OPPONENT"):state.status();
        text(canvas, status, w * .5f, h * .717f, s * .035f,
            state.winner() == ScoreState.NONE ? 0xffa7b6c9 : 0xffd1f5df, true);
        button(canvas, w*.18f, h*.765f, w*.46f, h*.875f, "UNDO", TouchTargets.UNDO, match.hasPoints()||match.retired(), s);
        button(canvas, w*.54f, h*.765f, w*.82f, h*.875f, match.nextReady()?"NEXT":"NEW", TouchTargets.NEW, true, s);
    }
    private void sportHeading(Canvas c,float w,float h,float s){
        String label=match.format().name().toUpperCase(java.util.Locale.ROOT)+" · MENU";float size=s*.039f;paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));float width=paint.measureText(label),iconSize=s*.052f,left=(w-width-iconSize-s*.018f)/2;
        SportIcons icon=new SportIcons(match.format().sport,(int)iconSize);icon.setBounds((int)left,(int)(h*.108f-iconSize/2),(int)(left+iconSize),(int)(h*.108f+iconSize/2));icon.draw(c);
        text(c,label,left+iconSize+s*.018f+width/2,h*.108f,size,SportIcons.accent(match.format().sport),true);
    }
    private void card(Canvas c, float l, float t, float r, float b, int target, String label, String value, int fill, int accent, float s,int server) {
        ScoreState state=match.game();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed == target ? accent : fill);
        c.drawRoundRect(new RectF(l,t,r,b), s*.07f, s*.07f, paint);
        if ((match.retired()?match.winner():state.winner()) == target||serverSide(server)==target) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(s*.012f); paint.setColor(0xffebf8ef);
            if(serverSide(server)==target){paint.setColor(0xfff6e7ab);paint.setStrokeWidth(s*.008f);}
            c.drawRoundRect(new RectF(l+s*.006f,t+s*.006f,r-s*.006f,b-s*.006f), s*.07f,s*.07f,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        String shown=fitLabel(label,r-l-s*.06f,s*.042f,true);
        text(c,shown,(l+r)/2,t+(b-t)*.20f,s*.042f,0xfff5f8ff,true);
        text(c,value,(l+r)/2,t+(b-t)*.56f,
            s*(value.length()<3?.225f:value.length()<4?.17f:.125f),Color.WHITE,true);
        if(serverSide(server)==target)serveBadge(c,l+s*.025f,r-s*.025f,t+(b-t)*.86f,s,server);
        else{String footer=match.retired()?(match.retiredPlayer()==target?"RETIRED":"MATCH WINNER"):state.winner()==ScoreState.NONE?"TAP +1":state.winner()==target?"WINNER":"GAME OVER";
            text(c,fitLabel(footer,r-l-s*.04f,s*.033f,false),(l+r)/2,t+(b-t)*.86f,s*.033f,0xffe4eafa,false);}
    }
    private int knownServer(){return match.complete()||match.game().winner()!=ScoreState.NONE?-1:match.server();}
    private int serverSide(int slot){return slot<0?-1:slot>=4?slot-4:slot%2;}
    private String serveLabel(int slot){
        if(!match.format().doubles||slot>=4)return "SERVING";
        return "SERVE · "+(slot==0?"ME":slot==1?"OPP. 1":slot==2?"PARTNER":"OPP. 2");
    }
    private String serviceAnnouncement(){
        if(match.complete()||match.game().winner()!=ScoreState.NONE)return "";
        int slot=knownServer();if(slot<0)return "Serving not set.";
        String name=slot==0?"Me":slot==1?match.format().doubles?primaryLabel:opponentLabel:slot==2?partnerLabel:slot==3?otherLabel:slot==4?"Our team":"Opposing team";
        return "Serving: "+name+".";
    }
    private void serveBadge(Canvas c,float l,float r,float y,float s,int server){
        String label=serveLabel(server);float font=s*.036f;paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(font);
        float available=r-l-s*.045f;while(paint.measureText(label)>available){font-=s*.001f;paint.setTextSize(font);}
        float width=paint.measureText(label)+s*.045f,center=(l+r)/2;
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xfff6e7ab);c.drawRoundRect(new RectF(center-width/2,y-s*.031f,center+width/2,y+s*.031f),s*.025f,s*.025f,paint);
        text(c,label,center,y,font,0xff172031,true);
    }
    private String fitLabel(String value,float maxWidth,float size,boolean bold){
        paint.setTextSize(size);paint.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
        if(paint.measureText(value)<=maxWidth)return value;
        int end=value.length();while(end>0&&paint.measureText(value.substring(0,end)+"…")>maxWidth)end=value.offsetByCodePoints(end,-1);
        return value.substring(0,end)+"…";
    }
    private void drawAmbient(Canvas c,float w,float h,float s){
        c.drawColor(Color.BLACK);int shift=(int)((System.currentTimeMillis()/60000)%5)-2;c.save();c.translate(shift,-shift);
        text(c,java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),w*.5f,h*.115f,s*.065f,0xffaaaaaa,false);
        text(c,match.format().name().toUpperCase(java.util.Locale.ROOT)+" · "+match.gamesWon(0)+"–"+match.gamesWon(1)+" · BO"+match.bestOf(),w*.5f,h*.23f,s*.035f,0xff999999,false);
        text(c,"ME",w*.27f,h*.36f,s*.04f,0xffaaaaaa,false);String label=fitLabel(opponentLabel,w*.36f,s*.04f,false);text(c,label,w*.73f,h*.36f,s*.04f,0xffaaaaaa,false);
        text(c,match.game().displayPoint(0),w*.27f,h*.51f,s*.20f,0xffbbbbbb,false);
        text(c,match.game().displayPoint(1),w*.73f,h*.51f,s*.20f,0xffbbbbbb,false);
        int server=knownServer();if(serverSide(server)>=0)text(c,serveLabel(server),w*(serverSide(server)==0?.27f:.73f),h*.63f,s*.031f,0xffbbbbbb,true);
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
