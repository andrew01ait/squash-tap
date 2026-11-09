package at.andrew.squashtap;
import org.junit.Test;
import static org.junit.Assert.*;
public class MatchDisplayPolicyTest {
    private MatchState match(){return new MatchState("display",3);}
    private MatchState finished(){MatchState m=match();for(int set=0;set<2;set++){for(int i=0;i<11;i++)m.add(0);if(set==0)m.nextGame();}return m;}
    @Test public void explicitlyStartedZeroScoreMatchIsOngoing(){assertTrue(MatchDisplayPolicy.ongoing(match(),"display",false));}
    @Test public void idleDraftIsNotOngoing(){assertFalse(MatchDisplayPolicy.ongoing(match(),"",false));}
    @Test public void oldUnfinishedScoreDoesNotImplicitlyStartSession(){MatchState m=match();m.add(0);assertFalse(MatchDisplayPolicy.ongoing(m,"",false));}
    @Test public void staleSessionForAnotherMatchIsNotOngoing(){assertFalse(MatchDisplayPolicy.ongoing(match(),"previous",false));}
    @Test public void absentSessionIsSafe(){assertFalse(MatchDisplayPolicy.ongoing(match(),null,false));}
    @Test public void pausedMatchIsNotOngoing(){assertFalse(MatchDisplayPolicy.ongoing(match(),"display",true));}
    @Test public void completedMatchStopsOngoingSession(){assertFalse(MatchDisplayPolicy.ongoing(finished(),"display",false));}
    @Test public void visibleInteractiveMatchStaysAwake(){assertTrue(MatchDisplayPolicy.keepAwake(match(),"display",false,true,true,false,false));}
    @Test public void idleDraftNeverHoldsScreen(){assertFalse(MatchDisplayPolicy.keepAwake(match(),"",false,true,true,false,false));}
    @Test public void differentMatchCannotHoldScreen(){assertFalse(MatchDisplayPolicy.keepAwake(match(),"previous",false,true,true,false,false));}
    @Test public void ambientReleasesInteractiveWakeFlag(){assertFalse(MatchDisplayPolicy.keepAwake(match(),"display",false,true,true,false,true));assertTrue(MatchDisplayPolicy.ongoing(match(),"display",false));}
    @Test public void backgroundReleasesWakeFlagButSessionContinues(){assertFalse(MatchDisplayPolicy.keepAwake(match(),"display",false,true,false,false,false));assertTrue(MatchDisplayPolicy.ongoing(match(),"display",false));}
    @Test public void settingsDoNotForceFullBrightness(){assertFalse(MatchDisplayPolicy.keepAwake(match(),"display",false,true,true,true,false));}
    @Test public void pauseReleasesScreen(){assertFalse(MatchDisplayPolicy.keepAwake(match(),"display",true,true,true,false,false));}
    @Test public void completionReleasesScreen(){assertFalse(MatchDisplayPolicy.keepAwake(finished(),"display",false,true,true,false,false));}
    @Test public void userCanDisableInteractiveKeepAwake(){assertFalse(MatchDisplayPolicy.keepAwake(match(),"display",false,false,true,false,false));}
}
