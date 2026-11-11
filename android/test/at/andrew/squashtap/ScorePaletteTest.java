package at.andrew.squashtap;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class ScorePaletteTest {
 private double luminance(int c){double result=0;double[] weight={.2126,.7152,.0722};for(int i=0;i<3;i++){double v=((c>>(16-i*8))&255)/255d;result+=weight[i]*(v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4));}return result;}
 private double contrast(int a,int b){double x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 @Test public void vibrantAndOutdoorScoreTextHasStrongContrast(){for(String id:new String[]{"neon-lime","sunlight"}){ScorePalette p=ScorePalette.get(id);assertTrue(contrast(p.left,p.leftInk)>=7);assertTrue(contrast(p.right,p.rightInk)>=7);assertTrue(contrast(p.serve,p.serveInk)>=7);assertTrue(contrast(p.button,p.buttonInk)>=7);}}
 @Test public void paletteBackupIsValidatedAndUnknownPreferenceFallsBackSafely(){Map<String,String> s=new HashMap<>();s.put("score_palette","neon-lime");BackupArchive a=BackupArchive.parse(BackupArchive.create(new MatchBook(new MatchState("palette",3)),new OpponentDirectory(),s));assertEquals("neon-lime",a.settings.get("score_palette"));s.put("score_palette","custom");assertThrows(IllegalArgumentException.class,()->BackupArchive.validateSettings(s));assertEquals("classic",ScorePalette.get("old-setting").id);}
}
