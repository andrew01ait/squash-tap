package at.andrew.squashtap;

import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Short calls for live rallies. Names come from the scorecard being heard. */
final class SpokenScore {
    private static final Pattern NAME_WORD = Pattern.compile("[^\\p{Z}\\s,]+");
    private static final Pattern NAME_SUFFIX = Pattern.compile("(?i)(?:jr\\.?|sr\\.?|ii|iii|iv)");

    private SpokenScore() {}

    /** Names are stored as free text. Accept given-name-first and explicit "family, given" entries. */
    static String forename(String displayName) {
        Matcher word = NAME_WORD.matcher(displayName);
        int comma = displayName.indexOf(',');
        if (comma >= 0 && word.find(comma + 1) && !NAME_SUFFIX.matcher(word.group()).matches()) return word.group();
        return word.find(0) ? word.group() : "";
    }

    static String sideName(WatchLanguage language, int side, boolean doubles, IntFunction<String> players) {
        String player = forename(players.apply(side));
        String opponent = forename(players.apply(1 - side));
        if (!doubles) {
            if (!player.isEmpty() && !player.equalsIgnoreCase(opponent)) return player;
            return language.format("Player {0}", "Spieler {0}", "le joueur {0}", "el jugador {0}", side + 1);
        }
        String partner = forename(players.apply(side + 2));
        if (!player.isEmpty() && !partner.isEmpty()) {
            String otherPartner = forename(players.apply(3 - side));
            boolean samePair = player.equalsIgnoreCase(opponent) && partner.equalsIgnoreCase(otherPartner)
                    || player.equalsIgnoreCase(otherPartner) && partner.equalsIgnoreCase(opponent);
            if (!samePair) return language.format("{0} and {1}", "{0} und {1}", "{0} et {1}", "{0} y {1}", player, partner);
        }
        return language.format("Team {0}", "Team {0}", "l’équipe {0}", "el equipo {0}", side + 1);
    }

    static String remote(WatchLanguage language, MatchState before, MatchState after, IntFunction<String> players) {
        if (before == null || after == null || before == after || !before.id().equals(after.id())
                || !before.format().equals(after.format()) || after.retired() || after.game().entered()) return "";
        boolean correction = before.gameCount() > after.gameCount() || before.retired();
        if (before.gameCount() == after.gameCount()) {
            String previous = before.game().rallyHistory();
            String current = after.game().rallyHistory();
            if (previous.equals(current) && !correction) return "";
            correction |= !current.startsWith(previous);
        } else if (!correction && after.game().rallies() == 0) {
            return "";
        }
        return call(language, before, after, correction, players);
    }

    static String call(WatchLanguage language, MatchState before, MatchState after,
                       boolean correction, IntFunction<String> players) {
        ScoreState game = after.game();
        MatchFormat format = after.format();
        IntFunction<String> sideName = side -> sideName(language, side, format.doubles, players);
        String prefix = correction ? language.pick("Correction. ", "Korrektur. ", "Correction. ", "Corrección. ") : "";
        int winner = game.winner();
        if (winner >= 0) {
            String name = sideName.apply(winner);
            return prefix + (format.nested() ? setWon(language, after, winner, name) : rallyGameWon(language, after, winner, name));
        }
        return prefix + (format.nested() ? pointsInSet(language, after, correction, sideName)
                : rallyScore(language, before, after, correction, sideName));
    }

    private static String pointsInSet(WatchLanguage language, MatchState after, boolean correction, IntFunction<String> sideName) {
        ScoreState game = after.game();
        MatchFormat format = after.format();
        if (!correction && game.lastRallyWinner() >= 0
                && game.currentGamePoints(0) == 0 && game.currentGamePoints(1) == 0) {
            return gameWonInSet(language, after, game.lastRallyWinner(), sideName);
        }
        if (game.tieBreak()) {
            int a = game.matchTie() ? game.blue() : game.currentGamePoints(0);
            int b = game.matchTie() ? game.red() : game.currentGamePoints(1);
            if (a == b) return tiedPoints(language, a);
            return language.format("{0} to {1}, {2}.", "{0} zu {1} für {2}.",
                    "{0} à {1} pour {2}.", "{0} a {1} para {2}.",
                    Math.max(a, b), Math.min(a, b), sideName.apply(a > b ? 0 : 1));
        }
        int server = after.server();
        int first = server < 0 ? 0 : server % 2;
        String a = game.displayPoint(first), b = game.displayPoint(1 - first);
        if (a.equals("AD") || b.equals("AD")) {
            return language.format("Advantage {0}.", "Vorteil {0}.", "Avantage {0}.",
                    "Ventaja para {0}.", sideName.apply(a.equals("AD") ? first : 1 - first));
        }
        if (a.equals("40") && b.equals("40")) {
            if (!game.decidingPoint()) return language.pick("Deuce.", "Einstand.", "Égalité.", "Iguales.");
            if (format.sport.equals("padel")) return format.deuce.equals("star")
                    ? language.pick("Star Point.", "Star Point.", "Point étoile.", "Punto estrella.")
                    : language.pick("Golden Point.", "Golden Point.", "Point en or.", "Punto de oro.");
            return language.pick("Deciding point.", "Entscheidungspunkt.", "Point décisif.", "Punto decisivo.");
        }
        if (a.equals(b)) return tiedPoints(language, tennisPoint(language, a));
        return pair(language, tennisPoint(language, a), tennisPoint(language, b)) + ".";
    }

