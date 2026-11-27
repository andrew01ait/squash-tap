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
    private WatchLanguage ui;
    private String remoteStatus;
    private boolean finalConfirmed;
    private String tr(String text){return ui.text(text);}
    void language(WatchLanguage language){ui=language;refresh();}
    void remote(String status,boolean confirmed){
        if(java.util.Objects.equals(status,remoteStatus)&&confirmed==finalConfirmed)return;
        remoteStatus=status;
        setClickable(status!=null);
        finalConfirmed=confirmed;
        pressed=TouchTargets.OUTSIDE;
        refresh();
    }
    private MatchState match;
    private ScorePalette palette=ScorePalette.get("classic");
    public void palette(String id){palette=ScorePalette.get(id);invalidate();}
    private float downX, downY;
    private long downTime;
    private int pressed = TouchTargets.OUTSIDE;
    private boolean updateAvailable;
    private boolean ambient,lowBit;
    private boolean paused;
    private boolean sessionActive;
    private String reminder="";
    public void reminder(String value){reminder=value;invalidate();if(!value.isEmpty())postDelayed(()->{if(reminder.equals(value)){reminder="";invalidate();}},4500);}
    private String ownLabel="Me",opponentLabel="OPPONENT",partnerLabel="Partner",primaryLabel="Opponent 1",otherLabel="Opponent 2";
    public void ownName(String value){ownLabel=value==null||value.isEmpty()?tr("Me"):value;refresh();}
    private float fontScale(){return Math.max(.5f,getResources().getConfiguration().fontScale);}
    private float density(){return getResources().getDisplayMetrics().density;}
    private boolean round(){return getResources().getConfiguration().isScreenRound();}
    private RectF targetBounds(int target){
        float w=getWidth(),h=getHeight();
        float[] area=TouchTargets.area(target,w/density(),h/density(),round());
        return new RectF(area[0]*w,area[1]*h,area[2]*w,area[3]*h);
    }
    public void players(String partner,String primary,String other){partnerLabel=partner==null||partner.isEmpty()?tr("Partner"):partner;primaryLabel=primary==null||primary.isEmpty()?tr("Opponent 1"):primary;otherLabel=other==null||other.isEmpty()?tr("Opponent 2"):other;refresh();}

    public ScoreView(Context context, MatchState match, Actions actions) {
        super(context);
        ui=WatchLanguage.get(context);
        ownLabel=tr("Me");opponentLabel=tr("OPPONENT");partnerLabel=tr("Partner");
        primaryLabel=tr("Opponent 1");otherLabel=tr("Opponent 2");
        this.match = match;
        this.actions = actions;
        setFocusable(true);
        refresh();
    }
    public void available(boolean available) { updateAvailable = available; invalidate(); }
    public void opponent(String name){opponentLabel=name==null||name.isEmpty()?tr("OPPONENT"):name;refresh();}
    public void match(MatchState value){match=value;refresh();}
    public void session(boolean active,boolean paused){sessionActive=active;this.paused=paused;invalidate();}
    public void ambient(boolean value,boolean lowBit){ambient=value;this.lowBit=lowBit;pressed=TouchTargets.OUTSIDE;invalidate();}
    private String periodUnit(boolean plural) {
        return ui.periodUnit(match.format(),plural);
    }
    private String periodNumber() {
        return periodUnit(false) + " " + match.gameCount() + "/" + match.bestOf();
    }
    private String periodDetail() {
        ScoreState current = match.game();
        if (current.matchTie()) return tr("Match tie-break");
        if (match.format().nested()) return tr("Games") + " " + current.blue() + "–" + current.red();
        int last = match.gameCount() - (current.winner() == ScoreState.NONE ? 2 : 1);
        if (last < 0) return "";
        ScoreState finished = match.game(last);
        return ui.pick("Last", "Zuletzt", "Préc.", "Último") + " " + finished.blue() + "–" + finished.red();
    }
    private String periodAnnouncement() {
        String detail = periodDetail();
        return periodUnit(true) + " " + match.gamesWon(0) + ui.pick(" to ", " zu ", " à ", " a ") + match.gamesWon(1)
            + ". " + periodUnit(false) + " " + match.gameCount() + ", " + tr("Best of") + " "
            + match.bestOf() + (detail.isEmpty() ? "" : ". " + detail);
    }
    private String rallyStatus(ScoreState state) {
        String status = state.status();
        if (!status.startsWith("GAMES ")) return ui.scoreTerms(status);
        // Tennis and padel already show this set's game tally above the points.
        int previous = match.gameCount() - 2;
        if (previous < 0) return "";
        ScoreState finished = match.game(previous);
        return periodUnit(false) + " " + (previous+1) + " · " + finished.blue() + "–" + finished.red();
    }
    public void refresh() {
        if(remoteStatus!=null){
            String right=match.format().doubles?primaryLabel+" & "+otherLabel:opponentLabel;
            String left=match.format().doubles?ownLabel+" & "+partnerLabel:ownLabel;
            setContentDescription(tr("Read-only score.")+" "+tr(match.format().name())+". "+
                left+" "+tr(match.game().displayPoint(0))+", "+right+" "+tr(match.game().displayPoint(1))+
                ". "+periodAnnouncement()+
                ". "+remoteStatus+". "+tr(finalConfirmed?"Save result":"Match menu"));
            invalidate();return;
        }
        setContentDescription(tr("Keep Score. ")+tr(match.format().name())+". "+(match.format().doubles?ownLabel+tr(" and ")+partnerLabel+" ":ownLabel+" ") + tr(match.game().displayPoint(0))+", "+opponentLabel+" "+tr(match.game().displayPoint(1))
            +". "+periodAnnouncement()+". "+serviceAnnouncement()+tr(" Bottom left Undo; bottom right ")+(match.nextReady()?tr("Next ")+periodUnit(false).toLowerCase(ui.locale):tr("New match"))+tr(". Top opens the ")+tr(match.format().name())+ui.pick(" menu, including live sharing and the referee."," mit Live-Teilen und Schiedsrichter."," avec le partage en direct et l’arbitrage."," con las opciones de compartir en directo y de arbitraje."));
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        ScoreState state=match.game();
        paint.setAntiAlias(!ambient||!lowBit);
        if(ambient){drawAmbient(canvas,w,h,s);return;}
        canvas.drawColor(BuildConfig.PLAY_DISTRIBUTION?Color.BLACK:Color.rgb(8, 13, 23));
        int server=knownServer();
        RectF left=targetBounds(TouchTargets.BLUE),right=targetBounds(TouchTargets.RED);
        float headerHeight=targetBounds(TouchTargets.MENU).bottom;
        sportHeading(canvas,w,headerHeight,s);
        periodPanel(canvas,w,headerHeight*.49f,headerHeight,s);
        card(canvas,left.left,left.top,left.right,left.bottom,TouchTargets.BLUE,
            match.format().doubles?ownLabel+" & "+partnerLabel:ownLabel.toUpperCase(java.util.Locale.ROOT), tr(state.displayPoint(0)), palette.left, palette.leftPressed, s,server);
        card(canvas,right.left,right.top,right.right,right.bottom,TouchTargets.RED,
            opponentLabel, tr(state.displayPoint(1)), palette.right, palette.rightPressed, s,server);
        String status = paused?tr("PAUSED · TAP MENU"):match.retired()?(match.winner()==0?tr("WON · RETIREMENT"):tr("LOST · RETIREMENT")):match.complete()?(match.winner()==0?tr("MATCH WON"):tr("MATCH LOST"))
            :!sessionActive?(match.format().pickleball()?tr("TAP RALLY TO START"):tr("TAP +1 TO START"))
            :state.winner() == ScoreState.BLUE ? tr("SET WON · NEXT TO PLAY")
            : state.winner() == ScoreState.RED ? tr("SET LOST · NEXT TO PLAY")
            :match.format().sport.equals("squash")&&state.gameBall()!=-1?(match.gamesWon(state.gameBall())==match.needed()-1?tr("MATCH BALL"):tr("GAME BALL"))+" · "+(state.gameBall()==0?tr("ME"):tr("OPPONENT")):rallyStatus(state);
        RectF undo=targetBounds(TouchTargets.UNDO),next=targetBounds(TouchTargets.NEW);
        float inset=dp(round()?7:3);
        float top=undo.top+inset,bottom=undo.bottom-inset;
        // The lower outside corners of both buttons must fit the visible round face.
        float edge=round()?w/2f-(float)Math.sqrt(Math.max(0,bottom*(h-bottom)))*w/h+dp(1):undo.left;
        float buttonLeft=Math.max(undo.left,edge);
        float statusCenter=(left.bottom+top)/2,statusHeight=top-left.bottom-dp(1);
        if(remoteStatus!=null){
            fittedText(canvas,remoteStatus,w*.5f,statusCenter,s*.041f,w*.84f,statusHeight,palette.muted,true);
            button(canvas,buttonLeft,top,w-buttonLeft,bottom,tr(finalConfirmed?"Save result":"Match menu"),TouchTargets.MENU,true,s);
            return;
        }
        if(!reminder.isEmpty())status=tr(reminder);
        fittedText(canvas,status,w*.5f,statusCenter,s*.041f,w*.84f,statusHeight,
            palette.id.equals("sunlight")?Color.WHITE:state.winner() == ScoreState.NONE ? 0xffa7b6c9 : 0xffd1f5df, true);
        button(canvas,buttonLeft,top,undo.right,bottom,tr("UNDO"),TouchTargets.UNDO,match.hasPoints()||match.retired(),s);
        button(canvas,next.left,top,w-buttonLeft,bottom,match.nextReady()?tr("NEXT"):tr("NEW"),TouchTargets.NEW,true,s);
    }
    // Use actual text metrics for the narrow chord at the top of round watches.
    private float fittedPixels(String value, float desired, float width, float height, boolean bold) {
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextSize(desired);
        return desired * Math.min(1f, Math.min(width / Math.max(1f, paint.measureText(value)),
            height / Math.max(1f, paint.descent() - paint.ascent())));
    }
    private void fittedText(Canvas c, String value, float center, float y, float desired,
                            float width, float height, int ink, boolean bold) {
        float size = fittedPixels(value, desired * fontScale(), width, height, bold);
        text(c,value,center,y,size/fontScale(),ink,bold);
    }
    private void sportHeading(Canvas c,float w,float headerHeight,float s){
        String label=tr(match.format().name()).toUpperCase(ui.locale)+tr(" · MENU")+(updateAvailable?" •":"");
        float iconSize=s*.05f,gap=s*.014f,y=headerHeight*.31f;
        float size=fittedPixels(label,s*.047f*fontScale(),w*(round()?.47f:.82f)-iconSize-gap,headerHeight*.30f,true);
        paint.setTextSize(size);
        float width=paint.measureText(label),left=(w-width-iconSize-gap)/2;
        SportIcons icon=new SportIcons(match.format().sport,(int)iconSize);
        icon.setBounds((int)left,(int)(y-iconSize/2),(int)(left+iconSize),(int)(y+iconSize/2));icon.draw(c);
        text(c,label,left+iconSize+gap+width/2,y,size/fontScale(),updateAvailable?0xff94edb4:palette.id.equals("sunlight")?Color.WHITE:SportIcons.accent(match.format().sport),true);
    }
    private void periodPanel(Canvas c, float w, float top, float bottom, float s) {
        float h=bottom-top, center=(top+bottom)/2;
        float left=w*(round()?.145f:.04f), right=w-left, divider=w*.515f, pad=s*.021f;
        int mainInk=ambient?0xffd6d6d6:Color.WHITE, secondaryInk=ambient?0xffbababa:palette.muted;
        paint.setStyle(Paint.Style.FILL);
        if(!ambient){
            paint.setColor(0xff1c2b3c);
            c.drawRoundRect(left,top,right,bottom,h*.32f,h*.32f,paint);
            paint.setColor(0xff526376);
            c.drawRect(divider,top+h*.19f,divider+Math.max(1f,s*.003f),bottom-h*.19f,paint);
        }
        String unit=periodUnit(true), score=match.gamesWon(0)+"–"+match.gamesWon(1);
        float usable=divider-left-2*pad, gap=s*.017f;
        float labelSize=fittedPixels(unit,s*.057f*fontScale(),usable,h*.64f,false);
        paint.setTextSize(labelSize);
        float labelWidth=paint.measureText(unit);
        float scoreSize=fittedPixels(score,s*.087f*fontScale(),usable,h*.90f,true);
        paint.setTextSize(scoreSize);
        float scoreWidth=paint.measureText(score);
        float fit=Math.min(1f,(usable-gap)/(labelWidth+scoreWidth));
        labelWidth*=fit; scoreWidth*=fit;
        float start=(left+divider-labelWidth-gap-scoreWidth)/2;
        text(c,unit,start+labelWidth/2,center,labelSize*fit/fontScale(),secondaryInk,false);
        text(c,score,start+labelWidth+gap+scoreWidth/2,center,scoreSize*fit/fontScale(),mainInk,true);

        String detail=periodDetail();
        float x=(divider+right)/2, width=right-divider-2*pad;
        if(detail.isEmpty()){
            fittedText(c,periodNumber(),x,center,s*.058f,width,h*.72f,mainInk,true);
        }else{
            fittedText(c,periodNumber(),x,top+h*.28f,s*.055f,width,h*.48f,mainInk,true);
            fittedText(c,detail,x,top+h*.74f,s*.053f,width,h*.46f,secondaryInk,false);
        }
    }
    private void card(Canvas c, float l, float t, float r, float b, int target, String label, String value, int fill, int accent, float s,int server) {
        ScoreState state=match.game();int ink=target==TouchTargets.BLUE?palette.leftInk:palette.rightInk;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(remoteStatus==null&&pressed == target ? accent : fill);
        c.drawRoundRect(new RectF(l,t,r,b), s*.07f, s*.07f, paint);
        if ((match.retired()?match.winner():state.winner()) == target||serverSide(server)==target) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(s*.012f); paint.setColor(0xffebf8ef);
            if(serverSide(server)==target){paint.setColor(palette.outline);paint.setStrokeWidth(s*.008f);}
            c.drawRoundRect(new RectF(l+s*.006f,t+s*.006f,r-s*.006f,b-s*.006f), s*.07f,s*.07f,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        float height=b-t, center=(l+r)/2, width=r-l-s*.045f;
        float labelHeight=height*.19f, footerHeight=height*.21f;
        float labelY=t+dp(3)+labelHeight/2, footerY=b-dp(3)-footerHeight/2;
        String shown=fitLabel(label,width,s*.047f,true);
        fittedText(c,shown,center,labelY,s*.047f,width,labelHeight,ink,true);
        float scoreTop=labelY+labelHeight/2+dp(2),scoreBottom=footerY-footerHeight/2-dp(2);
        drawPoints(c,value,center,scoreTop,scoreBottom,width,s,ink);
        if(serverSide(server)==target)serveBadge(c,l+s*.025f,r-s*.025f,footerY,footerHeight,s,server);
        else if(remoteStatus==null||state.winner()!=ScoreState.NONE||match.retired()){String footer=match.retired()?(match.retiredPlayer()==target?tr("RETIRED"):tr("MATCH WINNER")):state.winner()==ScoreState.NONE?tr("TAP +1"):state.winner()==target?tr("WINNER"):tr("GAME OVER");
            if(match.format().pickleball()&&state.winner()==ScoreState.NONE)footer=tr("TAP RALLY WINNER");
            fittedText(c,footer,center,footerY,s*.037f,width,footerHeight,ink,false);}
    }
    private void drawPoints(Canvas c,String value,float x,float top,float bottom,float width,float s,int ink){
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(s*.29f*fontScale());
        android.graphics.Rect bounds=new android.graphics.Rect();
        paint.getTextBounds(value,0,value.length(),bounds);
        float fit=Math.min(1f,Math.min(width/Math.max(1f,paint.measureText(value)),(bottom-top)/Math.max(1,bounds.height())));
        paint.setTextSize(paint.getTextSize()*fit);
        paint.getTextBounds(value,0,value.length(),bounds);
        paint.setColor(ink);paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(value,x,(top+bottom)/2-bounds.exactCenterY(),paint);
    }
    private int knownServer(){return match.complete()||match.game().winner()!=ScoreState.NONE?-1:match.server();}
    private int serverSide(int slot){return slot<0?-1:slot>=4?slot-4:slot%2;}
    private String serveLabel(int slot){
        if(!match.format().doubles||slot>=4)return tr("SERVING");
        return tr("SERVE · ")+(slot==0?tr("ME"):slot==1?tr("OPP. 1"):slot==2?tr("PARTNER"):tr("OPP. 2"));
    }
    private String serviceAnnouncement(){
        if(match.complete()||match.game().winner()!=ScoreState.NONE)return "";
        int slot=knownServer();if(slot<0)return tr("Serving not set.");
        String name=slot==0?ownLabel:slot==1?match.format().doubles?primaryLabel:opponentLabel:slot==2?partnerLabel:slot==3?otherLabel:slot==4?tr("Our team"):tr("Opposing team");
        return tr("Serving: ")+name+".";
    }
    private void serveBadge(Canvas c,float l,float r,float y,float band,float s,int server){
        String label=serveLabel(server);
        float size=fittedPixels(label,s*.039f*fontScale(),r-l-s*.045f,band-dp(2),true);
        paint.setTextSize(size);
        float width=paint.measureText(label)+s*.045f,center=(l+r)/2;
        paint.setStyle(Paint.Style.FILL);paint.setColor(palette.serve);float half=(paint.descent()-paint.ascent()+dp(2))/2;c.drawRoundRect(new RectF(center-width/2,y-half,center+width/2,y+half),s*.025f,s*.025f,paint);
        text(c,label,center,y,size/fontScale(),palette.serveInk,true);
    }
    private String fitLabel(String value,float maxWidth,float size,boolean bold){
        paint.setTextSize(size*fontScale());paint.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
        if(paint.measureText(value)<=maxWidth)return value;
        int end=value.length();while(end>0&&paint.measureText(value.substring(0,end)+"…")>maxWidth)end=value.offsetByCodePoints(end,-1);
        return value.substring(0,end)+"…";
    }
    private void drawAmbient(Canvas c,float w,float h,float s){
        c.drawColor(Color.BLACK);int shift=(int)((System.currentTimeMillis()/60000)%5)-2;c.save();c.translate(shift,-shift);
        String heading=java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))+" · "+tr(match.format().name());
        fittedText(c,heading,w*.5f,h*.108f,s*.059f,w*.53f,h*.09f,0xffbbbbbb,false);
        periodPanel(c,w,h*.177f,h*.305f,s);
        text(c,fitLabel(ownLabel,w*.36f,s*.04f,false),w*.27f,h*.36f,s*.04f,0xffaaaaaa,false);String label=fitLabel(opponentLabel,w*.36f,s*.04f,false);text(c,label,w*.73f,h*.36f,s*.04f,0xffaaaaaa,false);
        text(c,tr(match.game().displayPoint(0)),w*.27f,h*.51f,s*.20f,0xffbbbbbb,false);
        text(c,tr(match.game().displayPoint(1)),w*.73f,h*.51f,s*.20f,0xffbbbbbb,false);
        int server=knownServer();if(serverSide(server)>=0)text(c,serveLabel(server),w*(serverSide(server)==0?.27f:.73f),h*.63f,s*.031f,0xffbbbbbb,true);
        text(c,remoteStatus!=null?fitLabel(remoteStatus,w*.8f,s*.037f,true):paused?tr("MATCH PAUSED"):match.complete()?tr("MATCH COMPLETE"):sessionActive?tr("MATCH IN PROGRESS"):tr("READY TO PLAY"),w*.5f,h*.72f,s*.037f,0xff999999,false);
        text(c,remoteStatus!=null?tr("Wake to view"):tr("Wake to score"),w*.5f,h*.82f,s*.035f,0xff888888,false);c.restore();
    }
    private void button(Canvas c,float l,float t,float r,float b,String label,int target,boolean enabled,float s) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(pressed==target?palette.leftPressed:enabled?palette.button:palette.id.equals("classic")?0xff123f70:0xff354255);
        c.drawRoundRect(new RectF(l,t,r,b),s*.05f,s*.05f,paint);
        fittedText(c,label,(l+r)/2,(t+b)/2,s*.038f,r-l-dp(5),b-t-dp(3),enabled?palette.buttonInk:0xffd5deeb,true);
    }
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setTextSize(size*fontScale());
        paint.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(value,x,y-(paint.ascent()+paint.descent())/2,paint);
    }
    private int touchTarget(float x,float y){
        if(remoteStatus!=null){
            return x>=0&&x<getWidth()&&y>=0&&y<getHeight()?TouchTargets.MENU:TouchTargets.OUTSIDE;
        }
        return TouchTargets.hit(x/getWidth(),y/getHeight(),getWidth()/density(),getHeight()/density(),round());
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(ambient)return true;
        if (event.getPointerCount() > 1) { pressed = TouchTargets.OUTSIDE; invalidate(); return true; }
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=event.getX(); downY=event.getY(); downTime=event.getEventTime();
                pressed=touchTarget(downX,downY); invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX()-downX,event.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop()*2)
                    pressed=TouchTargets.OUTSIDE;
                invalidate(); return true;
            case MotionEvent.ACTION_UP:
                int target=touchTarget(event.getX(),event.getY());
                boolean tap=target==pressed && target!=TouchTargets.OUTSIDE && TouchTargets.isTap(downX,downY,
                    event.getX(),event.getY(),event.getEventTime()-downTime,ViewConfiguration.get(getContext()).getScaledTouchSlop()*2);
                pressed=TouchTargets.OUTSIDE; invalidate();
                if(tap){
                    boolean remoteTap=remoteStatus!=null;
                    performClick();
                    if(target!=TouchTargets.BLUE&&target!=TouchTargets.RED)performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if(!remoteTap)actions.action(target);
                }
                return true;
            case MotionEvent.ACTION_CANCEL: pressed=TouchTargets.OUTSIDE; invalidate(); return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){ super.performClick(); if(remoteStatus!=null&&!ambient)actions.action(TouchTargets.MENU); return true; }
    private float dp(float value){return value*density();}
}
