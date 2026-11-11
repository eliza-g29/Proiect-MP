package ro.hospital.operations;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.service.AccessPolicy;
import ro.hospital.repo.*;
import ro.hospital.domain.*;

class ScheduleServiceTest {
 NamedParameterJdbcTemplate db=mock(NamedParameterJdbcTemplate.class);
 AccessPolicy access=mock(AccessPolicy.class);
 DoctorRepository doctors=mock(DoctorRepository.class);
 AppointmentRepository appointments=mock(AppointmentRepository.class);
 ScheduleService service=new ScheduleService(db,access,doctors,appointments);
 AccessPolicy.Actor patient=new AccessPolicy.Actor(1L,1L,"PATIENT",null,21L);
 @Test void availableSlotsRespectWorkAndThirtyMinuteBoundaries() {
  Doctor d=new Doctor();d.id=11L;d.hospitalId=1L;when(doctors.findByIdAndHospitalId(11L,1L)).thenReturn(Optional.of(d));
  var day=LocalDate.now().plusDays(1);
  when(doctors.findByHospitalIdOrderByNameAsc(1L)).thenReturn(List.of(d));
  when(db.queryForList(contains("FROM work_periods"),anyMap())).thenReturn(List.of(new HashMap<>(Map.of("doctor_id",11L,"start_at",java.sql.Timestamp.valueOf(day.atTime(9,0)),"end_at",java.sql.Timestamp.valueOf(day.atTime(10,0))))));
  Appointment a=new Appointment();a.doctorId=11L;a.startAt=day.atTime(9,0);a.status="SCHEDULED";
  when(db.queryForList(contains("FROM appointments"),anyMap())).thenReturn(List.of(new HashMap<>(Map.of("doctor_id",11L,"start_at",java.sql.Timestamp.valueOf(a.startAt)))));
  assertEquals(List.of(day.atTime(9,30)),service.slots(11L,day,patient));
 }
 @Test void anotherHospitalDoctorCannotBeQueried() {
  assertThrows(ResponseStatusException.class,()->service.slots(99L,LocalDate.now(),patient));verifyNoInteractions(db);
 }
 @Test void negativeWorkingPeriodIsRejectedBeforeWriting() {
  var now=LocalDateTime.now();assertThrows(ResponseStatusException.class,()->service.save(null,new ScheduleService.Period(11L,now,now.minusHours(1)),patient));verifyNoInteractions(db);
 }
}
