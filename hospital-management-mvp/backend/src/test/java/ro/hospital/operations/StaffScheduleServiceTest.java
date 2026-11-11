package ro.hospital.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.service.AccessPolicy;

class StaffScheduleServiceTest {
  NamedParameterJdbcTemplate db=mock(NamedParameterJdbcTemplate.class);
  AccessPolicy access=mock(AccessPolicy.class);
  StaffScheduleService service=new StaffScheduleService(db,access);
  AccessPolicy.Actor staff=new AccessPolicy.Actor(9L,1L,"STAFF",null,null);
  AccessPolicy.Actor admin=new AccessPolicy.Actor(1L,1L,"ADMIN",null,null);
  @Test void staffCanOnlyReadOwnWorkingPeriods() {
    when(db.queryForList(anyString(),anyMap())).thenReturn(List.of());
    service.list(staff);
    var sql=ArgumentCaptor.forClass(String.class);verify(db).queryForList(sql.capture(),anyMap());
    assertTrue(sql.getValue().contains("w.hospital_id=:h"));assertTrue(sql.getValue().contains("w.staff_id=:actor"));
  }
  @Test void patientCannotReadStaffWorkingPeriods() {
    when(access.denied()).thenReturn(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
    assertThrows(ResponseStatusException.class,()->service.list(new AccessPolicy.Actor(2L,1L,"PATIENT",null,21L)));
    verifyNoInteractions(db);
  }
  @Test void inactiveOrForeignStaffCannotBeScheduled() {
    when(db.queryForObject(anyString(),anyMap(),eq(Boolean.class))).thenReturn(false);
    var start=LocalDateTime.of(2099,1,1,9,0);
    assertThrows(ResponseStatusException.class,()->service.save(null,new StaffScheduleService.Period(99L,start,start.plusHours(8)),admin));
    verify(db,never()).queryForMap(anyString(),anyMap());
  }
  @Test void staffCannotWriteOwnWorkingPeriodsWithoutAdministrator() {
    doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(access).admin(staff);
    var start=LocalDateTime.of(2099,1,1,9,0);
    assertThrows(ResponseStatusException.class,()->service.save(null,new StaffScheduleService.Period(9L,start,start.plusHours(8)),staff));
    verifyNoInteractions(db);
  }
}
