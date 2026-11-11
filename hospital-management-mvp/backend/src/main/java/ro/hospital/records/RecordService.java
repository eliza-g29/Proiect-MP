package ro.hospital.records;

import java.io.IOException;
import ro.hospital.util.SqlRows;
import java.nio.file.*;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.domain.AuditEvent;
import ro.hospital.repo.AuditEventRepository;
import ro.hospital.service.AccessPolicy;
import ro.hospital.service.AccessPolicy.Actor;

@Service
public class RecordService {
  // Only these constant table/column names enter SQL; all client values are bound parameters.
  private record Spec(String table, String scope, Map<String,String> fields) {}
  private static Map<String,String> fields(String... pairs) {
    Map<String,String> result=new LinkedHashMap<>();
    for(int i=0;i<pairs.length;i+=2) result.put(pairs[i],pairs[i+1]);
    return result;
  }
  private static Map<String,String> clinicalFields(String... pairs) {
    Map<String,String> result=fields(pairs);result.put("patient_visible","boolean");return result;
  }
  private static final Map<String,Spec> SPECS=Map.ofEntries(
    Map.entry("departments",new Spec("departments","admin",fields("name","text:160"))),
    Map.entry("staff-departments",new Spec("staff_departments","admin",fields("staff_id","id","department_id","id","valid_from","date","valid_to","date?"))),
    Map.entry("allergies",new Spec("allergies","patient",clinicalFields("patient_id","id","substance","text:200","reaction","text:4000","status","text:20"))),
    Map.entry("diagnoses",new Spec("diagnoses","encounter",clinicalFields("encounter_id","id","code","text:80","description","text:4000","coding_system","text:120","coding_version","text:80"))),
    Map.entry("observations",new Spec("observations","encounter",clinicalFields("encounter_id","id","type","text:120","value","text:300","unit","text:80","measured_at","timestamp","coding_system","text:120","coding_version","text:80"))),
    Map.entry("lab-results",new Spec("lab_results","patient",clinicalFields("patient_id","id","encounter_id","id?","test_code","text:120","result","text:4000","unit","text:80","coding_system","text:120","coding_version","text:80"))),
    Map.entry("prescriptions",new Spec("prescriptions","encounter",clinicalFields("encounter_id","id","medicine","text:200","dosage","text:300","instructions","text:4000"))),
    Map.entry("ai-reviews",new Spec("ai_reviews","analysis",fields("analysis_id","id","decision","text:30","notes","text:4000","revised_recommendation","text:4000?"))),
    Map.entry("medical-documents",new Spec("medical_documents","patient",fields()))
  );
  private final NamedParameterJdbcTemplate db;
  private final AccessPolicy access;
  private final AuditEventRepository audits;
  private final Path storage;
  public RecordService(NamedParameterJdbcTemplate db,AccessPolicy access,AuditEventRepository audits,
      @Value("${app.document-storage:./data/documents}") String storage) {
    this.db=db;this.access=access;this.audits=audits;this.storage=Path.of(storage).toAbsolutePath().normalize();
  }
  private ResponseStatusException bad(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,reason); }
  private Spec spec(String kind) {
    Spec s=SPECS.get(kind);
    if(s==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Tip de înregistrare necunoscut");
    return s;
  }
  private void audit(Actor a,String action,String resource) {
    AuditEvent e=new AuditEvent();e.hospitalId=a.hospitalId();e.actorId=a.id();e.action=action;e.resource=resource;
    e.createdAt=LocalDateTime.now();audits.save(e);
  }
  public List<Map<String,Object>> staffDirectory(Actor a) {
    access.admin(a);
    return db.queryForList("SELECT id,name,role FROM app_users WHERE hospital_id=:hospital AND role IN ('ADMIN','DOCTOR','STAFF') ORDER BY name",
      Map.of("hospital",a.hospitalId()));
  }
  public List<Map<String,Object>> list(String kind,Actor a) { return list(kind,null,a); }
  public List<Map<String,Object>> list(String kind,Long patientId,Actor a) {
    Spec s=spec(kind);
    Map<String,Object> params=new HashMap<>();params.put("hospital",a.hospitalId());
    String where="r.hospital_id=:hospital";
    if(patientId!=null && !s.scope().equals("admin")) {
      access.patient(patientId,a);params.put("patient",patientId);
      if(s.scope().equals("patient"))where+=" AND r.patient_id=:patient";
      else if(s.scope().equals("encounter"))where+=" AND r.encounter_id IN (SELECT id FROM encounters WHERE hospital_id=:hospital AND patient_id=:patient)";
      else if(s.scope().equals("analysis"))where+=" AND r.analysis_id IN (SELECT v.id FROM ai_analyses v JOIN encounters e ON e.id=v.encounter_id AND e.hospital_id=v.hospital_id WHERE e.hospital_id=:hospital AND e.patient_id=:patient)";
    }
    if(s.scope().equals("admin")) access.staff(a);
    else if(s.scope().equals("analysis")) {
      access.decision(a);
      if(a.role().equals("DOCTOR")) {
        where+=" AND r.analysis_id IN (SELECT v.id FROM ai_analyses v JOIN encounters e ON e.id=v.encounter_id AND e.hospital_id=v.hospital_id WHERE v.hospital_id=:hospital AND e.doctor_id=:doctor)";
        params.put("doctor",a.doctorId());
      }
    } else if(!a.role().equals("ADMIN")) {
      List<Long> ids=s.scope().equals("patient") ? access.patients(a).stream().map(p->p.id).toList()
        : access.encounters(a).stream().map(e->e.id).toList();
      params.put("ids",ids.isEmpty()?List.of(-1L):ids);
      where+=" AND r."+(s.scope().equals("patient")?"patient_id":"encounter_id")+" IN (:ids)";
    }
    if(kind.equals("lab-results") && !a.role().equals("ADMIN")) {
      var ids=access.encounters(a).stream().map(e->e.id).toList();params.put("encIds",ids.isEmpty()?List.of(-1L):ids);
      where+=" AND (r.encounter_id IS NULL OR r.encounter_id IN (:encIds))";
    }
    if(a.role().equals("PATIENT") && Set.of("patient","encounter").contains(s.scope()))where+=" AND r.patient_visible=TRUE";
    List<Map<String,Object>> rows=db.queryForList("SELECT r.* FROM "+s.table()+" r WHERE "+where+" ORDER BY r.id DESC",params);
    if(kind.equals("medical-documents")) rows.forEach(row->row.remove("storage_key"));
    audit(a,"RECORDS_READ",kind+(patientId==null?"":":patient:"+patientId));return SqlRows.localDates(rows);
  }
  private Map<String,Object> validate(Spec s,Map<String,Object> input) {
    if(!s.fields().keySet().containsAll(input.keySet())) throw bad("Câmp necunoscut sau rezervat sistemului");
    Map<String,Object> values=new LinkedHashMap<>();
    s.fields().forEach((name,type)->{
      Object raw=input.get(name);String value=raw==null?"":raw.toString().trim();
      if(type.equals("boolean")) {
        if(value.isEmpty()) {values.put(name,true);return;}
        if(!value.equals("true") && !value.equals("false"))throw bad("Valoare logică invalidă: "+name);
        values.put(name,Boolean.valueOf(value));return;
      }
      if(value.isEmpty()) {
        if(!type.endsWith("?")) throw bad("Câmp obligatoriu: "+name);
        values.put(name,null);return;
      }
      try {
        if(type.startsWith("id")) {
          long id=Long.parseLong(value);if(id<=0) throw bad("Identificator invalid: "+name);values.put(name,id);
        } else if(type.startsWith("date")) values.put(name,Date.valueOf(LocalDate.parse(value)));
        else if(type.equals("timestamp")) values.put(name,Timestamp.valueOf(LocalDateTime.parse(value)));
        else { if(value.length()>Integer.parseInt(type.split(":")[1])) throw bad("Câmp prea lung: "+name);values.put(name,value); }
      } catch(IllegalArgumentException | java.time.format.DateTimeParseException ex) { throw bad("Valoare invalidă: "+name); }
    });
    return values;
  }
  private void references(String kind,Spec s,Map<String,Object> v,Actor a) {
    if(s.scope().equals("admin")) access.admin(a);else access.staff(a);
    if(v.get("patient_id")!=null) access.writePatient((Long)v.get("patient_id"),a);
    if(v.get("encounter_id")!=null) {
      var e=access.writeEncounter((Long)v.get("encounter_id"),a);
      if(v.get("patient_id")!=null && !e.patientId.equals(v.get("patient_id"))) throw bad("Consultația nu corespunde pacientului");
    }
    if(Set.of("diagnoses","prescriptions","ai-reviews").contains(kind)) access.decision(a);
    if(v.get("analysis_id")!=null) {var analysis=access.analysis((Long)v.get("analysis_id"),a);access.writeEncounter(analysis.encounterId,a);}
    if(kind.equals("allergies") && !Set.of("ACTIVE","INACTIVE","RESOLVED").contains(v.get("status"))) throw bad("Status alergie invalid");
    if(kind.equals("ai-reviews") && !Set.of("ACCEPTED","MODIFIED","REJECTED","NEEDS_REVIEW").contains(v.get("decision"))) throw bad("Decizie invalidă");
    if(kind.equals("ai-reviews") && v.get("decision").equals("MODIFIED") && v.get("revised_recommendation")==null) throw bad("Introdu recomandarea revizuită");
    if(kind.equals("staff-departments")) {
      Long count=db.queryForObject("SELECT count(*) FROM app_users WHERE id=:staff_id AND hospital_id=:hospital AND role IN ('ADMIN','DOCTOR','STAFF')",withHospital(v,a),Long.class);
      if(count==null || count!=1) throw bad("Contul nu aparține personalului spitalului");
      if(v.get("valid_to")!=null && ((Date)v.get("valid_to")).before((Date)v.get("valid_from"))) throw bad("Interval departament invalid");
    }
  }
  private Map<String,Object> withHospital(Map<String,Object> v,Actor a) {
    Map<String,Object> result=new HashMap<>(v);result.put("hospital",a.hospitalId());return result;
  }
  private Map<String,Object> insert(String kind,Spec s,Map<String,Object> v,Actor a) {
    v.put("hospital_id",a.hospitalId());
    if(kind.equals("diagnoses")) v.put("confirmed_by",a.id());
    if(kind.equals("prescriptions")) v.put("prescribed_by",a.id());
    if(kind.equals("ai-reviews")) v.put("reviewer_id",a.id());
    String columns=String.join(",",v.keySet());
    String params=String.join(",",v.keySet().stream().map(k->":"+k).toList());
    Map<String,Object> row=db.queryForMap("INSERT INTO "+s.table()+" ("+columns+") VALUES ("+params+") RETURNING *",v);
    boolean notifyPatient=Set.of("lab-results","medical-documents").contains(kind) && !Boolean.FALSE.equals(v.get("patient_visible"));
    if(notifyPatient && kind.equals("lab-results") && v.get("encounter_id")!=null)
      notifyPatient=Boolean.TRUE.equals(db.queryForObject("SELECT patient_visible FROM encounters WHERE hospital_id=:h AND id=:encounter",Map.of("h",a.hospitalId(),"encounter",v.get("encounter_id")),Boolean.class));
    if(notifyPatient) {
      db.update("INSERT INTO notifications(hospital_id,user_id,title,message) SELECT hospital_id,id,:title,:message FROM app_users WHERE hospital_id=:h AND patient_id=:patient AND active",
        Map.of("h",a.hospitalId(),"patient",v.get("patient_id"),"title",kind.equals("lab-results")?"Rezultat nou disponibil":"Document medical nou","message","O înregistrare nouă este disponibilă în dosarul tău medical."));
    }
    audit(a,"RECORD_CREATED",kind+":"+row.get("id"));row.remove("storage_key");return SqlRows.localDates(row);
  }
  @Transactional
  public Map<String,Object> create(String kind,Map<String,Object> input,Actor a) {
    Spec s=spec(kind);
    if(kind.equals("medical-documents")) throw bad("Folosește încărcarea de fișiere");
    Map<String,Object> v=validate(s,input);references(kind,s,v,a);return insert(kind,s,v,a);
  }
  @Transactional
  public Map<String,Object> update(String kind,Long id,Map<String,Object> input,Actor a) {
    Spec s=spec(kind);
    if(Set.of("medical-documents","ai-reviews").contains(kind)) throw bad("Documentele și revizuirile se păstrează; adaugă o nouă versiune");
    Map<String,Object> v=validate(s,input);references(kind,s,v,a);
    var old=db.queryForList("SELECT * FROM "+s.table()+" WHERE hospital_id=:hospital AND id=:id FOR UPDATE",Map.of("hospital",a.hospitalId(),"id",id));
    if(old.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Înregistrare inexistentă");
    if(v.containsKey("patient_visible") && !input.containsKey("patient_visible"))v.put("patient_visible",old.get(0).get("patient_visible"));
    for(String parent:List.of("patient_id","encounter_id","analysis_id","staff_id","department_id"))
      if(!Objects.equals(old.get(0).get(parent),v.get(parent))) throw bad("Legăturile înregistrării nu pot fi reasignate");
    Map<String,Object> params=withHospital(v,a);params.put("id",id);params.put("actor",a.id());params.put("kind",kind);
    db.update("INSERT INTO record_revisions(hospital_id,record_type,record_id,actor_id,previous_data) SELECT :hospital,:kind,id,:actor,to_jsonb(r) FROM "+s.table()+" r WHERE r.hospital_id=:hospital AND r.id=:id",params);
    if(kind.equals("diagnoses")) v.put("confirmed_by",a.id());
    if(kind.equals("prescriptions")) v.put("prescribed_by",a.id());
    params.putAll(v);
    var row=db.queryForMap("UPDATE "+s.table()+" SET "+String.join(",",v.keySet().stream().map(k->k+"=:"+k).toList())+" WHERE hospital_id=:hospital AND id=:id RETURNING *",params);
    audit(a,"RECORD_UPDATED",kind+":"+id);return SqlRows.localDates(row);
  }

  @Transactional
  public Map<String,Object> upload(Long patientId,String documentType,MultipartFile file,boolean patientVisible,Actor a) throws IOException {
    access.staff(a);access.writePatient(patientId,a);
    if(file.isEmpty() || file.getSize()>10*1024*1024) throw bad("Fișierul trebuie să aibă între 1 octet și 10 MB");
    String type=documentType.trim();if(type.isEmpty() || type.length()>120) throw bad("Tip document invalid");
    Files.createDirectories(storage);
    String key=UUID.randomUUID().toString();Path target=storage.resolve(key);
    try(var stream=file.getInputStream()) { Files.copy(stream,target); }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCompletion(int status) {
        if(status!=STATUS_COMMITTED) { try { Files.deleteIfExists(target); } catch(IOException ignored) {} }
      }
    });
    String name=Optional.ofNullable(file.getOriginalFilename()).orElse("document").replace('\\','/');
    name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "_");
    if(name.isBlank()) name="document";
    if(name.length()>255) name=name.substring(0,255);
    Map<String,Object> v=new LinkedHashMap<>();v.put("patient_id",patientId);v.put("storage_key",key);v.put("document_type",type);
    v.put("original_name",name);v.put("content_type","application/octet-stream");v.put("size_bytes",file.getSize());v.put("uploaded_by",a.id());v.put("patient_visible",patientVisible);
    return insert("medical-documents",spec("medical-documents"),v,a);
  }
  public record Download(String name,byte[] content) {}
  public Download download(Long id,Actor a) throws IOException {
    List<Map<String,Object>> rows=db.queryForList("SELECT * FROM medical_documents WHERE id=:id AND hospital_id=:hospital",Map.of("id",id,"hospital",a.hospitalId()));
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Document inexistent");
    Map<String,Object> row=rows.get(0);
    if(a.role().equals("PATIENT") && Boolean.FALSE.equals(row.get("patient_visible")))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Document indisponibil");
    access.patient(((Number)row.get("patient_id")).longValue(),a);
    Path file=storage.resolve(row.get("storage_key").toString()).normalize();
    if(!file.startsWith(storage) || !Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Fișier indisponibil");
    byte[] content=Files.readAllBytes(file);audit(a,"DOCUMENT_READ","document:"+id);
    return new Download(row.get("original_name").toString(),content);
  }
}
