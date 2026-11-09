package at.andrew.squashtap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public final class InstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent result){
        int status=result.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        String message;
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            message="Waiting for the watch's installation confirmation.";
            Intent confirmation=result.getParcelableExtra(Intent.EXTRA_INTENT);
            try{
                if(confirmation==null)throw new IllegalStateException("No installer screen");
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(confirmation);
            }catch(Exception unavailable){message="This firmware cannot open installation confirmation. Update from your phone instead.";}
        }else if(status==PackageInstaller.STATUS_SUCCESS){message="Update installed. Reopen Squash Tap to see the new version.";}
        else{message="Installation failed ("+status+"): "+result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);}
        context.getSharedPreferences("squash",Context.MODE_PRIVATE).edit().putString("install_status",message).apply();
    }
}
