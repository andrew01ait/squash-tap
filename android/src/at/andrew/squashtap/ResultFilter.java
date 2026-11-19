package at.andrew.squashtap;
import java.time.*;
import java.util.*;
public final class ResultFilter {
    public static LocalDate date(MatchResult result){return result.startedAt>0?Instant.ofEpochMilli(result.startedAt).atZone(ZoneId.of(result.zoneId)).toLocalDate():LocalDate.parse(result.date);}
    public static List<MatchResult> select(MatchBook book,OpponentDirectory directory,String opponentId,LocalDate from,LocalDate to){
        if(from!=null&&to!=null&&from.isAfter(to))throw new IllegalArgumentException("From date must not follow To date");
        List<MatchResult> out=new ArrayList<>();for(MatchResult r:book.newestFirst()){
            LocalDate date=date(r);if(opponentId!=null&&!directory.involved(r.id,opponentId)||from!=null&&date.isBefore(from)||to!=null&&date.isAfter(to))continue;out.add(r);
        }
        Collections.sort(out,(a,b)->{int c=date(b).compareTo(date(a));if(c==0)c=Long.compare(b.startedAt,a.startedAt);if(c==0)c=Integer.compare(b.number,a.number);return c;});return out;
    }
}
