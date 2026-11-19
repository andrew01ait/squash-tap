package at.andrew.squashtap;

import android.content.Context;
import android.os.LocaleList;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/** Watch UI language. Serialized match formats, IDs and player-entered names never pass through this catalog. */
final class WatchLanguage {
    static final String PREFERENCE = "watch_language";
    static final String[] CODES = {"auto", "en", "de", "fr", "es"};
    private static final Pattern ARG = Pattern.compile("\\{(\\d+)\\}");
    private static final WatchLanguage[] instances = new WatchLanguage[CODES.length];
    final String code;
    final Locale locale;
    private final JSONObject labels;

    private WatchLanguage(Context context, int index) {
        code = CODES[index];
        locale = Locale.forLanguageTag(code);
        if (index == 1) {
            labels = new JSONObject();
            return;
        }
        int resource = index == 2 ? R.raw.watch_de : index == 3 ? R.raw.watch_fr : R.raw.watch_es;
        try (InputStream stream = context.getResources().openRawResource(resource)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            labels = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception invalid) {
            throw new IllegalStateException("Unreadable watch language resource", invalid);
        }
    }

    static WatchLanguage get(Context context) {
        String selected = context.getSharedPreferences("squash", Context.MODE_PRIVATE).getString(PREFERENCE, "auto");
        int index = Arrays.asList(CODES).indexOf(selected);
        if (index <= 0) {
            LocaleList preferred = context.getResources().getConfiguration().getLocales();
            for (int i = 0; i < preferred.size() && index <= 0; i++) {
                index = Arrays.asList(CODES).indexOf(preferred.get(i).getLanguage());
            }
            if (index <= 0) index = 1;
        }
        synchronized (instances) {
            if (instances[index] == null) instances[index] = new WatchLanguage(context, index);
            return instances[index];
        }
    }

    String pick(String en, String de, String fr, String es) {
        switch (code) {
            case "de": return de;
            case "fr": return fr;
            case "es": return es;
            default: return en;
        }
    }

    String format(String en, String de, String fr, String es, Object... args) {
        return fill(pick(en, de, fr, es), args);
    }

    String fill(String pattern, Object... args) {
        Matcher match = ARG.matcher(pattern);
        StringBuffer out = new StringBuffer();
        while (match.find()) {
            int index = Integer.parseInt(match.group(1));
            if (index >= args.length) throw new IllegalArgumentException("Missing label argument");
            match.appendReplacement(out, Matcher.quoteReplacement(String.valueOf(args[index])));
        }
        match.appendTail(out);
        return out.toString();
    }

    String text(String english) {
        if (english == null || code.equals("en")) return english;
        int first = 0, end = english.length();
        while (first < end && Character.isWhitespace(english.charAt(first))) first++;
        while (end > first && Character.isWhitespace(english.charAt(end - 1))) end--;
        String key = english.substring(first, end);
        if (!labels.has(key)) return english;
        return english.substring(0, first) + labels.optString(key) + english.substring(end);
    }

    // These values come only from MatchResult/ScoreState's numeric descriptions.
    // Never pass player names, serialized matches or server messages to this formatter.
    private static final Pattern SCORE_TERMS = Pattern.compile(
        "Start not recorded|Not recorded|Unavailable \\(clock changed\\)|Match tie-break|DEUCE / ADVANTAGE|"
        + "· WIN BY TWO|· CAP|· unfinished|· points|\\b(?:Date|Start|End|DEUCE|TO|GAMES|RIGHT|LEFT|SERVE|Game|Set|Me|Opponent|sec)\\b:?");
    String scoreTerms(String value) {
        if (code.equals("en")) return value;
        String complete = text(value);
        if (!complete.equals(value)) return complete;
        Matcher match = SCORE_TERMS.matcher(value);
        StringBuffer out = new StringBuffer();
        while (match.find()) match.appendReplacement(out, Matcher.quoteReplacement(text(match.group())));
        match.appendTail(out);
        String translated=out.toString();
        if (code.equals("fr")||code.equals("es"))
            translated=translated.replaceAll("(\\d{2})\\.(\\d{2})\\.(\\d{4})","$1/$2/$3");
        return translated;
    }

    String periodUnit(MatchFormat format,boolean plural){
        if(format.nested())return text(plural?"Sets":"Set");
        return plural?pick("Games","Sätze","Manches","Juegos"):pick("Game","Satz","Manche","Juego");
    }

    String date(java.time.LocalDate value) {
        return value.format(java.time.format.DateTimeFormatter.ofPattern(
            code.equals("fr")||code.equals("es")?"dd/MM/yyyy":"dd.MM.yyyy",locale));
    }

    String filterDate(java.time.LocalDate value){return code.equals("en")?value.toString():date(value);}

    String status(String value) {
        if(code.equals("en"))return value;
        for(String prefix:new String[]{"Check failed: ","Downloading: ","Update stopped: ","Installation failed (",
                "Transfer service returned ","Update server returned HTTP ","Live service returned ",
                "Missing release field: ","Unsupported match length for "}) {
            if(value.startsWith(prefix))return text(prefix)+text(value.substring(prefix.length()));
        }
        return text(value);
    }

    String[] sortedSports() {
        String[] result = MatchFormat.SPORTS.clone();
        Collator order = Collator.getInstance(locale);
        Arrays.sort(result, (a, b) -> order.compare(text(MatchFormat.name(a)), text(MatchFormat.name(b))));
        return result;
    }

    String rules(MatchFormat format) {
        if (code.equals("en")) return format.description();
        String scoring;
        if (format.nested()) {
            scoring = text(format.deuce.equals("star") ? "Star Point" : format.deuce.equals("no-ad") ? "No-Ad / Golden Point" : "Advantage");
            return text(format.mode()) + " · " + scoring + " · " + text(format.finalTie ? "Match tie-break to 10" : "Full set");
        }
        scoring = format.pickleball() ? text(format.sideOut() ? "Traditional side-out" : format.deuce.equals("rally-2025") ? "Rally 2025 · win on serve" : "Rally 2026 · every rally scores") + " · " : "";
        return text(format.mode()) + " · " + scoring + format("to {0}, win by two", "bis {0}, zwei Punkte Vorsprung", "en {0}, deux points d’écart", "a {0}, dos de diferencia", format.target)
            + (format.sport.equals("badminton") ? format(", cap {0}", ", maximal {0}", ", plafond {0}", ", límite {0}", format.cap()) : "");
    }
}
