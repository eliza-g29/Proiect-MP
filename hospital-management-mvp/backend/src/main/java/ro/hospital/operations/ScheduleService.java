package ro.hospital.operations;

import java.time.*;
import ro.hospital.util.SqlRows;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.repo.AppointmentRepository;
import ro.hospital.repo.DoctorRepository;
import ro.hospital.service.AccessPolicy;
import ro.hospital.service.AccessPolicy.Actor;

@Service
public class ScheduleService {
  private final NamedParameterJdbcTemplate db;private final AccessPolicy access;private final DoctorRepository doctors;private final AppointmentRepository appointments;
  public ScheduleService(NamedParameterJdbcTemplate db,AccessPolicy access,DoctorRepository doctors,AppointmentRepository appointments) {
    this.db=db;this.access=access;this.doctors=doctors;this.appointments=appointments;
  }
  public record Period(@NotNull Long doctorId,@NotNull LocalDateTime startAt,@NotNull LocalDateTime endAt) {}
  public List<Map<String,Object>> periods(Actor a) {
    return SqlRows.localDates(db.queryForList("SELECT id,doctor_id,start_at,end_at FROM work_periods WHERE hospital_id=:h AND end_at>CURRENT_TIMESTAMP ORDER BY start_at LIMIT 500",Map.of("h",a.hospitalId())));
  }
  @Transactional public Map<String,Object> save(Long id,Period input,Actor a) {
    access.admin(a);
    if(!input.endAt().isAfter(input.startAt()) || Duration.between(input.startAt(),input.endAt()).compareTo(Duration.ofDays(31))>0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Intervalul de lucru trebuie să fie pozitiv, de maximum 31 de zile");
    if(doctors.findByIdAndHospitalId(input.doctorId(),a.hospitalId()).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Medic inexistent");
    Map<String,Object> v=new HashMap<>();v.put("h",a.hospitalId());v.put("doctor",input.doctorId());v.put("start",java.sql.Timestamp.valueOf(input.startAt()));v.put("end",java.sql.Timestamp.valueOf(input.endAt()));
    Map<String,Object> row;
    if(id==null) row=db.queryForMap("INSERT INTO work_periods(hospital_id,doctor_id,start_at,end_at) VALUES(:h,:doctor,:start,:end) RETURNING *",v);
    else {
      v.put("id",id);
      var oldRows=db.queryForList("SELECT doctor_id,start_at,end_at FROM work_periods WHERE hospital_id=:h AND id=:id FOR UPDATE",v);
      if(oldRows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Interval inexistent");
      var previous=oldRows.get(0);overrideDays(((Number)previous.get("doctor_id")).longValue(),((java.sql.Timestamp)previous.get("start_at")).toLocalDateTime(),((java.sql.Timestamp)previous.get("end_at")).toLocalDateTime(),a);
      var rows=db.queryForList("UPDATE work_periods SET doctor_id=:doctor,start_at=:start,end_at=:end WHERE hospital_id=:h AND id=:id RETURNING *",v);
      if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Interval inexistent");row=rows.get(0);
    }
    overrideDays(input.doctorId(),input.startAt(),input.endAt(),a);
    audit(a,"WORK_PERIOD_SAVED","period:"+row.get("id"));return SqlRows.localDates(row);
  }
  private void overrideDays(Long doctor,LocalDateTime start,LocalDateTime end,Actor a) {
    db.update("INSERT INTO doctor_schedule_exceptions(hospital_id,doctor_id,date) SELECT :h,:d,day::date FROM generate_series(CAST(:first AS timestamp),CAST(:last AS timestamp),interval '1 day') g(day) ON CONFLICT DO NOTHING",Map.of("h",a.hospitalId(),"d",doctor,"first",java.sql.Date.valueOf(start.toLocalDate()),"last",java.sql.Date.valueOf(end.minusNanos(1).toLocalDate())));
  }
  @Transactional public void delete(Long id,Actor a) {
    access.admin(a);
    var removed=db.queryForList("DELETE FROM work_periods WHERE hospital_id=:h AND id=:id RETURNING doctor_id,start_at,end_at",Map.of("h",a.hospitalId(),"id",id));
    if(removed.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Interval inexistent");
    var old=removed.get(0);overrideDays(((Number)old.get("doctor_id")).longValue(),((java.sql.Timestamp)old.get("start_at")).toLocalDateTime(),((java.sql.Timestamp)old.get("end_at")).toLocalDateTime(),a);
    audit(a,"WORK_PERIOD_DELETED","period:"+id);
  }
  public boolean available(Long doctor,LocalDateTime start,Actor a) {
    materialize(start.toLocalDate(),start.toLocalDate(),a);
    return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT FROM work_periods WHERE hospital_id=:h AND doctor_id=:d AND start_at<=:start AND end_at>=:end)",
      Map.of("h",a.hospitalId(),"d",doctor,"start",java.sql.Timestamp.valueOf(start),"end",java.sql.Timestamp.valueOf(start.plusMinutes(30))),Boolean.class));
  }
  public record Window(LocalDateTime startAt,LocalDateTime endAt) {}
  public record DayAvailability(LocalDate date,List<Window> workingHours,List<LocalDateTime> slots) {}
  public record DoctorAvailability(Long doctorId,String name,String specialty,List<DayAvailability> days) {}
  private void materialize(LocalDate from,LocalDate to,Actor a) {
    db.queryForObject("SELECT materialize_doctor_hours(:h,:first,:last)",Map.of("h",a.hospitalId(),"first",java.sql.Date.valueOf(from),"last",java.sql.Date.valueOf(to)),Integer.class);
  }
  private void range(LocalDate from,int days) {
    if(days<1 || days>14 || from.isBefore(LocalDate.now()) || from.plusDays(days-1).isAfter(LocalDate.now().plusDays(365)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Alege 1–14 zile din următoarele 12 luni");
  }
  @Transactional public List<DoctorAvailability> calendar(LocalDate from,int days,Long doctorId,Actor a) {
    range(from,days);
    if(doctorId!=null && doctors.findByIdAndHospitalId(doctorId,a.hospitalId()).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Medic inexistent");
    materialize(from,from.plusDays(days-1),a);
    Map<String,Object> params=new HashMap<>(Map.of("h",a.hospitalId(),"start",java.sql.Timestamp.valueOf(from.atStartOfDay()),"end",java.sql.Timestamp.valueOf(from.plusDays(days).atStartOfDay())));
    String filter=doctorId==null?"":" AND doctor_id=:doctor";if(doctorId!=null)params.put("doctor",doctorId);
    var windows=db.queryForList("SELECT doctor_id,start_at,end_at FROM work_periods WHERE hospital_id=:h AND start_at<:end AND end_at>:start"+filter+" ORDER BY doctor_id,start_at",params);
    var booked=db.queryForList("SELECT doctor_id,start_at FROM appointments WHERE hospital_id=:h AND status='SCHEDULED' AND start_at<:end AND start_at+interval '30 minutes'>:start"+filter,params);
    var directory=doctors.findByHospitalIdOrderByNameAsc(a.hospitalId());
    LocalDateTime now=LocalDateTime.now();List<DoctorAvailability> result=new ArrayList<>();
    for(var doctor:directory) {
      if(doctorId!=null && !doctor.id.equals(doctorId))continue;
      List<Window> doctorWindows=windows.stream().filter(w->((Number)w.get("doctor_id")).longValue()==doctor.id.longValue()).map(w->new Window(((java.sql.Timestamp)w.get("start_at")).toLocalDateTime(),((java.sql.Timestamp)w.get("end_at")).toLocalDateTime())).toList();
      List<LocalDateTime> doctorBookings=booked.stream().filter(b->((Number)b.get("doctor_id")).longValue()==doctor.id.longValue()).map(b->((java.sql.Timestamp)b.get("start_at")).toLocalDateTime()).toList();
      List<DayAvailability> dates=new ArrayList<>();
      for(int offset=0;offset<days;offset++) {
        LocalDate date=from.plusDays(offset);
        List<Window> dayWindows=doctorWindows.stream().filter(w->w.startAt().isBefore(date.plusDays(1).atStartOfDay())&&w.endAt().isAfter(date.atStartOfDay())).toList();
        List<Window> displayWindows=dayWindows.stream().map(w->new Window(w.startAt().isBefore(date.atStartOfDay())?date.atStartOfDay():w.startAt(),w.endAt().isAfter(date.plusDays(1).atStartOfDay())?date.plusDays(1).atStartOfDay():w.endAt())).toList();
        dates.add(new DayAvailability(date,displayWindows,freeSlots(date,dayWindows,doctorBookings,now)));
      }
      result.add(new DoctorAvailability(doctor.id,doctor.name,doctor.specialty,dates));
    }
    return result;
  }
  public static List<LocalDateTime> freeSlots(LocalDate day,List<Window> windows,List<LocalDateTime> booked,LocalDateTime now) {
    return AvailabilitySlots.calculate(day,windows.stream().map(w->new AvailabilitySlots.Window(w.startAt(),w.endAt())).toList(),booked,now);
  }

  @Transactional public List<LocalDateTime> slots(Long doctor,LocalDate day,Actor a) {
    return calendar(day,1,doctor,a).stream().flatMap(d->d.days().stream()).flatMap(d->d.slots().stream()).toList();
  }
  private void audit(Actor a,String action,String resource) {
    db.update("INSERT INTO audit_events(hospital_id,actor_id,action,resource) VALUES(:h,:a,:action,:r)",Map.of("h",a.hospitalId(),"a",a.id(),"action",action,"r",resource));
  }
}
