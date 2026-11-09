package at.andrew.squashtap;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Properties;

/** Untrusted release metadata is validated before any APK is downloaded. */
public final class UpdatePolicy {
    public static final long MAX_APK_BYTES = 20L * 1024 * 1024;
    public final long versionCode, sizeBytes;
    public final int minSdk;
    public final String versionName, packageName, apkUrl, sha256, notes;

    private UpdatePolicy(Properties p, String expectedPackage, String trustedHost) {
        if (!"1".equals(p.getProperty("schema"))) throw new IllegalArgumentException("Unsupported release format");
        packageName = required(p, "packageName");
        if (!expectedPackage.equals(packageName)) throw new IllegalArgumentException("Release is for a different app");
        versionName = required(p, "versionName");
        if (!versionName.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalArgumentException("Invalid version name");
        versionCode = Long.parseLong(required(p, "versionCode"));
        sizeBytes = Long.parseLong(required(p, "sizeBytes"));
        minSdk = Integer.parseInt(required(p, "minSdk"));
        if (versionCode <= 0 || sizeBytes <= 0 || sizeBytes > MAX_APK_BYTES || minSdk < 1)
            throw new IllegalArgumentException("Invalid release limits");
        apkUrl = required(p, "apkUrl");
        validateUrl(apkUrl, trustedHost);
        sha256 = required(p, "sha256").toLowerCase(Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid download checksum");
        notes = p.getProperty("notes", "A new test version is available.");
        if (notes.length() > 2000) throw new IllegalArgumentException("Release notes are too long");
    }
    public static UpdatePolicy parse(String text, String expectedPackage, String trustedHost) throws IOException {
        if (text == null || text.length() > 16384) throw new IllegalArgumentException("Invalid release response");
        if (text.trim().startsWith("<")) throw new IllegalArgumentException("The update website requires sign-in. Open it on your phone.");
        Properties p = new Properties();
        p.load(new StringReader(text));
        return new UpdatePolicy(p, expectedPackage, trustedHost);
    }
    public boolean newerThan(long installedCode) { return versionCode > installedCode; }
    public boolean supports(int sdk) { return sdk >= minSdk; }
    public static void validateUrl(String value, String trustedHost) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || !uri.getHost().equalsIgnoreCase(trustedHost) || uri.getUserInfo() != null
            || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getFragment() != null)
            throw new IllegalArgumentException("Update download must use the trusted HTTPS server");
    }
    public static String digest(InputStream stream) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] block = new byte[8192];
        int length;
        while ((length = stream.read(block)) != -1) md.update(block, 0, length);
        return hex(md.digest());
    }
    public static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    private static String required(Properties p, String name) {
        String value = p.getProperty(name);
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Missing release field: " + name);
        return value.trim();
    }
}
