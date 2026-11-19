package at.andrew.squashtap;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Import a confirmed result from the selected player's perspective into cloned local books. */
final class SharedResult {
    private SharedResult() {}

    // For each seat, the first opponent, partner and second opponent in the local directory.
    private static final int[][] ROLES = {{1,2,3},{0,3,2},{1,0,3},{0,1,2}};

    static MatchResult save(MatchBook book, OpponentDirectory names, MatchState match, String[] labels,
            long start, long finish, String zone, int seat, boolean host) {
        if (!match.complete() || labels.length != 4 || seat < 0 || seat >= (match.format().doubles ? 4 : 2) || host && seat != 0)
            throw new IllegalArgumentException("Invalid shared result");
        MatchResult existing = book.result(match.id());
        if (existing != null) return existing;
        if (book.current().id().equals(match.id()) != host) throw new IllegalArgumentException("Different current match");
        if (host && book.currentDeleted()) throw new IllegalStateException("Result was deleted");
        String timingZone = start > 0 || finish > 0 ? zone : "";
        long known = finish > 0 ? finish : start;
        LocalDate date = known > 0 ? Instant.ofEpochMilli(known).atZone(ZoneId.of(zone)).toLocalDate() : LocalDate.now();
        MatchResult original = MatchResult.from(match, date.toString(), 1, start, finish, timingZone);
        if (host) {
            // Keep the original directory identities. A confirmed shared result is immutable,
            // so the next local scoreboard must not offer Undo on the remote match.
            book.newMatch(UUID.randomUUID().toString(), match.bestOf(), match.format());
        } else {
            int[] roles = ROLES[seat];
            String first = names.importOpponent("", labels[roles[0]]);
            String partner = match.format().doubles ? names.importOpponent("", labels[roles[1]]) : "";
            String second = match.format().doubles ? names.importOpponent("", labels[roles[2]]) : "";
            // A name shared by two people cannot identify either historical role.
            boolean firstAmbiguous=!first.isEmpty() && (first.equals(partner) || first.equals(second));
            boolean partnerAmbiguous=!partner.isEmpty() && (partner.equals(first) || partner.equals(second));
            boolean secondAmbiguous=!second.isEmpty() && (second.equals(first) || second.equals(partner));
            names.assignTeam(match.id(),firstAmbiguous?"":first,partnerAmbiguous?"":partner,secondAmbiguous?"":second);
        }
        if (seat % 2 == 0) return book.importResult(original);
        int[][] scores = new int[original.gameCount()][2], detail = new int[original.gameCount()][6];
        for (int i = 0; i < scores.length; i++) {
            for (int side = 0; side < 2; side++) {
                scores[i][side] = original.points(i,1-side);
                detail[i][side] = original.rallyPoints(i,1-side);
                detail[i][side+2] = original.tiePoints(i,1-side);
                detail[i][side+4] = original.unfinishedPoints(i,1-side);
            }
        }
        return book.importResult(new MatchResult(original.id, original.date, 1, original.bestOf, scores,
            start, finish, timingZone, original.retired() ? 1-original.retiredPlayer : -1, original.format, detail));
    }
}
