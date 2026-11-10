package at.andrew.squashtap;
import java.time.Instant;
import java.util.*;

/** RFC-4180 results interchange. Input is validated completely before any merge. */
public final class MatchCsv {
    public static final int MAX_CHARS=3*1024*1024;
    public static final String HEADER="schema,match_id,date,match_number,opponent_id,opponent_name,best_of,started_at,finished_at,time_zone,duration_seconds,me_sets,opponent_sets,set_1_me,set_1_opponent,set_2_me,set_2_opponent,set_3_me,set_3_opponent,set_4_me,set_4_opponent,set_5_me,set_5_opponent";
    public static final String RETIREMENT_HEADER=HEADER+",retired_player";
    public static final String MULTISPORT_HEADER=RETIREMENT_HEADER+",sport,mode,rules,set_6_me,set_6_opponent,set_7_me,set_7_opponent,score_details,partner_id,partner_name,opponent_2_id,opponent_2_name";
    public static final class Row {public final MatchResult result;public final String opponentId,name;public String partnerId="",partnerName="",otherId="",otherName="";Row(MatchResult r,String id,String n){result=r;opponentId=id;name=n;}}
    private static boolean unsafe(char c){return "=+-@\t\r\n'".indexOf(c)>=0;}
    private static String safe(String value){if(!value.isEmpty()&&unsafe(value.charAt(0)))value="'"+value;return '"'+value.replace("\"","\"\"")+'"';}
    private static String restore(String value){return value.length()>1&&value.charAt(0)=='\''&&unsafe(value.charAt(1))?value.substring(1):value;}
    public static String export(List<MatchResult> results,OpponentDirectory directory){
        boolean retirements=false,multi=false;for(MatchResult r:results){if(r.retired())retirements=true;if(!r.format.legacy())multi=true;}if(multi)retirements=true;
        StringBuilder out=new StringBuilder("\ufeff"+(multi?MULTISPORT_HEADER:retirements?RETIREMENT_HEADER:HEADER)+"\r\n");for(MatchResult r:results){
            List<String> cells=new ArrayList<>(Arrays.asList(multi?"3":retirements?"2":"1",r.id,r.date,Integer.toString(r.number),directory.opponentId(r.id),directory.nameFor(r.id),Integer.toString(r.bestOf),r.startedAt==0?"":Instant.ofEpochMilli(r.startedAt).toString(),r.finishedAt==0?"":Instant.ofEpochMilli(r.finishedAt).toString(),r.zoneId,r.durationMillis()<0?"":Long.toString(r.durationMillis()/1000),Integer.toString(r.gamesWon(0)),Integer.toString(r.gamesWon(1))));
            for(int i=0;i<5;i++){cells.add(i<r.gameCount()?Integer.toString(r.points(i,0)):"");cells.add(i<r.gameCount()?Integer.toString(r.points(i,1)):"");}
            if(retirements)cells.add(r.retiredPlayer==0?"me":r.retiredPlayer==1?"opponent":"");
            if(multi){cells.add(r.format.sport);cells.add(r.format.doubles?"doubles":"singles");cells.add(r.format.encode());for(int i=5;i<7;i++){cells.add(i<r.gameCount()?Integer.toString(r.points(i,0)):"");cells.add(i<r.gameCount()?Integer.toString(r.points(i,1)):"");}StringBuilder detail=new StringBuilder();for(int i=0;i<r.gameCount();i++){if(i>0)detail.append(';');detail.append(r.rallyPoints(i,0)).append(',').append(r.rallyPoints(i,1)).append(',').append(r.tiePoints(i,0)).append(',').append(r.tiePoints(i,1)).append(',').append(r.unfinishedPoints(i,0)).append(',').append(r.unfinishedPoints(i,1));}cells.add(detail.toString());for(int slot=1;slot<=2;slot++){cells.add(directory.roleId(r.id,slot));cells.add(directory.roleName(r.id,slot));}}
            for(int i=0;i<cells.size();i++){if(i>0)out.append(',');out.append(safe(cells.get(i)));}out.append("\r\n");if(out.length()>MAX_CHARS)throw new IllegalArgumentException("Export is too large. Choose a smaller date range.");
        }return out.toString();
    }
    public static List<List<String>> cells(String text){
        if(text==null||text.length()>MAX_CHARS)throw new IllegalArgumentException("CSV is too large");if(text.startsWith("\ufeff"))text=text.substring(1);
        int first=text.indexOf('\n');String line=first<0?text:text.substring(0,first);char delimiter=line.startsWith("schema;")||line.startsWith("\"schema\";")?';':',';
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder value=new StringBuilder();boolean quoted=false,closed=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(quoted){if(c=='"'){if(i+1<text.length()&&text.charAt(i+1)=='"'){value.append('"');i++;}else{quoted=false;closed=true;}}else value.append(c);}
            else if(c=='"'){if(value.length()!=0||closed)throw new IllegalArgumentException("Unexpected quote in CSV");quoted=true;}
            else if(c==delimiter||c=='\r'||c=='\n'){
                row.add(restore(value.toString()));value.setLength(0);closed=false;
                if(c!=delimiter){if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;if(!(row.size()==1&&row.get(0).isEmpty()))rows.add(row);row=new ArrayList<>();if(rows.size()>10001)throw new IllegalArgumentException("Too many CSV rows");}
            }else{if(closed)throw new IllegalArgumentException("Unexpected text after a quoted CSV field");value.append(c);}
            if(value.length()>10000)throw new IllegalArgumentException("CSV field is too long");
        }
        if(quoted)throw new IllegalArgumentException("Unclosed CSV quote");if(value.length()>0||closed||!row.isEmpty()){row.add(restore(value.toString()));rows.add(row);}return rows;
    }
    public static List<Row> parse(String text){
        List<List<String>> rows=cells(text);boolean multi=!rows.isEmpty()&&rows.get(0).equals(Arrays.asList(MULTISPORT_HEADER.split(",")));boolean retirements=multi||!rows.isEmpty()&&rows.get(0).equals(Arrays.asList(RETIREMENT_HEADER.split(",")));if(rows.isEmpty()||!retirements&&!rows.get(0).equals(Arrays.asList(HEADER.split(","))))throw new IllegalArgumentException("Use a Squash Tap CSV export or its template with the original column headings");
        List<Row> out=new ArrayList<>();for(int n=1;n<rows.size();n++)try{
            List<String> c=rows.get(n);if(c.size()!=(multi?36:retirements?24:23)||!c.get(0).equals(multi?"3":retirements?"2":"1"))throw new IllegalArgumentException("Invalid columns or schema");
            String retirement=retirements?c.get(23):"";if(!retirement.isEmpty()&&!retirement.equals("me")&&!retirement.equals("opponent"))throw new IllegalArgumentException("Invalid retired player");
            String name=c.get(5).isEmpty()?"":OpponentDirectory.validName(c.get(5));String id=c.get(4);if(!id.isEmpty())OpponentDirectory.validId(id);if(name.isEmpty()&&!id.isEmpty())throw new IllegalArgumentException("Opponent name is missing");
            List<int[]> scores=new ArrayList<>();boolean empty=false;for(int i=13;i<23;i+=2){if(c.get(i).isEmpty()&&c.get(i+1).isEmpty())empty=true;else{if(empty)throw new IllegalArgumentException("Missing set score");scores.add(new int[]{Integer.parseInt(c.get(i)),Integer.parseInt(c.get(i+1))});}}
            if(multi)for(int i=27;i<31;i+=2){if(c.get(i).isEmpty()&&c.get(i+1).isEmpty())empty=true;else{if(empty)throw new IllegalArgumentException("Missing set score");scores.add(new int[]{Integer.parseInt(c.get(i)),Integer.parseInt(c.get(i+1))});}}
            MatchFormat format=multi?MatchFormat.decode(c.get(26)):MatchFormat.squash();if(multi&&(!format.sport.equals(c.get(24))||!(format.doubles?"doubles":"singles").equals(c.get(25))))throw new IllegalArgumentException("Inconsistent sport or mode");
            int[][] details=null;if(multi){String[] parts=c.get(31).split(";",-1);if(parts.length!=scores.size())throw new IllegalArgumentException("Invalid point details");details=new int[parts.length][];for(int i=0;i<parts.length;i++){String[] d=parts[i].split(",",-1);if(d.length!=4&&d.length!=6)throw new IllegalArgumentException("Invalid point details");details[i]=new int[d.length];for(int j=0;j<d.length;j++)details[i][j]=Integer.parseInt(d[j]);}}
            long start=c.get(7).isEmpty()?0:Instant.parse(c.get(7)).toEpochMilli(),finish=c.get(8).isEmpty()?0:Instant.parse(c.get(8)).toEpochMilli();
            MatchResult r=new MatchResult(c.get(1),c.get(2),Integer.parseInt(c.get(3)),Integer.parseInt(c.get(6)),scores.toArray(new int[0][]),start,finish,c.get(9),retirement.equals("me")?0:retirement.equals("opponent")?1:-1,format,details);
            if(Integer.parseInt(c.get(11))!=r.gamesWon(0)||Integer.parseInt(c.get(12))!=r.gamesWon(1)||!c.get(10).isEmpty()&&(r.durationMillis()<0||Long.parseLong(c.get(10))!=r.durationMillis()/1000))throw new IllegalArgumentException("Summary does not match set scores or timing");Row row=new Row(r,id,name);if(multi){row.partnerId=c.get(32);row.partnerName=c.get(33);row.otherId=c.get(34);row.otherName=c.get(35);for(int i=32;i<36;i+=2){if(!c.get(i).isEmpty())OpponentDirectory.validId(c.get(i));if(!c.get(i+1).isEmpty())OpponentDirectory.validName(c.get(i+1));else if(!c.get(i).isEmpty())throw new IllegalArgumentException("Missing player name");}if(!format.doubles&&(!row.partnerName.isEmpty()||!row.otherName.isEmpty()))throw new IllegalArgumentException("Singles cannot have extra players");java.util.HashSet<String> people=new java.util.HashSet<>();for(String person:new String[]{id,row.partnerId,row.otherId})if(!person.isEmpty()&&!people.add(person))throw new IllegalArgumentException("Duplicate player");}out.add(row);
        }catch(Exception error){throw new IllegalArgumentException("CSV row "+(n+1)+": "+error.getMessage(),error);}return out;
    }
    private static boolean same(MatchResult a,MatchResult b){
        if(!a.format.equals(b.format)||!a.date.equals(b.date)||a.bestOf!=b.bestOf||a.retiredPlayer!=b.retiredPlayer||a.startedAt!=b.startedAt||a.finishedAt!=b.finishedAt||!a.zoneId.equals(b.zoneId)||a.gameCount()!=b.gameCount())return false;
        for(int i=0;i<a.gameCount();i++)for(int p=0;p<2;p++)if(a.points(i,p)!=b.points(i,p)||a.rallyPoints(i,p)!=b.rallyPoints(i,p)||a.tiePoints(i,p)!=b.tiePoints(i,p)||a.unfinishedPoints(i,p)!=b.unfinishedPoints(i,p))return false;return true;
    }
    public static final class Plan {
        public final MatchBook book;public final OpponentDirectory directory;public int added,duplicates,conflicts,renumbered;public final List<String> conflictIds=new ArrayList<>();
        private Plan(MatchBook b,OpponentDirectory d){book=b;directory=d;}
    }
    public static Plan plan(MatchBook original,OpponentDirectory directory,List<Row> rows){
        Plan plan=new Plan(MatchBook.decode(original.encode()),OpponentDirectory.decode(directory.encode()));
        for(Row row:rows){MatchResult old=plan.book.result(row.result.id);
            if(old!=null){if(same(old,row.result))plan.duplicates++;else{plan.conflicts++;plan.conflictIds.add(row.result.id);}continue;}
            if(row.result.id.equals(plan.book.current().id())){plan.conflicts++;plan.conflictIds.add(row.result.id);continue;}
            MatchResult imported=plan.book.importResult(row.result);if(imported.number!=row.result.number)plan.renumbered++;
            String id=plan.directory.importOpponent(row.opponentId,row.name);plan.directory.assignTeam(imported.id,id,plan.directory.importOpponent(row.partnerId,row.partnerName),plan.directory.importOpponent(row.otherId,row.otherName));plan.added++;
        }return plan;
    }
}
