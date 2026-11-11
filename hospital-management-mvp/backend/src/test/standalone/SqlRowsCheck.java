import ro.hospital.util.SqlRows;
import java.time.*;
import java.util.*;
public class SqlRowsCheck {
  public static void main(String[] args) {
    for(String zone:List.of("UTC","Europe/Bucharest")) {
      TimeZone.setDefault(TimeZone.getTimeZone(zone));
      var expected=LocalDateTime.of(2026,11,11,9,30);
      Map<String,Object> row=new HashMap<>();
      row.put("start_at",java.sql.Timestamp.valueOf(expected));row.put("valid_from",java.sql.Date.valueOf("2026-11-11"));
      row.put("id",7L);row.put("patient_visible",false);row.put("empty",null);
      SqlRows.localDates(List.of(row));
      if(!expected.equals(row.get("start_at")) || !LocalDate.of(2026,11,11).equals(row.get("valid_from"))
          || !Long.valueOf(7L).equals(row.get("id")) || !Boolean.FALSE.equals(row.get("patient_visible")) || row.get("empty")!=null)
        throw new AssertionError("Hospital wall-clock serialization failed in "+zone);
    }
    System.out.println("SQL date normalization checks passed in UTC and Europe/Bucharest.");
  }
}
