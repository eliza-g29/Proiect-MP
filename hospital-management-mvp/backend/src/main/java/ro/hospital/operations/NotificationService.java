package ro.hospital.operations;

import java.util.*;
import ro.hospital.util.SqlRows;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.domain.Appointment;
import ro.hospital.service.AccessPolicy;
import ro.hospital.service.AccessPolicy.Actor;

@Service
public class NotificationService {
  private final NamedParameterJdbcTemplate db;private final AccessPolicy access;
  public NotificationService(NamedParameterJdbcTemplate db,AccessPolicy access) { this.db=db;this.access=access; }
  public List<Map<String,Object>> list(Actor a) {
    return SqlRows.localDates(db.queryForList("SELECT id,title,message,read_at,created_at FROM notifications WHERE hospital_id=:h AND user_id=:u ORDER BY id DESC LIMIT 200",Map.of("h",a.hospitalId(),"u",a.id())));
  }
  public void read(Long id,Actor a) {
    if(db.update("UPDATE notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP) WHERE id=:id AND hospital_id=:h AND user_id=:u",Map.of("id",id,"h",a.hospitalId(),"u",a.id()))==0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Notificare inexistentă");
  }
  public record Announcement(Long userId,@NotBlank @Size(max=180) String title,@NotBlank @Size(max=4000) String message) {}
  @Transactional public void announce(Announcement input,Actor a) {
    access.admin(a);Map<String,Object> v=new HashMap<>();v.put("h",a.hospitalId());v.put("u",input.userId());v.put("title",input.title());v.put("message",input.message());
    int n=db.update("INSERT INTO notifications(hospital_id,user_id,title,message) SELECT hospital_id,id,:title,:message FROM app_users WHERE hospital_id=:h AND active AND (CAST(:u AS BIGINT) IS NULL OR id=:u)",v);
    if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Destinatar inexistent");
    db.update("INSERT INTO audit_events(hospital_id,actor_id,action,resource) VALUES(:h,:a,'ANNOUNCEMENT_SENT',:r)",Map.of("h",a.hospitalId(),"a",a.id(),"r",input.userId()==null?"hospital":"user:"+input.userId()));
  }
  public void appointment(Actor a,Appointment appointment,String event) {
    db.update("""
      INSERT INTO notifications(hospital_id,user_id,title,message)
      SELECT hospital_id,id,:title,:message FROM app_users WHERE hospital_id=:h AND active
      AND (id=:actor OR doctor_id=:doctor OR patient_id=:patient)
      """,Map.of("h",a.hospitalId(),"actor",a.id(),"doctor",appointment.doctorId,"patient",appointment.patientId,
      "title",event,"message","Programarea #"+appointment.id+" · "+appointment.startAt+" · "+appointment.status));
  }
}
