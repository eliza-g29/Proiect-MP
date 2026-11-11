package ro.hospital.operations;

import java.time.*;
import ro.hospital.util.SqlRows;
import java.util.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.service.AccessPolicy;
import ro.hospital.service.AccessPolicy.Actor;

@Service
public class StaffScheduleService {
  private final NamedParameterJdbcTemplate db;
  private final AccessPolicy access;
  public StaffScheduleService(NamedParameterJdbcTemplate db,AccessPolicy access) {this.db=db;this.access=access;}
  public record Period(@NotNull Long staffId,@NotNull LocalDateTime startAt,@NotNull LocalDateTime endAt) {}
  public List<Map<String,Object>> list(Actor actor) {
    if(!Set.of("ADMIN","STAFF").contains(actor.role()))throw access.denied();
    String scope=actor.role().equals("ADMIN")?"":" AND w.staff_id=:actor";
    var rows=db.queryForList("SELECT w.id,w.staff_id,u.name,u.staff_specialty,w.start_at,w.end_at FROM staff_work_periods w JOIN app_users u ON u.hospital_id=w.hospital_id AND u.id=w.staff_id WHERE w.hospital_id=:h AND w.end_at>CURRENT_TIMESTAMP"+scope+" ORDER BY w.start_at LIMIT 500",
      Map.of("h",actor.hospitalId(),"actor",actor.id()));
    audit(actor,"STAFF_CALENDAR_READ","staff-work-periods");return SqlRows.localDates(rows);
  }
  @Transactional public Map<String,Object> save(Long id,Period input,Actor actor) {
    access.admin(actor);
    if(!input.endAt().isAfter(input.startAt()) || Duration.between(input.startAt(),input.endAt()).compareTo(Duration.ofDays(31))>0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Intervalul de lucru trebuie să fie pozitiv, de maximum 31 de zile");
    Map<String,Object> params=new HashMap<>();params.put("h",actor.hospitalId());params.put("staff",input.staffId());
    params.put("start",java.sql.Timestamp.valueOf(input.startAt()));params.put("end",java.sql.Timestamp.valueOf(input.endAt()));
    if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT FROM app_users WHERE hospital_id=:h AND id=:staff AND role='STAFF' AND active)",params,Boolean.class)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selectează un cont activ de personal medical auxiliar din spital");
    Map<String,Object> result;
    if(id==null)result=db.queryForMap("INSERT INTO staff_work_periods(hospital_id,staff_id,start_at,end_at) VALUES(:h,:staff,:start,:end) RETURNING *",params);
    else {
      params.put("id",id);
      var rows=db.queryForList("UPDATE staff_work_periods SET staff_id=:staff,start_at=:start,end_at=:end WHERE hospital_id=:h AND id=:id RETURNING *",params);
      if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Interval inexistent");result=rows.get(0);
    }
    audit(actor,"STAFF_WORK_SAVED","staff-period:"+result.get("id"));return SqlRows.localDates(result);
  }
  @Transactional public void delete(Long id,Actor actor) {
    access.admin(actor);
    if(db.update("DELETE FROM staff_work_periods WHERE hospital_id=:h AND id=:id",Map.of("h",actor.hospitalId(),"id",id))==0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Interval inexistent");
    audit(actor,"STAFF_WORK_DELETED","staff-period:"+id);
  }
  private void audit(Actor a,String action,String resource) {
    db.update("INSERT INTO audit_events(hospital_id,actor_id,action,resource) VALUES(:h,:actor,:action,:resource)",Map.of("h",a.hospitalId(),"actor",a.id(),"action",action,"resource",resource));
  }
}