    private static String rallyScore(WatchLanguage language, MatchState before, MatchState after,
                                     boolean correction, IntFunction<String> sideName) {
        ScoreState game = after.game();
        MatchFormat format = after.format();
        int server = after.server();
        int first = server < 0 ? 0 : server % 2;
        String prefix = "";
        // A following watch can receive several rallies at once. Announce a service event only for one known rally.
        boolean singleRally = !correction && before != null && before.gameCount() == after.gameCount()
                && game.rallies() == before.game().rallies() + 1;
        int previousServer = singleRally ? before.server() : -1;
        boolean changedSide = previousServer >= 0 && server >= 0 && previousServer % 2 != first;
        if (format.pickleball() && server >= 0) {
            if (format.sideOut() && changedSide) {
                prefix = language.pick("Side out. ", "Aufschlagwechsel. ", "Changement de service. ", "Cambio de saque. ");
            } else if (format.sideOut() && format.doubles && previousServer >= 0
                    && before.game().pickleServerNumber() == 1 && game.pickleServerNumber() == 2) {
                prefix = language.pick("Second server. ", "Zweiter Aufschläger. ", "Deuxième serveur. ", "Segundo servidor. ");
            }
            return prefix + game.pickleCall().replace("–", ", ") + ".";
        }
        if (changedSide && (format.sport.equals("squash") || format.sport.equals("badminton"))) {
            prefix = language.pick(format.sport.equals("squash") ? "Hand out. " : "Service over. ",
                    "Aufschlagwechsel. ", "Changement de service. ", "Cambio de saque. ");
        }
        if (format.sport.equals("badminton")) {
            int servingPoints = first == 0 ? game.blue() : game.red();
            int receivingPoints = first == 0 ? game.red() : game.blue();
            if (servingPoints == format.cap() - 1 || servingPoints == format.target - 1 && servingPoints > receivingPoints) {
                return prefix + (after.gamesWon(first) + 1 == after.needed()
                        ? language.format("{0}, match point, {1}.", "{0}, Matchball, {1}.", "{0}, volant de match, {1}.",
                                "{0}, punto de partido, {1}.", servingPoints, receivingPoints)
                        : language.format("{0}, game point, {1}.", "{0}, Satzball, {1}.", "{0}, volant de set, {1}.",
                                "{0}, punto de juego, {1}.", servingPoints, receivingPoints));
            }
        }
        String score = prefix + pair(language, game.displayPoint(first), game.displayPoint(1 - first)) + ".";
        if (format.sport.equals("squash")) {
            if (game.blue() == 10 && game.red() == 10)
                return score + language.pick(" Win by two.", " Zwei Punkte Vorsprung nötig.",
                        " Deux points d’écart nécessaires.", " Se necesitan dos puntos de diferencia.");
            int gameBall = game.gameBall();
            if (gameBall >= 0) {
                boolean forMatch = after.gamesWon(gameBall) + 1 == after.needed();
                if (gameBall == first) return score + (forMatch
                        ? language.pick(" Match ball.", " Matchball.", " Balle de match.", " Bola de partido.")
                        : language.pick(" Game ball.", " Satzball.", " Balle de jeu.", " Bola de juego."));
                return score + (forMatch
                        ? language.format(" Match ball for {0}.", " Matchball für {0}.", " Balle de match pour {0}.", " Bola de partido para {0}.", sideName.apply(gameBall))
                        : language.format(" Game ball for {0}.", " Satzball für {0}.", " Balle de jeu pour {0}.", " Bola de juego para {0}.", sideName.apply(gameBall)));
            }
        }
        return score;
    }

