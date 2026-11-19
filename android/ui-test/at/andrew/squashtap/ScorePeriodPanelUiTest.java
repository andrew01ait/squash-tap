package at.andrew.squashtap;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, application=Application.class, qualifiers="w192dp-h192dp-round-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScorePeriodPanelUiTest {
    private static final int DIAMETER=384;
    private static class Label {
        final String text;
        final RectF bounds;
        final RectF ink;
        final float size;
        final int color;
        Label(String text,float x,float baseline,Paint paint){
            this.text=text;size=paint.getTextSize();color=paint.getColor();
            float half=paint.measureText(text)/2;
            bounds=new RectF(x-half,baseline+paint.ascent(),x+half,baseline+paint.descent());
            android.graphics.Rect glyphs=new android.graphics.Rect();
            paint.getTextBounds(text,0,text.length(),glyphs);
            ink=new RectF(glyphs);ink.offset(x-half,baseline);
        }
    }
    private static class Display extends Canvas {
        final List<Label> labels=new ArrayList<>();
        Display(){
            super(Bitmap.createBitmap(DIAMETER,DIAMETER,Bitmap.Config.ARGB_8888));
        }
        @Override public void drawText(String text,float x,float y,Paint paint){
            labels.add(new Label(text,x,y,paint));
            super.drawText(text,x,y,paint);
        }
        Label find(String text){
            for(Label label:labels)if(label.text.equals(text))return label;
            return null;
        }
        Label require(String text){
            Label label=find(text);assertNotNull("Missing visible label "+text,label);return label;
        }
    }
    private ScoreView view(MatchState match,String language){
        Context context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("squash",0).edit().putString(WatchLanguage.PREFERENCE,language).commit();
        ScoreView view=new ScoreView(context,match,ignored->{});
        view.layout(0,0,DIAMETER,DIAMETER);return view;
    }
    private Display render(ScoreView view){
        Display display=new Display();view.draw(display);return display;
    }
    private void insideRoundFace(Label label){
        float r=DIAMETER/2f;
        for(float x:new float[]{label.bounds.left,label.bounds.right})
            for(float y:new float[]{label.bounds.top,label.bounds.bottom})
                assertTrue(label.text+" outside round display",Math.hypot(x-r,y-r)<=r);
    }
    @Test public void smallRoundWatchKeepsFullSetFiguresApartAndAboveTappableScoresInFourLanguages(){
        RuntimeEnvironment.setFontScale(1.3f);
        MatchState match=new MatchState("squash-panel",5);
        assertTrue(match.enterCompletedSet(11,7));assertTrue(match.nextGame());
        String[][] languages={{"en","Games","Game 2/5","Last 11–7"},{"de","Sätze","Satz 2/5","Zuletzt 11–7"},
            {"fr","Manches","Manche 2/5","Préc. 11–7"},{"es","Juegos","Juego 2/5","Último 11–7"}};
        float touchTop=DIAMETER*TouchTargets.area(TouchTargets.BLUE,192,192)[1];
        for(String[] language:languages){
            ScoreView view=view(match,language[0]);Display d=render(view);
            Label unit=d.require(language[1]),tally=d.require("1–0"),game=d.require(language[2]),last=d.require(language[3]);
            Label heading=d.require("Squash".toUpperCase(java.util.Locale.ROOT)+WatchLanguage.get(view.getContext()).text(" · MENU"));
            assertTrue("Set tally below 12sp on a small display",tally.size>=24);
            assertTrue(unit.bounds.right<tally.bounds.left);
            assertTrue(tally.bounds.right<Math.min(game.bounds.left,last.bounds.left));
            assertTrue(game.bounds.bottom<=last.bounds.top+1f);
            for(Label row:new Label[]{unit,tally,game,last}){
                insideRoundFace(row);assertTrue(row.bounds.top>heading.bounds.bottom);
                assertTrue(row.bounds.bottom<touchTop);
            }
            String spoken=view.getContentDescription().toString();
            assertTrue(spoken.contains(language[2].split("/")[0]));
            assertTrue(spoken.contains(WatchLanguage.get(view.getContext()).text("Best of")+" 5"));
        }
    }
    @Test public void tennisAndPadelKeepGamesAndDecidingTieBreakDistinctForScorersAndReaders(){
        for(String sport:new String[]{"tennis","padel"}){
            MatchFormat format=new MatchFormat(sport,sport.equals("padel"),6,"advantage",true);
            MatchState match=new MatchState(sport,3,format);
            assertTrue(match.enterCompletedSet(6,3));assertTrue(match.nextGame());
            for(int i=0;i<4;i++)assertTrue(match.add(1));
            ScoreView view=view(match,"de");
            Display display=render(view);
            display.require("Satz 2/3");display.require("Spiele 0–1");display.require("1–0");
            assertTrue(match.enterCompletedSet(3,6));assertTrue(match.nextGame());assertTrue(match.add(0));
            view.match(match);view.remote("Live",false);display=render(view);
            display.require("Satz 3/3");display.require("1–1");display.require("Match-Tiebreak");
            assertNull(display.find("Spiele 1–0"));
            assertTrue(view.getContentDescription().toString().contains("Match-Tiebreak"));
        }
    }
    @Test public void largeTextLeavesAGapBetweenTheNamePointsAndServeBadge(){
        RuntimeEnvironment.setFontScale(1.3f);
        MatchState match=new MatchState("spacing",3);
        match.serving(0,-1);
        for(int i=0;i<10;i++){match.add(0);match.add(1);}
        ScoreView view=view(match,"de");view.ownName("Alexandria Beispielname");view.opponent("Maximilian Mustername");
        Display screen=render(view);
        Label serving=screen.require("AUFSCHLAG");
        for(Label points:screen.labels){
            if(!points.text.equals("10"))continue;
            Label name=screen.labels.stream().filter(label->label.text.endsWith("…")&&Math.abs(label.bounds.centerX()-points.bounds.centerX())<2f).findFirst().get();
            assertTrue(name.ink.bottom+3<points.ink.top);
            assertTrue(points.ink.bottom+3<serving.ink.top);
            insideRoundFace(name);
        }
        assertEquals(2,screen.labels.stream().filter(label->label.text.equals("10")).count());
    }
    @Test public void finalAndRetirementKeepActualPeriodScoresInEveryPaletteAndAmbient(){
        MatchState match=new MatchState("final",3);
        assertTrue(match.enterCompletedSet(11,5));assertTrue(match.enterCompletedSet(11,7));
        ScoreView view=view(match,"en");view.remote("Final result",true);
        for(String id:ScorePalette.IDS){
            view.palette(id);Display display=render(view);
            assertEquals(Color.WHITE,display.require("2–0").color);display.require("Game 2/3");display.require("Last 11–7");
        }
        view.ambient(true,true);Label ambient=render(view).require("2–0");
        assertEquals(Color.red(ambient.color),Color.blue(ambient.color));
        assertEquals(Color.green(ambient.color),Color.blue(ambient.color));
        MatchState retired=new MatchState("retired",3);
        assertTrue(retired.add(1));assertTrue(retired.retire(0));view.match(retired);
        Display display=render(view);display.require("0–0");display.require("Game 1/3");
        assertNull(display.find("Last 0–1"));
    }
}
