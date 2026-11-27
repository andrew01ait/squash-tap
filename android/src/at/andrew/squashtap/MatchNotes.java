package at.andrew.squashtap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/** Optional personal details for a single match. These do not change the score or player catalog. */
public final class MatchNotes {
    public static final int TEXT_LIMIT = 600;
    public static final int LABEL_LIMIT = 80;
    public static final MatchNotes EMPTY = new MatchNotes("", "", "");

    public final String text;
    public final String team;
    public final String event;

    public MatchNotes(String text, String team, String event) {
        this.text = field(text.replace("\r\n", "\n").replace('\r', '\n'), TEXT_LIMIT, true);
        this.team = field(team, LABEL_LIMIT, false);
        this.event = field(event, LABEL_LIMIT, false);
    }

    private static String field(String value, int limit, boolean multiline) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
                i++;
            } else if (Character.isSurrogate(c) || Character.isISOControl(c) && !(multiline && (c == '\n' || c == '\t'))) {
                throw new IllegalArgumentException("Use plain text for match notes.");
            }
        }
        String trimmed = value.trim();
        if (trimmed.length() > limit) throw new IllegalArgumentException("A match note field is too long.");
        return trimmed;
    }

    public boolean isEmpty() {
        return text.isEmpty() && team.isEmpty() && event.isEmpty();
    }

    // A modified UTF-8 field uses at most three bytes per UTF-16 code unit, plus its length.
    int storageEstimate() {
        return 6 + 3 * (text.length() + team.length() + event.length());
    }

    void write(DataOutputStream out) throws IOException {
        out.writeUTF(text);
        out.writeUTF(team);
        out.writeUTF(event);
    }

    static MatchNotes read(DataInputStream in) throws IOException {
        return new MatchNotes(in.readUTF(), in.readUTF(), in.readUTF());
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof MatchNotes)) return false;
        MatchNotes value = (MatchNotes) other;
        return text.equals(value.text) && team.equals(value.team) && event.equals(value.event);
    }

    @Override public int hashCode() {
        return Objects.hash(text, team, event);
    }
}
