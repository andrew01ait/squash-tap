package at.andrew.squashtap;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.URI;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
public class DiagnosticTransferTest {
    @Test public void roundTripPreservesUnicodeLog()throws Exception{
        String report="Squash Tap v0.3.0\nMe 11–9 Opponent\nÜberprüfung: Pause → Resume";
        URI link=URI.create(DiagnosticTransfer.link("https://example.com",report));
        assertEquals("/diagnostics.html",link.getPath());assertNull(link.getQuery());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(GZIPInputStream in=new GZIPInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(link.getFragment().substring(2))))){byte[] b=new byte[256];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}
        assertEquals(report,bytes.toString("UTF-8"));
    }
    @Test public void payloadUsesFragmentInsteadOfHttpQuery()throws Exception{String link=DiagnosticTransfer.link("https://example.com","private log");assertTrue(link.contains("#d="));assertFalse(link.contains("?"));assertFalse(link.contains("private log"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsUnencryptedWebPage()throws Exception{DiagnosticTransfer.link("http://example.com","log");}
    @Test(expected=IllegalArgumentException.class)public void rejectsOversizedReport()throws Exception{DiagnosticTransfer.link("https://example.com",new String(new char[16001]));}
}
