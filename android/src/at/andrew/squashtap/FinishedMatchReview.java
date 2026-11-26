package at.andrew.squashtap;

import android.content.SharedPreferences;
import java.util.concurrent.TimeUnit;

/** Persists a saved result's review independently of its score and sharing state. */
final class FinishedMatchReview {
    static final long TIMEOUT = TimeUnit.HOURS.toMillis(3);
    private static final String REVIEW = "finished_review";
    private static final String LAST_USED = "finished_review_last_used";
    private static final String CLOSED = "finished_review_closed";
    private static final String SHARED = "finished_review_shared";
    private static final String ANCHOR = "finished_review_local_match";

    private static String key(MatchResult result) {
        return result.id + "@" + result.finishedAt;
    }

    static MatchResult result(SharedPreferences prefs, MatchBook book) {
        String current = book.current().id();
        if (current.equals(prefs.getString(ANCHOR, ""))) {
            MatchResult shared = book.result(prefs.getString(SHARED, ""));
            if (shared != null) return shared;
        }
        return book.current().complete() ? book.result(current) : null;
    }

    static boolean available(SharedPreferences prefs, MatchBook book) {
        MatchResult result = result(prefs, book);
        return result != null && !key(result).equals(prefs.getString(CLOSED, ""));
    }

    static void openShared(SharedPreferences prefs, MatchBook book, String id, long now) {
        MatchResult shared = book.result(id);
        if (shared == null) return;
        String current = book.current().id();
        SharedPreferences.Editor edit = prefs.edit();
        MatchResult previous = book.result(current);
        if (previous != null && !current.equals(id)) edit.putString(CLOSED, key(previous));
        edit.putString(ANCHOR, current).putString(SHARED, id)
                .putString(REVIEW, key(shared)).putLong(LAST_USED, now).apply();
    }

    static long lastUsed(SharedPreferences prefs, MatchBook book) {
        MatchResult result = result(prefs, book);
        if (result == null) return 0;
        return key(result).equals(prefs.getString(REVIEW, ""))
                ? Math.max(result.finishedAt, prefs.getLong(LAST_USED, 0)) : result.finishedAt;
    }

    static boolean expired(SharedPreferences prefs, MatchBook book, long now) {
        long last = lastUsed(prefs, book);
        return last > 0 && now - last >= TIMEOUT;
    }

    static void touch(SharedPreferences prefs, MatchBook book, long now) {
        MatchResult result = result(prefs, book);
        if (result != null) prefs.edit().putString(REVIEW, key(result)).putLong(LAST_USED, now).apply();
    }

    static void close(SharedPreferences prefs, MatchBook book) {
        MatchResult result = result(prefs, book);
        if (result == null) return;
        SharedPreferences.Editor edit = prefs.edit().remove(SHARED).remove(ANCHOR);
        if (result.id.equals(book.current().id())) edit.putString(CLOSED, key(result));
        edit.apply();
    }
}
