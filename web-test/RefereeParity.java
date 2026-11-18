import at.andrew.squashtap.MatchState;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Synthetic traces from the referee web tests, evaluated by the actual watch scorer. */
class RefereeParity {
    public static void main(String[] args) throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = input.readLine()) != null) {
            String[] parts = line.split("\\t", -1);
            MatchState match = MatchState.decode(parts[0]);
            for (char action : parts[1].toCharArray()) {
                switch (action) {
                    case 'B': match.add(0); break;
                    case 'R': match.add(1); break;
                    case 'N': match.nextGame(); break;
                    case 'U': match.undo(); break;
                    case 'Q': match.retire(1); break;
                    default: throw new IllegalArgumentException("Invalid test action");
                }
            }
            int serving = match.complete() || match.game().winner() >= 0 ? -1 : match.server();
            System.out.println(match.encode()+"\t"+match.game().blue()+","+match.game().red()+"\t"+
                match.gamesWon(0)+","+match.gamesWon(1)+"\t"+match.winner()+"\t"+serving);
        }
    }
}
