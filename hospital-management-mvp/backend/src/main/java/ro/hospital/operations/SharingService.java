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
import ro.hospital.service.AccessPolicy.Actor;

@Service
public class SharingService {
  private final NamedParameterJdbcTemplate db;
  public SharingService(NamedParameterJdbcTemplate db) { this.db=db; }
  public Set<Long> patients(Actor a,boolean write) {
    String sql="""
      SELECT DISTINCT g.patient_id FROM patient_access_grants g
      WHERE g.hospital_id=:h AND g.revoked_at IS NULL AND g.expires_at>CURRENT_TIMESTAMP
      AND (:write=FALSE OR g.permission='WRITE') AND (g.grantee_id=:actor OR EXISTS(
        SELECT FROM staff_departments s WHERE s.hospital_id=g.hospital_id AND s.department_id=g.department_id
        AND s.staff_id=:actor AND s.valid_from<=CURRENT_DATE AND (s.valid_to IS NULL OR s.valid_to>=CURRENT_DATE)))
      """;
    return new HashSet<>(db.queryForList(sql,Map.of("h",a.hospitalId(),"actor",a.id(),"write",write),Long.class));
  }
  private void admin(Actor a) { if(!a.role().equals("ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Acces rezervat administratorului"); }
  public record Grant(@NotNull Long patientId,Long granteeId,Long departmentId,@NotBlank String permission,
    @NotBlank @Size(max=300) String purpose,@NotNull @Future LocalDateTime expiresAt) {}
  public List<Map<String,Object>> list(Actor a) {
    admin(a);return SqlRows.localDates(db.queryForList("SELECT * FROM patient_access_grants WHERE hospital_id=:h ORDER BY id DESC",Map.of("h",a.hospitalId())));
  }
  @Transactional public Map<String,Object> create(Grant input,Actor a) {
    admin(a);
    if((input.granteeId()==null)==(input.departmentId()==null) || !Set.of("READ","WRITE").contains(input.permission()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Alege un cont sau un departament și o permisiune validă");
    Map<String,Object> v=new HashMap<>();v.put("h",a.hospitalId());v.put("actor",a.id());v.put("patient",input.patientId());
    v.put("user",input.granteeId());v.put("department",input.departmentId());v.put("permission",input.permission());v.put("purpose",input.purpose());v.put("expires",java.sql.Timestamp.valueOf(input.expiresAt()));
    if(input.granteeId()!=null && !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT FROM app_users WHERE hospital_id=:h AND id=:user AND active AND role IN ('DOCTOR','STAFF'))",v,Boolean.class)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Beneficiarul trebuie să fie membru activ al personalului");
    var result=db.queryForMap("INSERT INTO patient_access_grants(hospital_id,patient_id,grantee_id,department_id,permission,purpose,expires_at,granted_by) VALUES(:h,:patient,:user,:department,:permission,:purpose,:expires,:actor) RETURNING *",v);
    audit(a,"PATIENT_ACCESS_GRANTED","grant:"+result.get("id"));return SqlRows.localDates(result);
  }
  @Transactional public void revoke(Long id,Actor a) {
    admin(a);int n=db.update("UPDATE patient_access_grants SET revoked_at=CURRENT_TIMESTAMP WHERE id=:id AND hospital_id=:h AND revoked_at IS NULL",Map.of("id",id,"h",a.hospitalId()));
    if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Acordare de acces inexistentă sau revocată");audit(a,"PATIENT_ACCESS_REVOKED","grant:"+id);
  }
  private void audit(Actor a,String action,String resource) {
    db.update("INSERT INTO audit_events(hospital_id,actor_id,action,resource) VALUES(:h,:a,:action,:r)",Map.of("h",a.hospitalId(),"a",a.id(),"action",action,"r",resource));
  }
}
