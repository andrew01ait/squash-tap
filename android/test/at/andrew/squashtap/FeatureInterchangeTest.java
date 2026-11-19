package at.andrew.squashtap;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class FeatureInterchangeTest {
    public static final String ID="00112233445566778899aabbccddeeff",KEY="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @Test public void extendedBackupEncryptionCannotBeRelabelledAsOrdinaryTransfer()throws Exception{String cipher=TransferCrypto.sealBackup(ID,"out",KEY,"backup ✓");assertEquals("backup ✓",TransferCrypto.open(ID,"out",KEY,cipher));assertThrows(Exception.class,()->TransferCrypto.open(ID,"out",KEY,"1"+cipher.substring(1)));}
    @Test public void archiveCarriesAllFavouritesAndYourName(){MatchPresets p=new MatchPresets();p.add("Club",MatchFormat.defaults("pickleball"),1);Map<String,String> s=new HashMap<>();s.put("favourite_formats",p.encode());s.put("my_name","André");BackupArchive a=BackupArchive.parse(BackupArchive.create(new MatchBook(new MatchState("one",3)),new OpponentDirectory(),s));assertEquals("André",a.settings.get("my_name"));assertEquals(1,MatchPresets.decode(a.settings.get("favourite_formats")).list(null).size());}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("open")){java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=System.in.read(buf))!=-1)b.write(buf,0,n);System.out.print(TransferCrypto.open(ID,"in",KEY,new String(b.toByteArray(),java.nio.charset.StandardCharsets.UTF_8)));return;}
        MatchBook b=new MatchBook(new MatchState("current-backup",3));b.add(0);OpponentDirectory d=new OpponentDirectory();d.assign(b.current().id(),d.add("Jürgen 🏸"));Map<String,String> s=new HashMap<>();s.put("my_name","André");String archive=BackupArchive.create(b,d,s);System.out.print(args.length>0?TransferCrypto.sealBackup(ID,"out",KEY,archive):archive);
    }
}
