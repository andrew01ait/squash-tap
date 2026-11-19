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
        assertEquals(report, decode(link));
    }
    private static final String SITE = "https://squash-tap-andrew.endruuu.chatgpt.site";

    private static final String STATE = "1.de.6cu.72.0.0.0.0.3.9";

    private static String decode(URI url) throws IOException {
        byte[] compressed = Base64.getUrlDecoder().decode(url.getFragment().split("&", 2)[0].substring(2));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[256];
            int count;
            while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
        }
        return bytes.toString("UTF-8");
    }

    @Test public void watchExportDropsOldestEventsToFitTheCompleteUrl() throws Exception {
        String header = "Keep Score v1 (1)\n2026-02-01T10:30:00\nDemo Watch · API 34";
        String newest = "10:30:00 NFC share: commands=0 mapping=0 stage=waiting";
        String route = "10:29:59 NFC share: foreground=true defaultV2=true defaultV1=true";
        StringBuilder trace = new StringBuilder();
        java.util.Random random = new java.util.Random(1);
        for (int i = 0; i < 10; i++) {
            byte[] bytes = new byte[64];
            random.nextBytes(bytes);
            trace.append("older-").append(i).append(' ').append(Base64.getEncoder().encodeToString(bytes)).append('\n');
        }
        trace.append(route).append('\n').append(newest).append('\n');
        String link = DiagnosticTransfer.watchLink(SITE, header, STATE, trace.toString(), "");
        assertTrue(link.getBytes("UTF-8").length <= 318);
        String report = decode(URI.create(link));
        assertTrue(link.endsWith("&s=" + STATE));
        assertTrue(report.startsWith(header));
        assertTrue(report.contains(route + "\n" + newest));
        assertFalse(report.contains("older-0"));
    }

    @Test public void longUnicodeEventKeepsItsBeginningAndDoesNotBreakTheExport() throws Exception {
        StringBuilder event = new StringBuilder("11:02:03 New error ");
        for (int i = 0; i < 115; i++) event.appendCodePoint(0x1F600 + i);
        String link = DiagnosticTransfer.watchLink(SITE, "Keep Score v1 (1)\nAPI 34", STATE, event.toString(), "");
        String report = decode(URI.create(link));
        assertTrue(link.getBytes("UTF-8").length <= 318);
        assertTrue(report.contains("11:02:03 New error 😀"));
        assertTrue(report.endsWith("…"));
        assertFalse(report.contains("\uFFFD"));
    }

    @Test public void sparseReportIncludesCrashAndCurrentInformation() throws Exception {
        String header = "Keep Score v1 (1)\nAPI 34";
        String link = DiagnosticTransfer.watchLink(SITE, header, STATE, "", "Crash: Demo", "", "Exit: App updated");
        assertEquals(header + "\nCrash: Demo\nExit: App updated", decode(URI.create(link)));
    }
    @Test public void payloadUsesFragmentInsteadOfHttpQuery()throws Exception{String link=DiagnosticTransfer.link("https://example.com","private log");assertTrue(link.contains("#d="));assertFalse(link.contains("?"));assertFalse(link.contains("private log"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsUnencryptedWebPage()throws Exception{DiagnosticTransfer.link("http://example.com","log");}
    @Test(expected=IllegalArgumentException.class)public void rejectsOversizedReport()throws Exception{DiagnosticTransfer.link("https://example.com",new String(new char[16001]));}
}
