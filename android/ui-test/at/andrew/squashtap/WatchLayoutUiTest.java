package at.andrew.squashtap;

import android.app.*;import android.content.*;import android.graphics.*;import android.os.Looper;import android.view.*;import android.widget.*;
import java.io.*;import java.util.*;import org.json.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.ActivityController;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowDialog;import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;

/** Native layout audit across watch sizes, shapes and system font scaling. Not an installed device run. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class) @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WatchLayoutUiTest {
    private final File dir=new File("build/qa/profiles");
    @Test @Config(qualifiers="w192dp-h192dp-round-xhdpi") public void smallRound()throws Exception{renderProfile("round-192",192,192,true,1f);}
    @Test @Config(qualifiers="w216dp-h216dp-round-xhdpi") public void mediumRound()throws Exception{renderProfile("round-216",216,216,true,1f);}
    @Test @Config(qualifiers="w233dp-h233dp-round-xhdpi") public void baselineRound()throws Exception{renderProfile("round-233",233,233,true,1f);}
    @Test @Config(qualifiers="w240dp-h240dp-round-xhdpi") public void largeRound()throws Exception{renderProfile("round-240",240,240,true,1f);}
    @Test @Config(qualifiers="w180dp-h180dp-notround-xhdpi") public void smallSquare()throws Exception{renderProfile("square-180",180,180,false,1f);}
    @Test @Config(qualifiers="w240dp-h240dp-notround-xhdpi") public void largeSquare()throws Exception{renderProfile("square-240",240,240,false,1f);}
    @Test @Config(qualifiers="w201dp-h238dp-notround-xhdpi") public void rectangle()throws Exception{renderProfile("rectangle-201x238",201,238,false,1f);}
    @Test @Config(qualifiers="w192dp-h192dp-round-xhdpi") public void smallRoundLargeText()throws Exception{renderProfile("round-192-font130",192,192,true,1.3f);}
    @Test @Config(qualifiers="w240dp-h240dp-round-xhdpi") public void largeRoundLargeText()throws Exception{renderProfile("round-240-font130",240,240,true,1.3f);}
    private List<View> all(View v){List<View> out=new ArrayList<>();out.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out.addAll(all(((ViewGroup)v).getChildAt(i)));return out;}
    private Button button(String title){for(View v:all(ShadowDialog.getLatestDialog().getWindow().getDecorView()))if(v instanceof Button&&((Button)v).getText().toString().equals(title))return (Button)v;throw new AssertionError("Missing "+title);}
    private void click(String label){button(label).performClick();shadowOf(Looper.getMainLooper()).idle();}
    private void save(View view,String profile,String screen,int w,int h)throws Exception{
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);shadowOf(Looper.getMainLooper()).idle();
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);
        Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
        try(FileOutputStream out=new FileOutputStream(new File(dir,profile+"-"+screen+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
    private JSONArray textMetrics(View root,float density)throws Exception{
        JSONArray rows=new JSONArray();
        for(View v:all(root))if(v instanceof TextView){TextView t=(TextView)v;JSONObject row=new JSONObject();row.put("text",t.getText().toString());row.put("type",v.getClass().getSimpleName());row.put("widthDp",v.getWidth()/density);row.put("heightDp",v.getHeight()/density);row.put("fontPixels",t.getTextSize());row.put("lines",t.getLineCount());int[] at=new int[2];v.getLocationInWindow(at);row.put("xDp",at[0]/density);row.put("yDp",at[1]/density);rows.put(row);}
        return rows;
    }
    private void renderProfile(String name,int width,int height,boolean round,float font)throws Exception{
        dir.mkdirs();RuntimeEnvironment.setFontScale(font);assertEquals(font,RuntimeEnvironment.getFontScale(),.001f);
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("squash",0);MatchBook book=new MatchBook(new MatchState("size-audit-current",3));
        for(int i=0;i<9;i++){book.add(0);book.add(1);}book.add(1);
        OpponentDirectory names=new OpponentDirectory();String player=names.add("Alexandra Montgomery-Smith");names.assign(book.current().id(),player);names.add("Pat");
        prefs.edit().clear().putString("match_book",book.encode()).putString("opponent_book",names.encode()).putString(MatchDisplayPolicy.ACTIVE_MATCH,book.current().id()).putString("my_name","Andrew Lindley").putBoolean("asked_match_notification",true).commit();
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();MainActivity activity=controller.get();
        try{
            float density=activity.getResources().getDisplayMetrics().density;int w=Math.round(width*density),h=Math.round(height*density);assertEquals(2f,density,.001f);assertEquals(round,activity.getResources().getConfiguration().isScreenRound());
            JSONObject meta=new JSONObject();meta.put("profile",name);meta.put("widthDp",width);meta.put("heightDp",height);meta.put("round",round);meta.put("fontScale",font);meta.put("density",density);meta.put("qualifiers",RuntimeEnvironment.getQualifiers());
            ScoreView counter=ReflectionHelpers.getField(activity,"counter");save(counter,name,"score",w,h);
            meta.put("scoreAccessibility",counter.getContentDescription());meta.put("scoreTitlePixels",Math.min(w,h)*.042f);for(int target:new int[]{TouchTargets.MENU,TouchTargets.UNDO,TouchTargets.NEW}){float[] area=TouchTargets.area(target,width,height);assertTrue((area[3]-area[1])*height>=47.99f);assertTrue((area[2]-area[0])*width>=47.99f);}
            activity.action(TouchTargets.MENU);click("Current match");View panel=ShadowDialog.getLatestDialog().getWindow().getDecorView();save(panel,name,"current",w,h);meta.put("currentMenu",textMetrics(panel,density));
            for(View v:all(panel))if(v instanceof ScrollView){((ScrollView)v).fullScroll(View.FOCUS_DOWN);shadowOf(Looper.getMainLooper()).idle();break;}
            save(panel,name,"current-bottom",w,h);
            activity.action(TouchTargets.MENU);click("Players");View people=ShadowDialog.getLatestDialog().getWindow().getDecorView();save(people,name,"players",w,h);
            try(FileWriter out=new FileWriter(new File(dir,name+".json"))){out.write(meta.toString(2));}
        }finally{Dialog d=ShadowDialog.getLatestDialog();if(d!=null)d.dismiss();controller.pause().stop().destroy();}
    }
}
