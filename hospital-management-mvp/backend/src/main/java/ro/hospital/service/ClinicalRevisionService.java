package ro.hospital.service;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import ro.hospital.service.AccessPolicy.Actor;
@Service
public class ClinicalRevisionService {
 private final NamedParameterJdbcTemplate db;
 public ClinicalRevisionService(NamedParameterJdbcTemplate db) {this.db=db;}
 public void snapshot(String table,Long id,Actor actor) {
  if(!Set.of("patients","encounters").contains(table))throw new IllegalArgumentException("Unknown clinical table");
  db.update("INSERT INTO record_revisions(hospital_id,record_type,record_id,actor_id,previous_data) SELECT :h,:kind,id,:actor,to_jsonb(r) FROM "+table+" r WHERE r.hospital_id=:h AND r.id=:id",
   Map.of("h",actor.hospitalId(),"kind",table,"actor",actor.id(),"id",id));
 }
}
