package at.andrew.squashtap;

import android.content.Context;
import android.os.BatteryManager;

/** On-demand whole-watch reading, with no receiver, timer or background sampler. */
final class BatterySnapshot {
    static String describe(Context context){
        BatteryManager battery=context.getSystemService(BatteryManager.class);
        if(battery==null)return "unavailable";
        int level=battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return (level>=0&&level<=100?level+"%":"level unavailable")+(battery.isCharging()?" · charging":" · not charging");
    }
}
