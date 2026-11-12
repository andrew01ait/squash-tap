package at.andrew.squashtap;

import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.TypedValue;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

/** Exercise the Play flavor on both API 34 and the required API 35 target. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={34,35},application=Application.class,qualifiers="w233dp-h233dp-round")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class PlayDistributionUiTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private SharedPreferences prefs;
    private String savedBook,savedNames;
    @Before public void launch(){
        assertTrue("This test must use the Play build",BuildConfig.PLAY_DISTRIBUTION);
        MatchBook book=new MatchBook(new MatchState("saved-before-play",3));
        while(!book.current().complete()){for(int i=0;i<11;i++)book.add(0);if(!book.current().complete())book.nextGame();}
        book.newMatch("preserve-play-upgrade",3);
        book.add(0);book.add(1);book.add(0);
        OpponentDirectory names=new OpponentDirectory();String opponent=names.add("Sam");names.assign(book.current().id(),opponent);
        prefs=RuntimeEnvironment.getApplication().getSharedPreferences("squash",0);
        prefs.edit().clear().putString("match_book",book.encode()).putString("opponent_book",names.encode())
            .putString("score_palette","neon-lime").putBoolean("asked_match_notification",true)
            .putString(MatchDisplayPolicy.ACTIVE_MATCH,book.current().id())
            .putString("update_status","Old website update available").commit();
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();
        savedBook=prefs.getString("match_book","");savedNames=prefs.getString("opponent_book","");
    }
    @After public void finish(){Dialog dialog=ShadowDialog.getLatestDialog();if(dialog!=null)dialog.dismiss();if(controller!=null)controller.pause().stop().destroy();}
    private View root(){Dialog d=ShadowDialog.getLatestDialog();assertNotNull(d);assertTrue(d.isShowing());return d.getWindow().getDecorView();}
    private Button find(View v,String text){if(v instanceof Button&&((Button)v).getText().toString().equals(text))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=find(((ViewGroup)v).getChildAt(i),text);if(b!=null)return b;}return null;}
    private void click(String text){Button b=find(root(),text);assertNotNull("Missing "+text,b);b.performClick();}
    private boolean contains(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(contains(((ViewGroup)v).getChildAt(i),text))return true;return false;}
    private void unchanged(){assertEquals(savedBook,prefs.getString("match_book",""));assertEquals(savedNames,prefs.getString("opponent_book",""));assertEquals("neon-lime",prefs.getString("score_palette",""));assertEquals("preserve-play-upgrade",prefs.getString(MatchDisplayPolicy.ACTIVE_MATCH,""));assertFalse(prefs.contains("last_check"));}
    private void updates(){activity.action(TouchTargets.MENU);click("Settings");click("Check for updates");}

    @Test public void launchRetainsExistingDataAndNeverCreatesWebsiteUpdater(){
        assertNull(ReflectionHelpers.getField(activity,"updater"));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10));unchanged();
    }
    @Test public void updateMenuUsesPlayAndContainsNoInstallerControls(){
        updates();assertNotNull(find(root(),"Open Google Play"));assertNotNull(find(root(),"Back to Settings"));
        assertTrue(contains(root(),"Updates are managed by Google Play."));
        for(String label:new String[]{"Download & update","Allow app updates","Set up direct updates","Phone install help","Copy command"})assertNull(find(root(),label));
        assertFalse(contains(root(),"Old website update available"));unchanged();
    }
    @Test public void everyLegacyUpdateEntryPointIsGuarded(){
        for(String method:new String[]{"checkUpdates","downloadUpdate","allowUpdates","showPermissionHelp","phoneHelp"}){
            ReflectionHelpers.callInstanceMethod(activity,method);assertNotNull(find(root(),"Open Google Play"));unchanged();
        }
    }
    @Test public void playStoreIntentUsesSamePackageAndKeepsCurrentMatch(){
        Intent expected=new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=at.andrew.squashtap")).setPackage("com.android.vending");
        ResolveInfo info=new ResolveInfo();info.activityInfo=new ActivityInfo();info.activityInfo.packageName="com.android.vending";info.activityInfo.name="Store";
        shadowOf(activity.getPackageManager()).addResolveInfoForIntent(expected,info);
        updates();click("Open Google Play");Intent actual=shadowOf(activity).getNextStartedActivity();assertNotNull(actual);
        assertEquals(Intent.ACTION_VIEW,actual.getAction());assertEquals(expected.getData(),actual.getData());assertEquals("com.android.vending",actual.getPackage());unchanged();
    }
    @Test public void backFromUpdatesReturnsToSettingsWithoutChangingSession(){
        updates();click("Back to Settings");assertNotNull(find(root(),"Check for updates"));unchanged();
    }
    @Test public void excludedUpdaterHasNoNetworkOrInstallationFallback(){
        UpdateClient client=new UpdateClient(activity);
        try{client.check();fail("Website check must be disabled");}catch(UnsupportedOperationException expected){}
        try{client.download(null,p->{});fail("APK download must be disabled");}catch(UnsupportedOperationException expected){}
        try{client.install(null);fail("APK installation must be disabled");}catch(UnsupportedOperationException expected){}
        unchanged();
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void scoreboardUsesBlackBackgroundForWearQuality(){
        ScoreView score=ReflectionHelpers.getField(activity,"counter");score.measure(View.MeasureSpec.makeMeasureSpec(466,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(466,View.MeasureSpec.EXACTLY));score.layout(0,0,466,466);
        Bitmap image=Bitmap.createBitmap(466,466,Bitmap.Config.ARGB_8888);score.draw(new Canvas(image));assertEquals(Color.BLACK,image.getPixel(0,0));unchanged();
    }
    @Test public void launchThemeUsesBlackAndTheActualLauncherIcon(){
        TypedValue value=new TypedValue();assertTrue(activity.getTheme().resolveAttribute(android.R.attr.windowSplashScreenBackground,value,true));assertEquals(Color.BLACK,value.data);
        assertTrue(activity.getTheme().resolveAttribute(android.R.attr.windowSplashScreenAnimatedIcon,value,true));assertEquals(R.drawable.ic_launcher,value.resourceId);unchanged();
    }
}
