package at.andrew.squashtap;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class BackupArchiveTest {
    @Test public void completeArchiveRetainsCurrentRalliesUndoPeopleAndPreferences(){MatchBook b=new MatchBook(new MatchState("current-backup",3));b.add(0);b.add(1);OpponentDirectory d=new OpponentDirectory();String id=d.add("Jürgen 🏸");d.assign(b.current().id(),id);Map<String,String> s=new TreeMap<>();s.put("my_name","Andrew");s.put("match_ready_id",b.current().id());s.put("selected_sport","squash");s.put("match_keep_awake_v3","true");s.put("last_best_of","3");BackupArchive a=BackupArchive.parse(BackupArchive.create(b,d,s));assertEquals(b.encode(),a.book.encode());assertEquals(d.encode(),a.directory.encode());assertEquals(s,a.settings);a.book.undo();assertEquals(0,a.book.current().game().red());assertEquals(1,a.book.current().game().blue());}
    @Test public void checksumRejectsEditedData(){String j=BackupArchive.create(new MatchBook(new MatchState("empty",3)),new OpponentDirectory(),Collections.emptyMap());int i=j.indexOf("payload\":\"")+10;String changed=j.substring(0,i)+(j.charAt(i)=='A'?'B':'A')+j.substring(i+1);assertThrows(IllegalArgumentException.class,()->BackupArchive.parse(changed));}
    @Test public void untrustedSettingsAndSessionCredentialsCannotEnterBackup(){Map<String,String> s=new HashMap<>();s.put("active_match_id","secret-session");assertThrows(IllegalArgumentException.class,()->BackupArchive.create(new MatchBook(new MatchState("empty",3)),new OpponentDirectory(),s));}
    @Test public void invalidPresetsAndPreparedIdentityAreRejected(){Map<String,String> s=new HashMap<>();s.put("favourite_formats","corrupt");assertThrows(IllegalArgumentException.class,()->BackupArchive.create(new MatchBook(new MatchState("empty",3)),new OpponentDirectory(),s));s.clear();s.put("match_ready_id","another-match");assertThrows(IllegalArgumentException.class,()->BackupArchive.create(new MatchBook(new MatchState("empty",3)),new OpponentDirectory(),s));}
    @Test public void everySportAndPreferenceCanBeBackedUpTogether(){Map<String,String> s=new TreeMap<>();for(String sport:MatchFormat.SPORTS){s.put("last_format_"+sport,MatchFormat.defaults(sport).encode());s.put("last_best_"+sport,"3");}s.put("haptic_mode","milestones");s.put("match_reminders","true");String j=BackupArchive.create(new MatchBook(new MatchState("empty",3)),new OpponentDirectory(),s);assertEquals(s,BackupArchive.parse(j).settings);}
    @Test public void fullBackupIncludesActiveAndSavedNotes(){
        MatchBook b=new MatchBook(new MatchState("active",3));
        b.importResult(new MatchResult("record","2026-11-05",1,3,new int[][]{{11,6},{11,8}}));
        MatchNotes active=new MatchNotes("Try a shorter serve","");
        MatchNotes saved=new MatchNotes("First round","Tuesday tournament");
        b.setNotes("active",active);
        b.setNotes("record",saved);
        BackupArchive backup=BackupArchive.parse(BackupArchive.create(b,new OpponentDirectory(),Collections.emptyMap()));
        assertEquals(active,backup.book.notes("active"));
        assertEquals(saved,backup.book.notes("record"));
        assertEquals(b.encode(),backup.book.encode());
    }
}
