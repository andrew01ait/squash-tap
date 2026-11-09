package at.andrew.squashtap;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** The same streaming verifier is exercised by local tests and the watch downloader. */
public final class VerifiedDownload {
    public interface Progress { void update(int percent); }
    public static void copy(InputStream input,OutputStream output,long expected,String checksum,Progress progress)throws Exception{
        if(expected<=0||expected>UpdatePolicy.MAX_APK_BYTES)throw new IOException("Invalid download size");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] buffer=new byte[8192];int n,last=-1;long total=0;
        while((n=input.read(buffer))!=-1){
            if(Thread.currentThread().isInterrupted())throw new IOException("Download cancelled");
            total+=n;
            if(total>expected)throw new IOException("Download exceeds expected size");
            output.write(buffer,0,n);digest.update(buffer,0,n);
            int percent=(int)(total*100/expected);
            if(last<0||percent/5!=last/5){last=percent;progress.update(percent);}
        }
        if(total!=expected)throw new IOException("Download incomplete. Please retry.");
        if(!MessageDigest.isEqual(UpdatePolicy.hex(digest.digest()).getBytes(StandardCharsets.US_ASCII),checksum.getBytes(StandardCharsets.US_ASCII)))
            throw new IOException("Download checksum did not match");
    }
}
