package at.andrew.squashtap;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.tech.Ndef;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

/** Explicit foreground handovers. Browser links and read-only watch invitations use different NDEF records. */
final class WatchNfc {
    private static final String INVITE_TYPE = "at.andrew.squashtap:watch-room";
    private final MainActivity activity;
    private final ComponentName service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::expire;
    private NfcAdapter adapter;
    private CardEmulation cards;
    private NfcTagFile offer;
    private NfcTagFile.ReadProgress readProgress;
    private boolean reading;
    private LinearLayout page;
    private TextView status;
    private Button retry;

    WatchNfc(MainActivity activity) {
        this.activity = activity;
        service = new ComponentName(activity, WatchNfcService.class);
    }

    boolean canSend() {
        return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
    }

    boolean canRead() {
        return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    static NdefMessage watchInvitation(String code) {
        byte[] payload = RoomCrypto.normalizeCode(code).getBytes(StandardCharsets.US_ASCII);
        return new NdefMessage(NdefRecord.createExternal("at.andrew.squashtap", "watch-room", payload));
    }

    static String invitationCode(NdefMessage message) {
        if (message == null || message.getRecords().length != 1) return null;
        NdefRecord record = message.getRecords()[0];
        if (record.getTnf() != NdefRecord.TNF_EXTERNAL_TYPE ||
                !Arrays.equals(record.getType(), INVITE_TYPE.getBytes(StandardCharsets.US_ASCII))) return null;
        byte[] bytes = record.getPayload();
        if (bytes.length != 10) return null;
        String code = new String(bytes, StandardCharsets.US_ASCII);
        try { return code.equals(RoomCrypto.normalizeCode(code)) ? code : null; }
        catch (IllegalArgumentException invalid) { return null; }
    }

    void shareLink(String link, Runnable back, String backLabel) {
        show("Share page via NFC", "Ready to share. Hold the back of the unlocked phone against the watch.",
            new NdefMessage(NdefRecord.createUri(link)), null, back, backLabel);
    }

    void shareInvitation(String code, Runnable back, String backLabel) {
        show("Pair a watch via NFC", "On the other watch, choose Join via NFC and hold the watches together.",
            watchInvitation(code), null, back, backLabel);
    }

    void receive(Consumer<String> join, Runnable back, String backLabel) {
        show("Join via NFC", "On the host, choose Pair a watch via NFC. Hold the watches together to follow the score.",
            null, join, back, backLabel);
    }

    private void show(String title, String instruction, NdefMessage message, Consumer<String> join,
                      Runnable back, String backLabel) {
        LinearLayout shown = activity.roomPanel(title, null);
        page = shown;
        status = activity.paragraph(shown, activity.tr(instruction));
        retry = activity.navigationButton(shown, activity.tr("Try NFC again"),
            () -> show(title, instruction, message, join, back, backLabel));
        retry.setVisibility(View.GONE);
        activity.backButton(shown, backLabel, back);
        try {
            adapter = NfcAdapter.getDefaultAdapter(activity);
            if (adapter == null || !adapter.isEnabled()) {
                stopped("Turn on NFC in the watch settings, then try again.");
                return;
            }
            if (message == null) {
                int flags = NfcAdapter.FLAG_READER_NFC_A;
                adapter.enableReaderMode(activity, tag -> {
                    String code = readCode(tag);
                    main.post(() -> {
                        if (!reading || page != shown || !activity.roomPageVisible(shown)) return;
                        if (code == null) {
                            status.setText(activity.tr("Use the watch invitation on the host. Hold the watches together again."));
                            return;
                        }
                        clear();
                        join.accept(code);
                    });
                }, flags, null);
                reading = true;
            } else {
                boolean browserLink = message.getRecords()[0].getTnf() == NdefRecord.TNF_WELL_KNOWN;
                offer = new NfcTagFile(message.toByteArray(), progress -> main.post(() -> {
                    if (page != shown || offer == null || !activity.roomPageVisible(shown)) return;
                    readProgress = progress;
                    String text = "Device detected. Hold still.";
                    if (progress == NfcTagFile.ReadProgress.COMPLETE) {
                        text = browserLink ? "Link read. Open it on the phone." : "Invitation read by the other watch.";
                    }
                    status.setText(activity.tr(text));
                }));
                WatchNfcService.publish(offer);
                cards = CardEmulation.getInstance(adapter);
                if (!cards.setPreferredService(activity, service) && !cards.isDefaultServiceForAid(service, NfcTagFile.AID)) {
                    stopped("NFC sharing is unavailable here. Use the QR or watch code.");
                    return;
                }
            }
            activity.keepNfcScreenAwake(true);
            main.postDelayed(timeout, WatchNfcService.WINDOW_MS);
        } catch (RuntimeException unavailable) {
            stopped("NFC sharing is unavailable here. Use the QR or watch code.");
        }
    }

    private static String readCode(Tag tag) {
        try {
            Ndef ndef = Ndef.get(tag);
            return ndef == null ? null : invitationCode(ndef.getCachedNdefMessage());
        } catch (RuntimeException removed) { return null; }
    }

    void pause() {
        if (page != null && (offer != null || reading)) stopped("NFC stopped. Tap to try again.");
    }

    private void expire() {
        if (offer == null) {
            pause();
        } else if (readProgress == NfcTagFile.ReadProgress.COMPLETE) {
            disarm();
            retry.setVisibility(View.VISIBLE);
        } else if (readProgress == NfcTagFile.ReadProgress.SELECTED) {
            stopped("Read interrupted. Separate the devices and try again.");
        } else {
            stopped("No NFC reader reached Keep Score. Separate the devices and try again.");
        }
    }

    private void stopped(String reason) {
        disarm();
        status.setText(activity.tr(reason));
        retry.setVisibility(View.VISIBLE);
    }

    void clear() {
        if (page == null) return;
        disarm();
        page = null;
        status = null;
        retry = null;
    }

    private void disarm() {
        main.removeCallbacks(timeout);
        WatchNfcService.withdraw(offer);
        offer = null;
        readProgress = null;
        if (cards != null) {
            try { cards.unsetPreferredService(activity); } catch (RuntimeException unavailable) { }
            cards = null;
        }
        if (reading) {
            reading = false;
            try { adapter.disableReaderMode(activity); } catch (RuntimeException unavailable) { }
        }
        activity.keepNfcScreenAwake(false);
        adapter = null;
    }
}
