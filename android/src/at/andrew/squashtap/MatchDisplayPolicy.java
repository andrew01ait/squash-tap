package at.andrew.squashtap;

/** Separates screen power policy from the ongoing task's background lifetime. */
public final class MatchDisplayPolicy {
    public static final String ACTIVE_MATCH="active_match_id";
    public static boolean ongoing(MatchState match,String activeMatchId,boolean paused){
        return match.id().equals(activeMatchId)&&!match.complete()&&!paused;
    }
    public static boolean keepAwake(MatchState match,String activeMatchId,boolean paused,boolean enabled,boolean resumed,boolean panelOpen,boolean ambient){
        return ongoing(match,activeMatchId,paused)&&enabled&&resumed&&!panelOpen&&!ambient;
    }
}
