package at.andrew.squashtap;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Browser-compatible authenticated encryption; the key travels only in a QR URL fragment. */
public final class TransferCrypto {
    public static final int MAX_PLAIN=4*1024*1024,MAX_COMPRESSED=1024*1024;
    public static final int MAX_BACKUP_PLAIN=24*1024*1024,MAX_BACKUP_COMPRESSED=4*1024*1024;
    private static final Base64.Encoder ENCODER=Base64.getUrlEncoder().withoutPadding();
    public static String random(int bytes){byte[] value=new byte[bytes];new SecureRandom().nextBytes(value);return ENCODER.encodeToString(value);}
    public static String fingerprint(String value){try{return ENCODER.encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static byte[] aad(String id,String direction){if(!id.matches("[a-f0-9]{32}")||!(direction.equals("out")||direction.equals("in")))throw new IllegalArgumentException("Invalid transfer context");return ("squashtap-transfer-v1|"+id+"|"+direction).getBytes(StandardCharsets.UTF_8);}
    public static String seal(String id,String direction,String key,String text)throws Exception{
        return seal(id,direction,key,text,false);
    }
    public static String sealBackup(String id,String direction,String key,String text)throws Exception{return seal(id,direction,key,text,true);}
    private static String seal(String id,String direction,String key,String text,boolean backup)throws Exception{
        byte[] plain=text.getBytes(StandardCharsets.UTF_8);if(plain.length>(backup?MAX_BACKUP_PLAIN:MAX_PLAIN))throw new IllegalArgumentException("Transfer is too large. Nothing was omitted.");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(GZIPOutputStream zip=new GZIPOutputStream(bytes)){zip.write(plain);}if(bytes.size()>(backup?MAX_BACKUP_COMPRESSED:MAX_COMPRESSED))throw new IllegalArgumentException("Transfer is too large. Nothing was omitted.");
        byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");byte[] secret=Base64.getUrlDecoder().decode(key);if(secret.length!=32)throw new IllegalArgumentException("Invalid encryption key");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(secret,"AES"),new GCMParameterSpec(128,iv));cipher.updateAAD(aad(id,direction));if(backup)cipher.updateAAD("|backup-v1".getBytes(StandardCharsets.UTF_8));return (backup?"2.":"1.")+ENCODER.encodeToString(iv)+"."+ENCODER.encodeToString(cipher.doFinal(bytes.toByteArray()));
    }
    public static String open(String id,String direction,String key,String envelope)throws Exception{
        if(envelope.length()>6000000)throw new IllegalArgumentException("Transfer is too large");String[] parts=envelope.split("\\.");if(parts.length!=3||!(parts[0].equals("1")||parts[0].equals("2")))throw new IllegalArgumentException("Unknown transfer format");boolean backup=parts[0].equals("2");if(!backup&&envelope.length()>1500000)throw new IllegalArgumentException("Transfer is too large");
        byte[] iv=Base64.getUrlDecoder().decode(parts[1]),secret=Base64.getUrlDecoder().decode(key),sealed=Base64.getUrlDecoder().decode(parts[2]);if(iv.length!=12||secret.length!=32||sealed.length>(backup?MAX_BACKUP_COMPRESSED:MAX_COMPRESSED)+16)throw new IllegalArgumentException("Invalid transfer");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(secret,"AES"),new GCMParameterSpec(128,iv));cipher.updateAAD(aad(id,direction));if(backup)cipher.updateAAD("|backup-v1".getBytes(StandardCharsets.UTF_8));byte[] compressed=cipher.doFinal(sealed);
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(GZIPInputStream zip=new GZIPInputStream(new ByteArrayInputStream(compressed))){byte[] buffer=new byte[8192];int n;while((n=zip.read(buffer))!=-1){if(out.size()+n>(backup?MAX_BACKUP_PLAIN:MAX_PLAIN))throw new IllegalArgumentException("Expanded transfer is too large");out.write(buffer,0,n);}}return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
}
