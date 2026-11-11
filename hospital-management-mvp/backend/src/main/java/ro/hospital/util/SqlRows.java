package ro.hospital.util;

import java.util.List;
import java.util.Map;

/** The schema stores hospital wall-clock times, without a timezone offset. */
public final class SqlRows {
  private SqlRows() {}
  public static Map<String,Object> localDates(Map<String,Object> row) {
    row.replaceAll((key,value)->value instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime()
      : value instanceof java.sql.Date date ? date.toLocalDate() : value);
    return row;
  }
  public static List<Map<String,Object>> localDates(List<Map<String,Object>> rows) {
    rows.forEach(SqlRows::localDates);return rows;
  }
}
