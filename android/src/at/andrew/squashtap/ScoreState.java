package at.andrew.squashtap;

/** Single-game PAR-11 scoring. Kept independent of Android for executable tests. */
public final class ScoreState {
    public static final int BLUE = 0, RED = 1, NONE = -1;
    public static final int MAX_RALLIES = 10000;
    private final StringBuilder history = new StringBuilder();
    private int blue, red;
    private boolean entered;

    public int blue() { return blue; }
    public int red() { return red; }
    public int rallies() { return entered?blue+red:history.length(); }
    public boolean entered(){return entered;}
    /** A manually entered completed set has totals, not an invented order of rallies. */
    public static ScoreState completedSet(int blue,int red){
        int high=Math.max(blue,red),low=Math.min(blue,red);
        if(low<0||(long)blue+red>MAX_RALLIES||(low<10?high!=11:high!=low+2))throw new IllegalArgumentException("Enter a final set score: first to 11, win by two");
        ScoreState state=new ScoreState();state.blue=blue;state.red=red;state.entered=true;return state;
    }
    public int winner() {
        if (Math.max(blue, red) < 11 || Math.abs(blue - red) < 2) return NONE;
        return blue > red ? BLUE : RED;
    }
    public boolean deuce() { return blue >= 10 && red >= 10 && winner() == NONE; }
    public boolean add(int player) {
        if (player != BLUE && player != RED) throw new IllegalArgumentException("Unknown player");
        if (winner() != NONE || rallies() >= MAX_RALLIES) return false;
        history.append(player == BLUE ? 'B' : 'R');
        if (player == BLUE) blue++; else red++;
        return true;
    }
    public boolean undo() {
        if(entered)return false;
        if (history.length() == 0) return false;
        if (history.charAt(history.length() - 1) == 'B') blue--; else red--;
        history.setLength(history.length() - 1);
        return true;
    }
    public void reset() { history.setLength(0); blue = red = 0; entered=false; }
    public String encode() { return entered?"2|"+blue+"|"+red:"1|"+history; }
    public static ScoreState decode(String encoded) {
        if(encoded!=null&&encoded.startsWith("2|")){
            if(encoded.length()>16)throw new IllegalArgumentException("Invalid entered score");String[] parts=encoded.split("\\|",-1);
            if(parts.length!=3)throw new IllegalArgumentException("Invalid entered score");return completedSet(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
        }
        if (encoded == null || !encoded.startsWith("1|") || encoded.length() > MAX_RALLIES + 2)
            throw new IllegalArgumentException("Invalid saved score");
        ScoreState state = new ScoreState();
        for (int i = 2; i < encoded.length(); i++) {
            char point = encoded.charAt(i);
            if (point != 'B' && point != 'R') throw new IllegalArgumentException("Invalid rally");
            if (!state.add(point == 'B' ? BLUE : RED)) throw new IllegalArgumentException("Rally after game end");
        }
        return state;
    }
}
