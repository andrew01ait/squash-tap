package at.andrew.squashtap;
import android.app.*;import android.content.*;import android.graphics.*;import android.view.*;import java.io.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.ActivityController;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowDialog;import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
/** Native Canvas renders for round-screen inspection; these are not physical watch evidence. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class,qualifiers="w233dp-h233dp-round-xhdpi") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class VisualMultisportUiTest {
 private android.widget.Button find(View v,String text){if(v instanceof android.widget.Button&&((android.widget.Button)v).getText().toString().equals(text))return (android.widget.Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){android.widget.Button found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
 private void click(String text){android.widget.Button b=find(ShadowDialog.getLatestDialog().getWindow().getDecorView(),text);assertNotNull(text,b);b.performClick();}
 private void savePanel(String name)throws Exception{save(ShadowDialog.getLatestDialog().getWindow().getDecorView(),name);}
 private void save(View v,String name)throws Exception{int size=466;v.measure(View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY));v.layout(0,0,size,size);Bitmap image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);v.draw(new Canvas(image));File dir=new File("build/qa");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}}
 @Test public void renderScoreboardsSelectorAndPlayerIcons()throws Exception{
  SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("squash",0);OpponentDirectory names=new OpponentDirectory();String jane=names.add("Jane"),pat=names.add("Pat");MatchBook b=new MatchBook(new MatchState("current",3));
  for(String sport:MatchFormat.SPORTS){MatchFormat f=MatchFormat.defaults(sport);MatchState m=new MatchState("saved-"+sport,3,f);int high=f.nested()?6:f.target;m.enterCompletedSet(high,0);m.enterCompletedSet(high,2);b.importResult(MatchResult.from(m,"2026-11-10",1));if(f.doubles)names.assignTeam(m.id(),pat,jane,"");else names.assign(m.id(),jane);}
  prefs.edit().clear().putString("match_book",b.encode()).putString("opponent_book",names.encode()).putBoolean("asked_match_notification",true).commit();ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();MainActivity activity=controller.get();
  try{
   for(String sport:MatchFormat.SPORTS){MatchState m=new MatchState("visual-"+sport,3,MatchFormat.defaults(sport));m.serving(0,m.format().doubles?1:-1);if(m.format().nested()){for(int i=0;i<3;i++)m.add(0);for(int i=0;i<3;i++)m.add(1);m.add(1);}else{for(int i=0;i<9;i++){m.add(0);m.add(1);}m.add(0);}ScoreView view=new ScoreView(activity,m,t->{});view.opponent(m.format().doubles?"Pat + Jane":"Jane");view.players("Partner","Pat","Jane");view.session(true,false);save(view,"score-"+sport);}
   ReflectionHelpers.callInstanceMethod(activity,"showSports");savePanel("sports");click("Players");savePanel("players");click("Back to sports");click("Tennis");savePanel("sport-menu");click("New Tennis match");savePanel("new-match");click("Deuce: Advantage");savePanel("scoring-list");
   activity.action(TouchTargets.BLUE);activity.action(TouchTargets.MENU);click("Current match");savePanel("current-match");click("Cancel match");savePanel("cancel-match");click("Keep playing");click("Back to Menu");click("Change sport");savePanel("sports-locked");
  }finally{Dialog d=ShadowDialog.getLatestDialog();if(d!=null)d.dismiss();controller.pause().stop().destroy();}
 }
}
