package at.andrew.squashtap;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Locale;
import java.util.Set;

/** Uses an installed voice only while the watch activity is in the foreground. */
final class ScoreSpeech {
    static final String PREFERENCE = "spoken_scores";
    private final Context context;
    private final Runnable unavailable;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech engine;
    private Locale language;
    private boolean ready, failed, reported;
    private int generation;
    private long utterance;
    private String pending;
    private long pendingAt;

    ScoreSpeech(Context context, Runnable unavailable) {
        this.context = context.getApplicationContext();
        this.unavailable = unavailable;
    }

    void start(Locale locale) {
        if (locale.equals(language)) return;
        close();
        language = locale;
        int opening = generation;
        try {
            // Some engines report failure during construction. Handle it after assignment.
            engine = new TextToSpeech(context, status -> main.post(() -> initialized(opening, status)));
        } catch (RuntimeException unsupported) {
            failed = true;
        }
    }

    private void initialized(int opening, int status) {
        if (opening != generation || engine == null) return;
        try {
            if (status == TextToSpeech.SUCCESS && selectVoice()) {
                engine.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                ready = true;
            }
        } catch (RuntimeException unavailableEngine) {
            ready = false;
        }
        failed = !ready;
        String latest = pending;
        pending = null;
        if (latest != null && SystemClock.uptimeMillis() - pendingAt <= 5000) say(latest);
    }

    private boolean selectVoice() {
        Locale device = Locale.getDefault();
        Locale wanted = device.getLanguage().equals(language.getLanguage()) ? device : language;
        int support = engine.setLanguage(wanted);
        if (support >= TextToSpeech.LANG_AVAILABLE && installed(engine.getVoice())) return true;
        Set<Voice> voices = engine.getVoices();
        if (voices == null) return false;
        Voice best = null;
        for (Voice voice : voices) {
            if (!installed(voice)) continue;
            if (best == null || rank(voice, wanted) > rank(best, wanted)
                    || rank(voice, wanted) == rank(best, wanted) && voice.getName().compareTo(best.getName()) < 0) best = voice;
        }
        return best != null && engine.setVoice(best) == TextToSpeech.SUCCESS;
    }

    private boolean installed(Voice voice) {
        return voice != null && voice.getLocale().getLanguage().equals(language.getLanguage())
                && !voice.isNetworkConnectionRequired() && (voice.getFeatures() == null
                || !voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED));
    }

    private static int rank(Voice voice, Locale wanted) {
        return (voice.getLocale().equals(wanted) ? 10000 : 0) + voice.getQuality();
    }

    void say(String text) {
        if (language == null || text.isEmpty()) return;
        if (!ready && !failed) {
            pending = text;
            pendingAt = SystemClock.uptimeMillis();
            return;
        }
        if (ready) {
            try {
                if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "score-" + (++utterance)) == TextToSpeech.SUCCESS) return;
            } catch (RuntimeException unavailableEngine) {
                // Scoring can continue even if the system's speech service has stopped.
            }
        }
        if (!reported) {
            reported = true;
            unavailable.run();
        }
    }

    void close() {
        ++generation;
        pending = null;
        language = null;
        ready = failed = reported = false;
        if (engine != null) {
            engine.stop();
            engine.shutdown();
            engine = null;
        }
    }
}
