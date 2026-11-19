package at.andrew.squashtap;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.Consumer;

/** A read-only NFC Forum Type 4 tag. The file contains one length-prefixed NDEF message. */
final class NfcTagFile {
    static final String AID = "D2760000850101";
    enum ReadProgress { SELECTED, COMPLETE }
    private static final byte[] NDEF_AID = {(byte)0xD2, 0x76, 0, 0, (byte)0x85, 1, 1};
    private final byte[] contents;
    private final byte[] capability;
    private final int messageLength;
    private final BitSet bytesRead = new BitSet();
    private final Consumer<ReadProgress> listener;
    private ReadProgress progress;
    private boolean applicationSelected;
    private byte[] selectedFile;

    NfcTagFile(byte[] message) {
        this(message, null);
    }

    NfcTagFile(byte[] message, Consumer<ReadProgress> listener) {
        if (message.length == 0 || message.length > 8190) throw new IllegalArgumentException("Invalid NDEF size");
        this.listener = listener;
        messageLength = message.length;
        contents = new byte[Math.max(256, message.length + 2)];
        contents[0] = (byte)(message.length >>> 8);
        contents[1] = (byte)message.length;
        System.arraycopy(message, 0, contents, 2, message.length);
        // Advertise 240-byte chunks so even a full response fits in a short exchange.
        capability = new byte[]{0, 15, 0x20, 0, (byte)0xF0, 0, (byte)0xFF,
            4, 6, (byte)0xE1, 4, (byte)(contents.length >>> 8), (byte)contents.length, 0, (byte)0xFF};
    }

    void reset() {
        applicationSelected = false;
        selectedFile = null;
        bytesRead.clear();
    }

    byte[] respond(byte[] command) {
        if (command == null || command.length < 4) return status(0x6700);
        if (command[0] != 0) return status(0x6E00);
        int p1 = command[2] & 255, p2 = command[3] & 255;
        switch (command[1] & 255) {
            case 0xA4:
                selectedFile = null;
                if (p1 == 4) {
                    reset();
                    applicationSelected = (p2 == 0 || p2 == 12) && selects(command, NDEF_AID);
                    if (applicationSelected && progress == null) notifyProgress(ReadProgress.SELECTED);
                    return status(applicationSelected ? 0x9000 : 0x6A82);
                }
                if (!applicationSelected || p1 != 0 || (p2 != 0 && p2 != 12)) return status(0x6A82);
                if (selects(command, new byte[]{(byte)0xE1, 3})) selectedFile = capability;
                if (selects(command, new byte[]{(byte)0xE1, 4})) selectedFile = contents;
                return status(selectedFile == null ? 0x6A82 : 0x9000);
            case 0xB0:
                int length;
                if (command.length == 5) {
                    length = command[4] & 255;
                    if (length == 0) length = 256;
                } else if (command.length == 7 && command[4] == 0) {
                    length = ((command[5] & 255) << 8) | (command[6] & 255);
                    if (length == 0 || length > 256) return status(0x6CF0);
                } else {
                    return status(0x6700);
                }
                if (selectedFile == null) return status(0x6986);
                int offset = (p1 << 8) | p2;
                if (offset >= selectedFile.length) return status(0x6B00);
                int count = Math.min(length, selectedFile.length - offset);
                byte[] result = new byte[count + 2];
                System.arraycopy(selectedFile, offset, result, 0, count);
                result[count] = (byte)0x90;
                if (selectedFile == contents && progress != ReadProgress.COMPLETE) {
                    // Only actual payload bytes count. Readers can split, repeat or overlap reads.
                    int start = Math.max(2, offset) - 2;
                    int end = Math.min(messageLength, offset + count - 2);
                    if (start < end) {
                        bytesRead.set(start, end);
                        if (bytesRead.nextClearBit(0) >= messageLength) notifyProgress(ReadProgress.COMPLETE);
                    }
                }
                return result;
            case 0xD6:
                return status(0x6986);
            default:
                return status(0x6D00);
        }
    }

    private void notifyProgress(ReadProgress next) {
        progress = next;
        if (listener != null) listener.accept(next);
    }

    private static boolean selects(byte[] command, byte[] name) {
        int end = 5 + name.length;
        if (command.length != end && command.length != end + 1) return false;
        return (command[4] & 255) == name.length && Arrays.equals(Arrays.copyOfRange(command, 5, end), name);
    }

    static byte[] status(int word) {
        return new byte[]{(byte)(word >>> 8), (byte)word};
    }
}
