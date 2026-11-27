package at.andrew.squashtap;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Crash-safe, excluded from Android/Keep Score backups; the device key never leaves the Android Keystore. */
final class RoomFile implements RoomClient.Vault {
    private static final String ALIAS = "at.andrew.squashtap.room-v1";
    private static final byte[] AAD = "KeepScore:local-rooms:1".getBytes(StandardCharsets.UTF_8);
    private static final int LIMIT = 6 * 1024 * 1024;
    interface Keys { SecretKey load(boolean allowCreate) throws Exception; }
    private final AtomicFile file;
    private final Keys keys;
    private SecretKey cachedKey;

    RoomFile(Context context) {
        this(new File(context.getNoBackupFilesDir(), "shared-matches-v1"), RoomFile::key);
    }

    RoomFile(File target, Keys keys) {
        file = new AtomicFile(target);
        this.keys = keys;
    }

    @Override public JSONObject read() throws Exception {
        byte[] sealed;
        // AtomicFile.openRead also restores a legacy backup after an interrupted rename.
        try (FileInputStream in = file.openRead()) {
            if (in.getChannel().size() > LIMIT) throw new IOException("Shared match storage is too large");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            int n;
            while ((n = in.read(block)) != -1) bytes.write(block, 0, n);
            sealed = bytes.toByteArray();
        } catch (FileNotFoundException missing) {
            if (file.getBaseFile().exists()) throw missing;
            return new JSONObject();
        }
        if (sealed.length < 29 || sealed[0] != 1) throw new IOException("Invalid shared match storage");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deviceKey(false), new GCMParameterSpec(128, Arrays.copyOfRange(sealed, 1, 13)));
        cipher.updateAAD(AAD);
        return new JSONObject(new String(cipher.doFinal(sealed, 13, sealed.length - 13), StandardCharsets.UTF_8));
    }

    @Override public void write(JSONObject value) throws Exception {
        if (value.length() == 0) {
            file.delete();
            if (file.getBaseFile().exists()) throw new IOException("Could not remove shared match data");
            return;
        }
        byte[] plain = value.toString().getBytes(StandardCharsets.UTF_8);
        if (plain.length + 29 > LIMIT) throw new IOException("Shared match storage is full");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        // AndroidKeyStore supplies a fresh IV; caller-provided IVs are disallowed for encryption.
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey(true));
        cipher.updateAAD(AAD);
        byte[] iv = cipher.getIV(), encrypted = cipher.doFinal(plain);
        if (iv.length != 12) throw new IOException("Unexpected room key parameters");
        FileOutputStream out = file.startWrite();
        try {
            out.write(1);
            out.write(iv);
            out.write(encrypted);
            out.getFD().sync();
            file.finishWrite(out);
        } catch (Exception e) {
            file.failWrite(out);
            throw e;
        }
    }

    private SecretKey deviceKey(boolean create) throws Exception {
        if (cachedKey == null) cachedKey = keys.load(create);
        return cachedKey;
    }

    private static SecretKey key(boolean allowCreate) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey)store.getKey(ALIAS, null);
        if (!allowCreate) throw new IOException("Shared match device key is unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}