    private static String gameWonInSet(WatchLanguage ui, MatchState match, int winner, IntFunction<String> sideName) {
        ScoreState set = match.game();
        int won = winner == 0 ? set.blue() : set.red();
        int other = winner == 0 ? set.red() : set.blue();
        String name = sideName.apply(winner);
        if (won + other == 1) {
            return ui.format("First game, {0}.", "Erstes Spiel für {0}.",
                    "Premier jeu pour {0}.", "Primer juego para {0}.", name);
        }
        if (won == other) {
            String tied = ui.format("Game, {0}. {1} all.", "Spiel für {0}. Ausgleich, {1} zu {1}.",
                    "Jeu pour {0}. {1} partout.", "Juego para {0}. {1} iguales.", name, won);
            return set.tieBreak() ? tied + ui.pick(" Tie-break.", " Tie-Break.", " Jeu décisif.", " Desempate.") : tied;
        }
        if (won > other) {
            if (match.format().doubles)
                return ui.format("Game, {0}. {1} games to {2}.", "Spiel für {0}. {1} zu {2} im Satz.",
                        "Jeu pour {0}. {1} jeux à {2}.", "Juego para {0}. {1} juegos a {2}.", name, won, other);
            return ui.format("{0} wins the game and leads {1} games to {2}.", "{0} gewinnt das Spiel und führt {1} zu {2}.",
                    "{0} remporte le jeu et mène {1} jeux à {2}.", "{0} gana el juego y va por delante {1} a {2}.", name, won, other);
        }
        String leader = sideName.apply(1 - winner);
        if (match.format().doubles)
            return ui.format("Game, {0}. {1} games to {2} for {3}.", "Spiel für {0}. {1} zu {2} für {3}.",
                    "Jeu pour {0}. {1} jeux à {2} pour {3}.", "Juego para {0}. {1} juegos a {2} para {3}.", name, other, won, leader);
        return ui.format("Game, {0}. {3} leads {1} games to {2}.", "Spiel für {0}. {3} führt {1} zu {2}.",
                "Jeu pour {0}. {3} mène {1} jeux à {2}.", "Juego para {0}. {3} va por delante {1} a {2}.", name, other, won, leader);
    }

    private static String setWon(WatchLanguage ui, MatchState match, int winner, String name) {
        ScoreState set = match.game();
        int won = winner == 0 ? set.blue() : set.red();
        int other = winner == 0 ? set.red() : set.blue();
        if (!match.complete()) {
            return ui.format("Game and set, {0}. {1} games to {2}.", "Spiel und Satz für {0}. {1} zu {2}.",
                    "Jeu et manche pour {0}. {1} jeux à {2}.", "Juego y set para {0}. {1} juegos a {2}.", name, won, other);
        }
        String finish = set.matchTie()
                ? ui.format("Match, {0}. {1} to {2} in the match tie-break.", "Match für {0}. {1} zu {2} im Match-Tie-Break.",
                        "Match pour {0}. {1} à {2} au super jeu décisif.", "Partido para {0}. {1} a {2} en el desempate de partido.", name, won, other)
                : ui.format("Game, set and match, {0}. {1} games to {2}.", "Spiel, Satz und Match für {0}. {1} zu {2}.",
                        "Jeu, set et match pour {0}. {1} jeux à {2}.", "Juego, set y partido para {0}. {1} juegos a {2}.", name, won, other);
        return finish + ui.format(" {0} sets to {1}.", " {0} zu {1} in Sätzen.", " {0} manches à {1}.",
                " {0} sets a {1}.", match.gamesWon(winner), match.gamesWon(1 - winner));
    }

    private static String tennisPoint(WatchLanguage language, String value) {
        return value.equals("0") ? language.pick("love", "null", "zéro", "cero") : value;
    }

    private static String rallyGameWon(WatchLanguage ui, MatchState match, int winner, String name) {
        ScoreState game = match.game();
        String points = ui.format("{0} to {1}.", "{0} zu {1}.", "{0} à {1}.", "{0} a {1}.",
                Math.max(game.blue(), game.red()), Math.min(game.blue(), game.red()));
        String result = match.complete()
                ? ui.format("Match to {0}.", "Match für {0}.", "Match pour {0}.", "Partido para {0}.", name)
                : ui.format("Game to {0}.", "Satz für {0}.", "Manche pour {0}.", "Juego para {0}.", name);
        String call = match.format().sport.equals("badminton") ? result + " " + points : points + " " + result;
        if (!match.complete() || match.bestOf() == 1) return call;
        return call + ui.format(" {0} games to {1}.", " {0} zu {1} in Sätzen.", " {0} manches à {1}.",
                " {0} juegos a {1}.", match.gamesWon(winner), match.gamesWon(1 - winner));
    }

    private static String tiedPoints(WatchLanguage language, Object value) {
        return language.format("{0} all.", "{0} beide.", "{0} partout.", "{0} iguales.", value);
    }

    private static String pair(WatchLanguage language, Object first, Object second) {
        return language.format("{0}, {1}", "{0} zu {1}", "{0} à {1}", "{0} a {1}", first, second);
    }
}
