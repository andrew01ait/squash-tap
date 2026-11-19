package at.andrew.squashtap;

import android.app.Application;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Looper;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNfcAdapter;
import org.robolectric.shadows.ShadowCardEmulation;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.shadows.ShadowView;
import org.xmlpull.v1.XmlPullParser;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, application=Application.class)
public class WatchNfcUiTest {
    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte)values[i];
        return result;
    }
    private final byte[] selectNdef = bytes(0,0xA4,4,0,7,0xD2,0x76,0,0,0x85,1,1,0);
    private final byte[] selectFile = bytes(0,0xA4,0,12,2,0xE1,4);

    private byte[] read(WatchNfcService service, byte[] command) {
        byte[] response = service.processCommandApdu(command,null);
        assertArrayEquals(bytes(0x90,0),Arrays.copyOfRange(response,response.length-2,response.length));
        return Arrays.copyOf(response,response.length-2);
    }
    private NdefMessage scan(WatchNfcService service) throws Exception {
        read(service,selectNdef);
        read(service,bytes(0,0xA4,0,12,2,0xE1,3));
        byte[] cc = read(service,bytes(0,0xB0,0,0,15));
        assertArrayEquals(bytes(4,6,0xE1,4),Arrays.copyOfRange(cc,7,11));
        assertEquals((byte)0xFF,cc[14]);
        read(service,selectFile);
        byte[] nlen = read(service,bytes(0,0xB0,0,0,2));
        int length = ((nlen[0]&255)<<8) + (nlen[1]&255);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        while (data.size()<length) {
            int offset = 2 + data.size(), count = Math.min(128,length-data.size());
            data.write(read(service,bytes(0,0xB0,offset>>>8,offset,count)));
        }
        return new NdefMessage(data.toByteArray());
    }

    @Test public void installedServiceDeclaresTheNdefReaderRoute() throws Exception {
        Application app = org.robolectric.RuntimeEnvironment.getApplication();
        PackageManager pm = app.getPackageManager();
        ServiceInfo info = pm.getServiceInfo(new ComponentName(app, WatchNfcService.class), PackageManager.GET_META_DATA);
        assertEquals("android.permission.BIND_NFC_SERVICE", info.permission);
        String ns = "http://schemas.android.com/apk/res/android";
        try (XmlResourceParser xml = info.loadXmlMetaData(pm, "android.nfc.cardemulation.host_apdu_service")) {
            assertNotNull(xml);
            boolean group = false, aid = false;
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.getEventType() != XmlPullParser.START_TAG) continue;
                if ("host-apdu-service".equals(xml.getName())) assertTrue(xml.getAttributeBooleanValue(ns, "requireDeviceUnlock", false));
                if ("aid-group".equals(xml.getName())) group = "other".equals(xml.getAttributeValue(ns, "category"));
                if ("aid-filter".equals(xml.getName())) aid = group && NfcTagFile.AID.equals(xml.getAttributeValue(ns, "name"));
            }
            assertTrue("NDEF must be routable when the app is installed", aid);
        }
    }

    @Test public void splitOverlappingAndExtendedReadsCountOnlyReturnedPayloadInOneContact() {
        List<NfcTagFile.ReadProgress> events = new ArrayList<>();
        NfcTagFile file = new NfcTagFile(bytes(10,11,12,13,14,15), events::add);
        byte[] selectWithoutFci = selectNdef.clone();
        selectWithoutFci[3] = 12;
        assertArrayEquals(bytes(0x90,0), file.respond(selectWithoutFci));
        file.respond(selectFile);
        assertArrayEquals(bytes(0,6,0x90,0), file.respond(bytes(0,0xB0,0,0,0,0,2)));
        assertArrayEquals(bytes(13,14,15,0x90,0), file.respond(bytes(0,0xB0,0,5,3)));
        file.reset();  // Bytes served to the first reader cannot complete a later reader's scan.
        file.respond(selectNdef);
        file.respond(selectFile);
        assertArrayEquals(bytes(10,11,12,13,0x90,0), file.respond(bytes(0,0xB0,0,2,0,0,4)));
        assertArrayEquals(bytes(0x6C,0xF0), file.respond(bytes(0,0xB0,0,0,0,1,1)));
        assertEquals(Arrays.asList(NfcTagFile.ReadProgress.SELECTED), events);
        file.respond(bytes(0,0xB0,0,4,4));
        file.respond(bytes(0,0xB0,0,0,0));
        assertEquals(Arrays.asList(NfcTagFile.ReadProgress.SELECTED, NfcTagFile.ReadProgress.COMPLETE), events);
    }

    private View currentPage() {
        return ShadowDialog.getLatestDialog().getWindow().getDecorView();
    }

    private void click(String label) {
        ArrayList<View> matches = new ArrayList<>();
        currentPage().findViewsWithText(matches, label, View.FIND_VIEWS_WITH_TEXT);
        for (View view : matches) if (view.isClickable() && view.performClick()) return;
        fail("Missing button " + label);
    }

    @Test @Config(qualifiers="de-rAT") public void qrButtonArmsActualSenderAndReportsReadOrTimeout() throws Exception {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = controller.get();
        shadowOf(a.getPackageManager()).setSystemFeature(PackageManager.FEATURE_NFC, true);
        shadowOf(a.getPackageManager()).setSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION, true);
        shadowOf(NfcAdapter.getDefaultAdapter(a)).setEnabled(true);
        String link = "https://example.org/live.html#id=test&k=private";
        WatchNfcService service = new WatchNfcService();
        a.showQr(link, "Audience QR", a::closePanel, "Back");
        click("Seite per NFC teilen");
        assertEquals(new ComponentName(a, WatchNfcService.class), ShadowCardEmulation.getPreferredService());
        read(service, selectNdef);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(ShadowView.innerText(currentPage()).contains("Gerät erkannt"));
        assertEquals(link, scan(service).getRecords()[0].toUri().toString());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertTrue(ShadowView.innerText(currentPage()).contains("Link ausgelesen"));
        assertNull(ShadowCardEmulation.getPreferredService());
        assertArrayEquals(bytes(0x6A,0x82), service.processCommandApdu(selectNdef, null));
        click(a.tr("Try NFC again"));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertTrue(ShadowView.innerText(currentPage()).contains("Kein NFC-Lesegerät"));
        click(a.tr("Try NFC again"));
        read(service, selectNdef);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertTrue(ShadowView.innerText(currentPage()).contains("Auslesen unterbrochen"));
        click(a.tr("Try NFC again"));
        click(a.tr("Back to QR"));
        assertArrayEquals(bytes(0x6A,0x82), service.processCommandApdu(selectNdef, null));
        click("Seite per NFC teilen");
        controller.pause();
        assertArrayEquals(bytes(0x6A,0x82), service.processCommandApdu(selectNdef, null));
        controller.stop().destroy();
    }

    @Test public void browserReadsExactHttpsLinkIncludingPrivateFragmentAcrossChunks() throws Exception {
        String link = "https://example.org/transfer.html#id=example&k=" + new String(new char[620]).replace('\0','x');
        WatchNfcService service = new WatchNfcService();
        NfcTagFile file = new NfcTagFile(new NdefMessage(NdefRecord.createUri(link)).toByteArray());
        WatchNfcService.publish(file);
        assertEquals(link,scan(service).getRecords()[0].toUri().toString());
        assertArrayEquals(bytes(0x69,0x86),service.processCommandApdu(bytes(0,0xD6,0,0,1,0),null));
        service.onDeactivated(0);
        assertArrayEquals(bytes(0x69,0x86),service.processCommandApdu(bytes(0,0xB0,0,0,2),null));
        assertEquals(link,scan(service).getRecords()[0].toUri().toString());
        WatchNfcService.withdraw(file);
        assertArrayEquals(bytes(0x6A,0x82),service.processCommandApdu(selectNdef,null));
    }

    @Test public void transmittedWatchInviteOnlyJoinsAsFollower() throws Exception {
        RoomClientUiTest.Relay relay = new RoomClientUiTest.Relay();
        RoomClient host = new RoomClient(new RoomClientUiTest.MemoryVault(),relay);
        RoomClient follower = new RoomClient(new RoomClientUiTest.MemoryVault(),relay);
        host.prepare(RoomClientUiTest.snapshot(new MatchState("tap-room",3),false),true,true);
        RoomClient.Session started = host.sync();
        WatchNfcService service = new WatchNfcService();
        NfcTagFile file = new NfcTagFile(WatchNfc.watchInvitation(started.joinCode).toByteArray());
        WatchNfcService.publish(file);
        String code = WatchNfc.invitationCode(scan(service));
        assertEquals(started.joinCode,code);
        RoomClient.Session joined = follower.findJoin(code).accept();
        assertEquals("follow",joined.role);
        assertEquals("",joined.refereeLink);
        assertEquals(started.matchId,joined.matchId);
        assertNull(WatchNfc.invitationCode(new NdefMessage(NdefRecord.createUri(started.refereeLink))));
        WatchNfcService.withdraw(file);
    }

    @Test public void readerRejectsUnrelatedAndMalformedRecords() {
        NdefRecord record = WatchNfc.watchInvitation("ABCDE FGHJK").getRecords()[0];
        assertEquals("ABCDEFGHJK", WatchNfc.invitationCode(new NdefMessage(record)));
        assertNull(WatchNfc.invitationCode(null));
        assertNull(WatchNfc.invitationCode(new NdefMessage(record,record)));
        assertNull(WatchNfc.invitationCode(new NdefMessage(NdefRecord.createUri("https://example.org/ABCDEFGHJK"))));
        assertNull(WatchNfc.invitationCode(new NdefMessage(NdefRecord.createExternal("other","watch-room",record.getPayload()))));
        assertNull(WatchNfc.invitationCode(new NdefMessage(NdefRecord.createExternal("at.andrew.squashtap","watch-room","abcdefghjk".getBytes(StandardCharsets.US_ASCII)))));
    }

    @Test public void expiredOrUnselectedOffersDiscloseNoPayload() {
        WatchNfcService service = new WatchNfcService();
        NfcTagFile file = new NfcTagFile(WatchNfc.watchInvitation("ABCDEFGHJK").toByteArray());
        WatchNfcService.publish(file);
        assertArrayEquals(bytes(0x6A,0x82),service.processCommandApdu(selectFile,null));
        read(service,selectNdef);read(service,selectFile);
        assertArrayEquals(bytes(0x6B,0),service.processCommandApdu(bytes(0,0xB0,0x7F,0,2),null));
        assertArrayEquals(bytes(0x67,0),service.processCommandApdu(bytes(0),null));
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61));
        assertArrayEquals(bytes(0x6A,0x82),service.processCommandApdu(bytes(0,0xB0,0,0,2),null));
    }

    @Test public void readerOnlyRunsOnExplicitPageAndStopsOnBackPauseAndTimeout() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = controller.get();
        shadowOf(a.getPackageManager()).setSystemFeature(PackageManager.FEATURE_NFC,true);
        shadowOf(a.getPackageManager()).setSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION,false);
        ShadowNfcAdapter hardware = shadowOf(NfcAdapter.getDefaultAdapter(a));
        hardware.setEnabled(true);
        assertTrue(a.nfc.canRead());assertFalse(a.nfc.canSend());assertFalse(hardware.isInReaderMode());
        a.nfc.receive(code -> fail("Unexpected join"),a::closePanel,"Back");
        assertTrue(hardware.isInReaderMode());
        a.closePanel();
        assertFalse(hardware.isInReaderMode());
        a.nfc.receive(code -> fail("Unexpected join"),a::closePanel,"Back");
        controller.pause();assertFalse(hardware.isInReaderMode());
        controller.resume();assertFalse(hardware.isInReaderMode());
        a.nfc.receive(code -> fail("Unexpected join"),a::closePanel,"Back");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));assertFalse(hardware.isInReaderMode());
        shadowOf(a.getPackageManager()).setSystemFeature(PackageManager.FEATURE_NFC,false);
        shadowOf(a.getPackageManager()).setSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION,true);
        assertFalse(a.nfc.canRead());assertTrue(a.nfc.canSend());
        controller.pause().stop().destroy();
    }
}
