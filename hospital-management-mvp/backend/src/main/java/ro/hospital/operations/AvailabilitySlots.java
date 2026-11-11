package ro.hospital.operations;
import java.time.*;
import java.util.*;
/** Pure half-open interval arithmetic shared by availability endpoints. */
public final class AvailabilitySlots {
  private AvailabilitySlots() {}
  public record Window(LocalDateTime startAt,LocalDateTime endAt) {}
  public static List<LocalDateTime> calculate(LocalDate day,List<Window> windows,List<LocalDateTime> booked,LocalDateTime now) {
    Set<LocalDateTime> result=new TreeSet<>();
    for(var w:windows) {
      LocalDateTime start=w.startAt(),end=w.endAt();
      if(start.isBefore(day.atStartOfDay())) {long minutes=Duration.between(start,day.atStartOfDay()).toMinutes();start=start.plusMinutes(((minutes+29)/30)*30);}
      for(LocalDateTime cursor=start;!cursor.plusMinutes(30).isAfter(end)&&cursor.toLocalDate().equals(day);cursor=cursor.plusMinutes(30)) {
        final LocalDateTime slot=cursor;
        if(slot.isAfter(now)&&booked.stream().noneMatch(b->b.isBefore(slot.plusMinutes(30))&&b.plusMinutes(30).isAfter(slot)))result.add(slot);
      }
    }
    return new ArrayList<>(result);
  }
}
