package at.andrew.squashtap;
import org.junit.Test;import static org.junit.Assert.*;import java.nio.file.*;import java.util.*;
public class TransferCryptoTest {
 public static final String ID="00112233445566778899aabbccddeeff",KEY=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]),TEXT="{\"message\":\"Grüße 李 🏸\",\"schema\":1}";
 @Test public void unicodeRoundtripUsesRandomAuthenticatedEnvelopes()throws Exception{String a=TransferCrypto.seal(ID,"out",KEY,TEXT),b=TransferCrypto.seal(ID,"out",KEY,TEXT);assertNotEquals(a,b);assertEquals(TEXT,TransferCrypto.open(ID,"out",KEY,a));}
 @Test public void wrongKeyIsRejected()throws Exception{String a=TransferCrypto.seal(ID,"out",KEY,TEXT);assertThrows(Exception.class,()->TransferCrypto.open(ID,"out",TransferCrypto.random(32),a));}
 @Test public void contextPreventsCrossSessionAndDirectionReplay()throws Exception{String a=TransferCrypto.seal(ID,"out",KEY,TEXT);assertThrows(Exception.class,()->TransferCrypto.open(ID,"in",KEY,a));assertThrows(Exception.class,()->TransferCrypto.open("ffeeddccbbaa99887766554433221100", "out",KEY,a));}
 @Test public void tamperingAndTruncationAreRejected()throws Exception{String a=TransferCrypto.seal(ID,"out",KEY,TEXT);String altered=a.substring(0,30)+(a.charAt(30)=='a'?'b':'a')+a.substring(31);assertThrows(Exception.class,()->TransferCrypto.open(ID,"out",KEY,altered));assertThrows(Exception.class,()->TransferCrypto.open(ID,"out",KEY,a.substring(0,a.length()-5)));}
 @Test public void unknownFormatAndOversizedPlaintextAreRejected(){assertThrows(Exception.class,()->TransferCrypto.open(ID,"out",KEY,"2.a.b"));char[] big=new char[TransferCrypto.MAX_PLAIN+1];Arrays.fill(big,'x');assertThrows(Exception.class,()->TransferCrypto.seal(ID,"out",KEY,new String(big)));}
 @Test public void browserGoldenVectorDecryptsOnWatch()throws Exception{Properties p=new Properties();try(java.io.InputStream in=Files.newInputStream(Paths.get("android/test-fixtures/transfer-vector.properties"))){p.load(in);}assertEquals(TEXT,TransferCrypto.open(ID,"out",KEY,p.getProperty("envelope")));}
 @Test public void fingerprintsAreStableButChangeWithState(){assertEquals(TransferCrypto.fingerprint("a"),TransferCrypto.fingerprint("a"));assertNotEquals(TransferCrypto.fingerprint("a"),TransferCrypto.fingerprint("b"));}
 public static void main(String[] args)throws Exception{System.out.print(TransferCrypto.seal(ID,"out",KEY,TEXT));}
}
