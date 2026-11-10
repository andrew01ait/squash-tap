package at.andrew.squashtap;

import android.app.Application;import android.graphics.*;import android.view.View;import java.io.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Native rendered regression: player labels must never replace or obscure the serve cue. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class,qualifiers="w233dp-h233dp-round-xhdpi") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ServingVisibilityUiTest {
    private final String longName="WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW";
    private ScoreView view(MatchState match,String name){ScoreView v=new ScoreView(RuntimeEnvironment.getApplication(),match,t->{});v.opponent(name);v.session(true,false);return v;}
    private Bitmap render(ScoreView v,int pixels){v.measure(View.MeasureSpec.makeMeasureSpec(pixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(pixels,View.MeasureSpec.EXACTLY));v.layout(0,0,pixels,pixels);Bitmap image=Bitmap.createBitmap(pixels,pixels,Bitmap.Config.ARGB_8888);v.draw(new Canvas(image));return image;}
    private int gold(Bitmap b,int side){int count=0;for(int y=(int)(b.getHeight()*.54);y<b.getHeight()*.66;y++)for(int x=(int)(b.getWidth()*(side==0?.09:.54));x<b.getWidth()*(side==0?.46:.91);x++)if(b.getPixel(x,y)==0xfff6e7ab)count++;return count;}
    private void save(Bitmap b,String name)throws Exception{File dir=new File("build/qa");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}}
    @Test public void missingAndLongOpponentNamesKeepTheSameIndependentServingBadge()throws Exception{
        MatchState match=new MatchState("serve",3);match.add(1);
        for(int size:new int[]{360,400,466,520}){ScoreView a=view(match,""),b=view(match,longName);Bitmap empty=render(a,size),wide=render(b,size);assertTrue(gold(empty,1)>size);assertEquals(0,gold(empty,0));assertEquals(gold(empty,1),gold(wide,1));
            int x=(int)(size*.54),y=(int)(size*.56),width=(int)(size*.36),height=(int)(size*.10);assertTrue(Bitmap.createBitmap(empty,x,y,width,height).sameAs(Bitmap.createBitmap(wide,x,y,width,height)));
            assertTrue(b.getContentDescription().toString().contains("Serving: "+longName+"."));assertTrue(a.getContentDescription().toString().contains("Serving: OPPONENT."));if(size==466){save(empty,"serve-no-name");save(wide,"serve-long-name");}}
    }
    @Test public void doublesServingRolesRemainVisibleWithMissingOrLongNames()throws Exception{
        for(int server=0;server<4;server++){MatchState match=new MatchState("doubles-"+server,3,MatchFormat.defaults("padel"));match.serving(server,server%2==0?1:0);ScoreView a=view(match,""),b=view(match,longName);a.players("","","");b.players(longName,longName,longName);Bitmap empty=render(a,360),wide=render(b,360);assertTrue(gold(wide,server%2)>360);assertEquals(gold(empty,server%2),gold(wide,server%2));assertEquals(0,gold(wide,1-server%2));assertTrue(b.getContentDescription().toString().contains("Serving: "+(server==0?"Me":longName)+"."));if(server==2)save(render(b,466),"serve-doubles");}
    }
    @Test public void unknownFirstServerAndCompletedGamesDoNotInventAServeCue(){
        MatchState match=new MatchState("unknown",3,MatchFormat.defaults("tennis"));ScoreView v=view(match,longName);Bitmap unknown=render(v,466);assertEquals(0,gold(unknown,0)+gold(unknown,1));assertTrue(v.getContentDescription().toString().contains("Serving not set."));
        match.serving(1,-1);match.retire(0);v.refresh();Bitmap complete=render(v,466);assertEquals(0,gold(complete,0)+gold(complete,1));assertFalse(v.getContentDescription().toString().contains("Serving:"));
    }
    @Test public void markerChangesSideWithTheScoreAndUndoWithoutChangingSavedState(){
        MatchState match=new MatchState("undo",3);match.add(0);ScoreView v=view(match,longName);String before=match.encode();assertTrue(gold(render(v,466),0)>466);assertEquals(before,match.encode());match.add(1);v.refresh();assertTrue(gold(render(v,466),1)>466);assertTrue(match.undo());v.refresh();assertTrue(gold(render(v,466),0)>466);assertEquals(before,match.encode());
    }
    @Test public void ambientRenderingKeepsServiceTextWithLongOrAbsentNames()throws Exception{
        MatchState match=new MatchState("ambient",3);match.add(1);ScoreView a=view(match,""),b=view(match,longName);a.ambient(true,false);b.ambient(true,false);Bitmap empty=render(a,466),wide=render(b,466);int x=245,y=270,w=160,h=50;assertTrue(Bitmap.createBitmap(empty,x,y,w,h).sameAs(Bitmap.createBitmap(wide,x,y,w,h)));int ink=0;for(int yy=y;yy<y+h;yy++)for(int xx=x;xx<x+w;xx++)if((wide.getPixel(xx,yy)&0x00ffffff)!=0)ink++;assertTrue(ink>40);save(wide,"serve-ambient");
    }
}
