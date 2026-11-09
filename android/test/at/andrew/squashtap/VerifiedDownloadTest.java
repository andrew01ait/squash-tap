package at.andrew.squashtap;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class VerifiedDownloadTest {
    private static final String ABC="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private ByteArrayInputStream input(String text){return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));}
    @Test public void validDownloadCopiesBytes()throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();VerifiedDownload.copy(input("abc"),out,3,ABC,p->{});assertEquals("abc",out.toString("UTF-8"));}
    @Test public void progressReachesOneHundred()throws Exception{final int[] percent={-1};VerifiedDownload.copy(input("abc"),new ByteArrayOutputStream(),3,ABC,p->percent[0]=p);assertEquals(100,percent[0]);}
    @Test(expected=IOException.class)public void rejectsCorruptFile()throws Exception{VerifiedDownload.copy(input("abd"),new ByteArrayOutputStream(),3,ABC,p->{});}
    @Test(expected=IOException.class)public void rejectsTruncatedFile()throws Exception{VerifiedDownload.copy(input("ab"),new ByteArrayOutputStream(),3,ABC,p->{});}
    @Test(expected=IOException.class)public void rejectsExtraBytes()throws Exception{VerifiedDownload.copy(input("abcd"),new ByteArrayOutputStream(),3,ABC,p->{});}
    @Test(expected=IOException.class)public void rejectsEmptyFile()throws Exception{VerifiedDownload.copy(input(""),new ByteArrayOutputStream(),3,ABC,p->{});}
    @Test(expected=IOException.class)public void propagatesConnectionFailure()throws Exception{InputStream broken=new InputStream(){public int read()throws IOException{throw new IOException("Connection lost");}};VerifiedDownload.copy(broken,new ByteArrayOutputStream(),3,ABC,p->{});}
    @Test(expected=IOException.class)public void propagatesStorageFailure()throws Exception{OutputStream broken=new OutputStream(){public void write(int value)throws IOException{throw new IOException("Disk full");}};VerifiedDownload.copy(input("abc"),broken,3,ABC,p->{});}
    @Test(expected=IOException.class)public void refusesOversizedDeclaration()throws Exception{VerifiedDownload.copy(input("abc"),new ByteArrayOutputStream(),UpdatePolicy.MAX_APK_BYTES+1,ABC,p->{});}
}
