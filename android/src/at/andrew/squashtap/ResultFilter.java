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
    public static List<String> eventLabels(MatchBook book,List<MatchResult> matches,boolean includeCurrent,Locale locale){
        Set<String> values=new HashSet<>();
        for(MatchResult match:matches)values.add(book.notes(match.id).event);
        if(includeCurrent)values.add(book.notes(book.current().id()).event);
        values.remove("");
        List<String> labels=new ArrayList<>(values);
        labels.sort(eventOrder(locale));
        return labels;
    }
    /** The input is newest-first. Keep that order inside each event. */
    public static void organizeByEvent(List<MatchResult> matches,MatchBook book,String event,boolean alphabetical,Locale locale){
        if(event!=null)matches.removeIf(match->!book.notes(match.id).event.equals(event));
        if(alphabetical)matches.sort(Comparator.comparing(match->book.notes(match.id).event,eventOrder(locale)));
    }
    private static Comparator<String> eventOrder(Locale locale){
        java.text.Collator collator=java.text.Collator.getInstance(locale);
        return Comparator.comparing(String::isEmpty).thenComparing(collator).thenComparing(Comparator.naturalOrder());
    }
}
