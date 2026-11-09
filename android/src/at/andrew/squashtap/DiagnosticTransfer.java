package at.andrew.squashtap;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/** Payload is in a URL fragment, which is never sent with the site's HTTP request. */
public final class DiagnosticTransfer {
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
