package at.andrew.squashtap;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
public class UpdatePolicyTest {
    private static final String HOST="updates.example.com", PKG="at.andrew.squashtap";
    private String manifest(){return "schema=1\npackageName="+PKG+"\nversionCode=2\nversionName=0.2.0\nminSdk=30\nsizeBytes=12345\napkUrl=https://"+HOST+"/apks/app.apk\nsha256="+new String(new char[64]).replace('\0','a')+"\nnotes=Hello\n";}
    private UpdatePolicy parse(String value)throws Exception{return UpdatePolicy.parse(value,PKG,HOST);}
    @Test public void validReleaseParses()throws Exception{UpdatePolicy p=parse(manifest());assertEquals("0.2.0",p.versionName);assertEquals(12345,p.sizeBytes);}
    @Test public void updateIsFoundForOldVersion()throws Exception{assertTrue(parse(manifest()).newerThan(1));}
    @Test public void equalVersionDoesNotUpdate()throws Exception{assertFalse(parse(manifest()).newerThan(2));}
    @Test public void downgradeIsNotOffered()throws Exception{assertFalse(parse(manifest()).newerThan(3));}
    @Test public void sdkCompatibilityIsChecked()throws Exception{UpdatePolicy p=parse(manifest());assertFalse(p.supports(29));assertTrue(p.supports(30));assertTrue(p.supports(34));}
    @Test(expected=IllegalArgumentException.class)public void rejectsWrongApp()throws Exception{parse(manifest().replace(PKG,"another.app"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsHttp()throws Exception{parse(manifest().replace("https://","http://"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsOtherHost()throws Exception{parse(manifest().replace("https://"+HOST,"https://evil.example"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsHostSuffixAttack()throws Exception{parse(manifest().replace(HOST+"/",HOST+".evil.example/"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsCredentialsInUrl()throws Exception{parse(manifest().replace("https://","https://user:password@"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsNonstandardPort()throws Exception{parse(manifest().replace(HOST+"/",HOST+":8443/"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsJavascript()throws Exception{parse(manifest().replace("https://"+HOST+"/apks/app.apk","javascript:alert(1)"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsBadChecksum()throws Exception{parse(manifest().replace("sha256=","sha256=X"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsMissingField()throws Exception{parse(manifest().replace("versionCode=2\n",""));}
    @Test(expected=IllegalArgumentException.class)public void rejectsBadVersionNumber()throws Exception{parse(manifest().replace("versionCode=2","versionCode=oops"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsNegativeVersion()throws Exception{parse(manifest().replace("versionCode=2","versionCode=-1"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsZeroSize()throws Exception{parse(manifest().replace("sizeBytes=12345","sizeBytes=0"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsOversizedApk()throws Exception{parse(manifest().replace("sizeBytes=12345","sizeBytes=999999999"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsUnknownSchema()throws Exception{parse(manifest().replace("schema=1","schema=2"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsSigninPage()throws Exception{parse("<!doctype html><title>Sign in</title>");}
    @Test(expected=IllegalArgumentException.class)public void rejectsOversizedManifest()throws Exception{parse(new String(new char[17000]));}
    @Test public void checksumMatchesKnownVector()throws Exception{assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",UpdatePolicy.digest(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))));}
}
