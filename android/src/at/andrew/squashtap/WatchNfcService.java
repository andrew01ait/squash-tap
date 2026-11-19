package at.andrew.squashtap;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.os.SystemClock;

/** Android binds this service for a reader. Only the current foreground offer has content. */
public final class WatchNfcService extends HostApduService {
    static final long WINDOW_MS = 60000;
    private static NfcTagFile offer;
    private static long deadline;

    static synchronized void publish(NfcTagFile file) {
        offer = file;
        deadline = SystemClock.elapsedRealtime() + WINDOW_MS;
    }

    static synchronized void withdraw(NfcTagFile file) {
        if (offer == file) offer = null;
    }

    @Override public byte[] processCommandApdu(byte[] command, Bundle extras) {
        synchronized (WatchNfcService.class) {
            if (offer == null || SystemClock.elapsedRealtime() >= deadline) {
                offer = null;
                return NfcTagFile.status(0x6A82);
            }
            return offer.respond(command);
        }
    }

    @Override public void onDeactivated(int reason) {
        synchronized (WatchNfcService.class) {
            if (offer != null) offer.reset();
        }
    }
}
