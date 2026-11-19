package at.andrew.squashtap;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;

public final class UpdateClient {
    public interface Progress { void update(int percent); }
    private final Context context;
    private final String trustedHost;
    public UpdateClient(Context context){
        this.context=context.getApplicationContext();
        trustedHost=java.net.URI.create(BuildConfig.RELEASE_URL).getHost();
    }
    private HttpsURLConnection connect(String address) throws Exception {
        UpdatePolicy.validateUrl(address,trustedHost);
        HttpsURLConnection connection=(HttpsURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent","KeepScore/"+BuildConfig.VERSION_NAME+" WearOS");
        connection.setRequestProperty("Cache-Control","no-cache");
        int code=connection.getResponseCode();
        if(code>=300&&code<400){ connection.disconnect(); throw new IOException("Update website requires sign-in or redirects. Open it on your phone."); }
        if(code!=200){connection.disconnect();throw new IOException("Update server returned HTTP "+code);}
        return connection;
    }
    public UpdatePolicy check() throws Exception {
        HttpsURLConnection connection=connect(BuildConfig.RELEASE_URL);
        try(InputStream input=connection.getInputStream(); ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] block=new byte[2048]; int n;
            while((n=input.read(block))!=-1){
                if(output.size()+n>16384)throw new IOException("Release response is too large");
                output.write(block,0,n);
            }
            return UpdatePolicy.parse(new String(output.toByteArray(),StandardCharsets.UTF_8),context.getPackageName(),trustedHost);
        }finally{connection.disconnect();}
    }
    public File download(UpdatePolicy release,Progress progress) throws Exception {
        if(!release.newerThan(BuildConfig.VERSION_CODE))throw new IOException("This version is already installed");
        if(!release.supports(Build.VERSION.SDK_INT))throw new IOException("Update needs a newer Android version");
        File partial=new File(context.getCacheDir(),"squash-update.part.apk");
        File complete=new File(context.getCacheDir(),"squash-update.apk");
        if(partial.exists()&&!partial.delete())throw new IOException("Cannot clear incomplete download");
        if(complete.exists()&&!complete.delete())throw new IOException("Cannot clear previous download");
        HttpsURLConnection connection=connect(release.apkUrl);
        boolean accepted=false;
        try(InputStream input=connection.getInputStream(); FileOutputStream output=new FileOutputStream(partial)){
            long headerLength=connection.getContentLengthLong();
            if(headerLength>0&&headerLength!=release.sizeBytes)throw new IOException("Download size differs from release");
            VerifiedDownload.copy(input,output,release.sizeBytes,release.sha256,progress::update);
            output.getFD().sync();
            verifyPackage(partial,release);
            accepted=true;
        }finally{connection.disconnect();if(!accepted)partial.delete();}
        if(!partial.renameTo(complete)){partial.delete();throw new IOException("Cannot save verified update");}
        return complete;
    }
    @SuppressWarnings("deprecation") private void verifyPackage(File apk,UpdatePolicy release) throws Exception {
        PackageManager pm=context.getPackageManager();
        PackageInfo candidate=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo installed=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(candidate==null||!context.getPackageName().equals(candidate.packageName)||candidate.getLongVersionCode()!=release.versionCode
            ||candidate.applicationInfo==null||candidate.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)
            throw new IOException("APK identity or version is invalid");
        if(candidate.signingInfo==null||installed.signingInfo==null||!certificates(candidate).equals(certificates(installed)))
            throw new IOException("APK was not signed with this app's trusted key");
    }
    private Set<String> certificates(PackageInfo info) throws Exception {
        Set<String> values=new HashSet<>();
        for(Signature s:info.signingInfo.getApkContentsSigners())values.add(UpdatePolicy.hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));
        if(values.isEmpty())throw new IOException("Missing APK signing certificate");
        return values;
    }
    public void install(File apk) throws Exception {
        if(!context.getPackageManager().canRequestPackageInstalls())throw new IOException("Allow Keep Score to install updates first");
        PackageInstaller installer=context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());params.setSize(apk.length());
        if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id=installer.createSession(params);boolean committed=false;
        try(PackageInstaller.Session session=installer.openSession(id)){
            try(InputStream input=new FileInputStream(apk); OutputStream output=session.openWrite("base.apk",0,apk.length())){
                byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);session.fsync(output);
            }
            Intent result=new Intent(context,InstallReceiver.class).setAction("at.andrew.squashtap.INSTALL_RESULT");
            int flags=PendingIntent.FLAG_UPDATE_CURRENT;
            if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
            PendingIntent callback=PendingIntent.getBroadcast(context,id,result,flags);
            session.commit(callback.getIntentSender());committed=true;
        }finally{if(!committed)installer.abandonSession(id);}
    }
}
