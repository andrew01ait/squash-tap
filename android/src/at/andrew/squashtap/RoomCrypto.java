package at.andrew.squashtap;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Shared with the referee browser. URL fragments carry independent writer/reader credentials. */
final class RoomCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DIGITS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    static final int MAX_PLAIN = 79000;
    private static final int MAX_ENVELOPE = 106000;

    static String newCode() {
        StringBuilder code = new StringBuilder(10);
        for (int i = 0; i < 10; i++) code.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        return code.toString();
    }

    static String normalizeCode(String input) {
        String value = input == null ? "" : input.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT)
            .replace('O', '0').replace('I', '1').replace('L', '1');
        if (!value.matches("[0-9A-HJKMNP-TV-Z]{10}")) throw new IllegalArgumentException("Enter the ten-character watch code");
        return value;
    }

    static String roomContext(String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("Invalid room identifier");
        return "KeepScore:room:1:" + id;
    }

    static String pairContext(String code) { return "KeepScore:pair:1:" + normalizeCode(code); }
    static String pairKey(String code) throws GeneralSecurityException {
        return encode(MessageDigest.getInstance("SHA-256").digest(pairContext(code).getBytes(StandardCharsets.UTF_8)));
    }

    static String encode(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    static byte[] decode(String value) { return Base64.getUrlDecoder().decode(value); }
    static String secret(String token) {
        if (token == null || !token.matches("[a-zA-Z0-9_-]{43}") || decode(token).length != 32)
            throw new IllegalArgumentException("Invalid room credential");
        return token;
    }

    static String seal(String context, String key, String text) throws GeneralSecurityException {
        byte[] plain = text.getBytes(StandardCharsets.UTF_8);
        if (plain.length > MAX_PLAIN) throw new IllegalArgumentException("Shared score is too large");
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(decode(secret(key)), "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return "1." + encode(iv) + "." + encode(cipher.doFinal(plain));
    }

    static String open(String context, String key, String envelope) throws GeneralSecurityException {
        if (envelope == null || envelope.length() > MAX_ENVELOPE || !envelope.startsWith("1."))
            throw new IllegalArgumentException("Invalid shared score");
        String[] parts = envelope.split("\\.", -1);
        if (parts.length != 3 || parts[1].length() != 16 || parts[2].length() < 22)
            throw new IllegalArgumentException("Invalid shared score");
        byte[] cipherText = decode(parts[2]);
        if (cipherText.length > MAX_PLAIN + 16) throw new IllegalArgumentException("Shared score is too large");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decode(secret(key)), "AES"), new GCMParameterSpec(128, decode(parts[1])));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }

    private RoomCrypto() {}
}
