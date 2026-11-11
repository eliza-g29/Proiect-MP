package ro.hospital.operations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.service.AccessPolicy;
class ReportServiceTest {
 NamedParameterJdbcTemplate db=mock(NamedParameterJdbcTemplate.class);AccessPolicy access=mock(AccessPolicy.class);
 ReportService reports=new ReportService(db,access);
 @Test void invalidDateRangeDoesNotQueryMedicalData() {
  var actor=new AccessPolicy.Actor(1L,1L,"ADMIN",null,null);
  assertThrows(ResponseStatusException.class,()->reports.report(LocalDate.of(2026,2,1),LocalDate.of(2026,1,1),actor));verifyNoInteractions(db);
 }
 @Test void csvEscapesSpreadsheetFormulasAndQuotes() {
  String csv=reports.csv(Map.of("byDoctor",List.of(Map.of("name","=1+1\n","specialty","Demo, \"test\"","appointments",2,"completed",1))));
  assertTrue(csv.contains("\"'=1+1\n\""));assertTrue(csv.contains("\"Demo, \"\"test\"\"\""));
 }
}
