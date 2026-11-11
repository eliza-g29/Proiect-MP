package ro.hospital.operations;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.service.AccessPolicy;
import ro.hospital.service.AccessPolicy.Actor;

@Service
public class ReportService {
  private final NamedParameterJdbcTemplate db;private final AccessPolicy access;
  public ReportService(NamedParameterJdbcTemplate db,AccessPolicy access) { this.db=db;this.access=access; }
  public Map<String,Object> report(LocalDate from,LocalDate to,Actor a) {
    access.admin(a);
    if(to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from,to)>366) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Raportul trebuie să acopere maximum 366 de zile");
    Map<String,Object> v=Map.of("h",a.hospitalId(),"start",java.sql.Timestamp.valueOf(from.atStartOfDay()),"end",java.sql.Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
    var totals=db.queryForMap("""
      SELECT (SELECT count(*) FROM patients WHERE hospital_id=:h AND created_at>=:start AND created_at<:end) AS new_patients,
        (SELECT count(*) FROM appointments WHERE hospital_id=:h AND start_at>=:start AND start_at<:end) AS appointments,
        (SELECT count(*) FROM encounters WHERE hospital_id=:h AND created_at>=:start AND created_at<:end) AS encounters,
        (SELECT count(*) FROM ai_analyses WHERE hospital_id=:h AND created_at>=:start AND created_at<:end) AS ai_analyses
      """,v);
    var byDoctor=db.queryForList("SELECT d.id,d.name,d.specialty,count(a.id) AS appointments,count(a.id) FILTER(WHERE a.status='COMPLETED') AS completed FROM doctors d LEFT JOIN appointments a ON a.doctor_id=d.id AND a.hospital_id=d.hospital_id AND a.start_at>=:start AND a.start_at<:end WHERE d.hospital_id=:h GROUP BY d.id ORDER BY d.name",v);
    var byStatus=db.queryForList("SELECT status,count(*) AS total FROM appointments WHERE hospital_id=:h AND start_at>=:start AND start_at<:end GROUP BY status ORDER BY status",v);
    db.update("INSERT INTO audit_events(hospital_id,actor_id,action,resource) VALUES(:h,:actor,'REPORT_READ',:resource)",Map.of("h",a.hospitalId(),"actor",a.id(),"resource",from+"/"+to));
    return Map.of("from",from,"to",to,"totals",totals,"byDoctor",byDoctor,"byStatus",byStatus);
  }
  public String csv(Map<String,Object> report) {
    StringBuilder csv=new StringBuilder("Medic,Specializare,Programari,Finalizate\r\n");
    Object rows=report.get("byDoctor");
    if(rows instanceof List<?> list) for(Object row:list) if(row instanceof Map<?,?> m) {
      csv.append(cell(m.get("name"))).append(',').append(cell(m.get("specialty"))).append(',').append(m.get("appointments")).append(',').append(m.get("completed")).append("\r\n");
    }
    return csv.toString();
  }
  private String cell(Object value) { String s=String.valueOf(value);if(!s.isEmpty() && "=+-@".indexOf(s.charAt(0))>=0)s="'"+s;return "\""+s.replace("\"","\"\"")+"\""; }
}
