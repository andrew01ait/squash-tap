package at.andrew.squashtap;
import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

/** Brief signals only after accepted scoring; no vibration timer or retained worker. */
public final class MatchHaptics {
    private MatchHaptics(){}
    public static void play(Context c,String mode,String event){
        if(mode.equals("off")||Settings.System.getInt(c.getContentResolver(),Settings.System.HAPTIC_FEEDBACK_ENABLED,1)==0)return;
        Vibrator v=(Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);if(v==null||!v.hasVibrator())return;
        long[] pattern=mode.equals("points")||event.equals("point")?new long[]{0,25}:event.equals("game")?new long[]{0,40,60,40}:event.equals("set")?new long[]{0,70,90,70}:new long[]{0,90,100,90,100,150};
        v.vibrate(VibrationEffect.createWaveform(pattern,-1));
    }
}
