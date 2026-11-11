import java.time.*;
import java.util.*;
import ro.hospital.operations.AvailabilitySlots;
public class AvailabilityCheck {
 static int checks=0;
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 public static void main(String[] args){
  LocalDate day=LocalDate.of(2026,11,12);LocalDateTime now=day.atTime(8,0);
  var range=new AvailabilitySlots.Window(day.atTime(9,0),day.atTime(12,0));
  var all=AvailabilitySlots.calculate(day,List.of(range),List.of(),now);
  check(all.size()==6,"six half-hour slots");
  check(AvailabilitySlots.calculate(day,List.of(range),List.of(day.atTime(9,0)),now).size()==5,"booked slot excluded");
  check(AvailabilitySlots.calculate(day,List.of(range),List.of(day.atTime(9,15)),now).size()==4,"partial overlap excludes both slots");
  check(AvailabilitySlots.calculate(day,List.of(range),List.of(day.atTime(8,30)),now).size()==6,"touching boundary remains free");
  check(AvailabilitySlots.calculate(day,List.of(range),List.of(),day.atTime(10,0)).get(0).equals(day.atTime(10,30)),"past and current start excluded");
  check(AvailabilitySlots.calculate(day,List.of(range,range),List.of(),now).size()==6,"overlapping windows do not duplicate slots");
  check(AvailabilitySlots.calculate(day,List.of(new AvailabilitySlots.Window(day.atTime(9,0),day.atTime(9,20))),List.of(),now).isEmpty(),"short window cannot contain consultation");
  check(AvailabilitySlots.calculate(day,List.of(new AvailabilitySlots.Window(day.minusDays(1).atTime(23,45),day.atTime(1,0))),List.of(),day.minusDays(1).atTime(20,0)).equals(List.of(day.atTime(0,15))),"overnight window preserves slot grid");
  System.out.println("Availability interval checks passed: "+checks);
 }
}
