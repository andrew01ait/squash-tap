package at.andrew.squashtap;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/** Payload is in a URL fragment, which is never sent with the site's HTTP request. */
public final class DiagnosticTransfer {
    // Includes the entire ASCII URL. This fits QR version 11-L with UTF-8 encoding.
    static final int MAX_WATCH_LINK_BYTES = 318;

    static String excerpt(String text, int maxCodePoints) {
        if (text.codePointCount(0, text.length()) <= maxCodePoints) return text;
        return text.substring(0, text.offsetByCodePoints(0, maxCodePoints - 1)) + "…";
    }

    static String flags(boolean... values) {
        int bits = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i]) bits |= 1 << i;
        }
        return Integer.toString(bits, 36);
    }

    static String watchLink(String site, String header, String state, String trace, String crash, String... extra) throws Exception {
        String suffix = "&s=" + state;
        int textBudget = MAX_WATCH_LINK_BYTES - suffix.length();
        // The last header line is the model. Some firmware supplies a long localized name.
        if (!fits(site, header, textBudget) && header.lastIndexOf('\n') >= 0) {
            header = header.substring(0, header.lastIndexOf('\n'));
        }
        String[] events = trace.trim().isEmpty() ? new String[0] : trace.trim().split("\n");
        String recent = events.length == 0 ? "" : "\n" + events[events.length - 1];
        // Reserve space for the newest event, including one exceptionally long or multilingual line.
        while (!recent.isEmpty() && !fits(site, header + recent, textBudget)) {
            int length = recent.codePointCount(0, recent.length());
            recent = length <= 3 ? "" : excerpt(recent, 3 * length / 4);
        }
        if (!crash.isEmpty() && fits(site, header + "\n" + crash + recent, textBudget)) {
            header += "\n" + crash;
        }
        for (int i = events.length - 2; i >= 0; i--) {
            String candidate = "\n" + events[i] + recent;
            if (!fits(site, header + candidate, textBudget)) break;
            recent = candidate;
        }

        String report = header + recent;
        for (String detail : extra) {
            if (!detail.isEmpty() && fits(site, report + "\n" + detail, textBudget)) report += "\n" + detail;
        }
        String url = link(site, report) + suffix;
        if (url.length() > MAX_WATCH_LINK_BYTES) throw new IllegalArgumentException("Diagnostic header exceeds the watch QR limit");
        return url;
    }

    private static boolean fits(String site, String report, int budget) throws Exception {
        return link(site, report).length() <= budget;
    }

    public static String link(String site,String text)throws Exception{
        java.net.URI origin=java.net.URI.create(site);
        if(!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getUserInfo()!=null||origin.getQuery()!=null||origin.getFragment()!=null)throw new IllegalArgumentException("A trusted HTTPS diagnostic page is required");
        byte[] raw=text.getBytes(StandardCharsets.UTF_8);
        if(raw.length>16000)throw new IllegalArgumentException("Diagnostic report is too long");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(GZIPOutputStream compressed=new GZIPOutputStream(bytes)){compressed.write(raw);}
        String payload=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        if(payload.length()>2300)throw new IllegalArgumentException("Report is too large for one QR code. Use a photo of Diagnostics instead.");
        return site+"/diagnostics.html#d="+payload;
    }
}
