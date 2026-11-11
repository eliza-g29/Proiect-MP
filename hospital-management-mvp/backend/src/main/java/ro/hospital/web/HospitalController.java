package ro.hospital.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.domain.*;
import ro.hospital.repo.*;
import ro.hospital.service.AccessPolicy;
import ro.hospital.service.ActorResolver;
import ro.hospital.service.ClinicalRevisionService;
import ro.hospital.operations.NotificationService;
import ro.hospital.operations.ScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController @RequestMapping("/api")
public class HospitalController {
  private final HospitalRepository hospitals;
  private final DoctorRepository doctors;
  private final PatientRepository patients;
  private final AppointmentRepository appointments;
  private final EncounterRepository encounters;
  private final AiAnalysisRepository analyses;
  private final AuditEventRepository audits;
  private final RestClient restClient;
  private final String aiUrl;
  private final AccessPolicy access;
  private final ActorResolver identity;
  private final NotificationService notifications;
  private final ScheduleService schedules;
  private final ObjectMapper json;
  private final ClinicalRevisionService revisions;
  private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate clinicalDb;

  public HospitalController(HospitalRepository hospitals, DoctorRepository doctors,
      PatientRepository patients, AppointmentRepository appointments,
      EncounterRepository encounters, AiAnalysisRepository analyses,
      AuditEventRepository audits, RestClient.Builder restClientBuilder,
      @Value("${app.ai-url}") String aiUrl, AccessPolicy access, ActorResolver identity, NotificationService notifications, ScheduleService schedules, ObjectMapper json, ClinicalRevisionService revisions, org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate clinicalDb) {
    this.hospitals=hospitals;this.doctors=doctors;this.patients=patients;
    this.appointments=appointments;this.encounters=encounters;this.analyses=analyses;
    this.clinicalDb=clinicalDb;this.revisions=revisions;this.notifications=notifications;this.schedules=schedules;this.json=json;this.identity=identity;this.access=access;this.audits=audits;
    SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));factory.setReadTimeout(Duration.ofSeconds(7));
    this.restClient=restClientBuilder.requestFactory(factory).build();this.aiUrl=aiUrl;
  }

  private record UserCtx(Long id,Long hospitalId,String role,Long doctorId,Long patientId) {
    boolean isStaff() { return role.equals("ADMIN") || role.equals("DOCTOR") || role.equals("STAFF"); }
  }
  private UserCtx ctx(Jwt jwt) {
    AccessPolicy.Actor a=identity.actor(jwt);
    return new UserCtx(a.id(),a.hospitalId(),a.role(),a.doctorId(),a.patientId());
  }
  private void staff(UserCtx u) { if(!u.isStaff()) throw error(HttpStatus.FORBIDDEN,"Acces rezervat personalului medical"); }
  private ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
  private void log(UserCtx u,String action,String resource) {
    AuditEvent event=new AuditEvent();event.hospitalId=u.hospitalId();event.actorId=u.id();
    event.action=action;event.resource=resource;event.createdAt=LocalDateTime.now();audits.save(event);
  }
  private AccessPolicy.Actor actor(UserCtx u) { return new AccessPolicy.Actor(u.id(),u.hospitalId(),u.role(),u.doctorId(),u.patientId()); }
  private Patient patient(Long id,UserCtx u) { return access.patient(id,actor(u)); }
  private List<Patient> visiblePatients(UserCtx u) { return access.patients(actor(u)); }
  private List<Encounter> visibleEncounters(UserCtx u) { return access.encounters(actor(u)); }
  private Doctor doctor(Long id,UserCtx u) { return doctors.findByIdAndHospitalId(id,u.hospitalId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Medic inexistent")); }
  private Encounter encounter(Long id,UserCtx u) { return access.encounter(id,actor(u)); }

  @GetMapping("/health") public Map<String,String> health() { hospitals.count();return Map.of("status","ok","service","hospital-api"); }

  @GetMapping("/dashboard")
  public Map<String,Object> dashboard(@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);
    List<Appointment> all=visibleAppointments(u);
    LocalDate today=LocalDate.now();
    long todayCount=all.stream().filter(a->a.startAt.toLocalDate().equals(today) && !a.status.equals("CANCELLED")).count();
    long planned=all.stream().filter(a->a.status.equals("SCHEDULED")).count();
    long pcount=visiblePatients(u).size();
    long dcount=doctors.findByHospitalIdOrderByNameAsc(u.hospitalId()).size();
    long acount=(u.role().equals("PATIENT") || u.role().equals("STAFF"))?0:analyses(jwt).size();
    Map<String,Object> result=new LinkedHashMap<>();
    result.put("patients",pcount);result.put("doctors",dcount);result.put("appointments",planned);
    result.put("todayAppointments",todayCount);result.put("aiAnalyses",acount);
    result.put("hospitalName",hospitals.findById(u.hospitalId()).map(h->h.name).orElse("Unitate medicală"));
    return result;
  }

  @GetMapping("/doctors")
  public List<Doctor> doctors(@AuthenticationPrincipal Jwt jwt) {
    return doctors.findByHospitalIdOrderByNameAsc(ctx(jwt).hospitalId());
  }

  @GetMapping("/patients")
  public List<Patient> patients(@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);
    List<Patient> result=visiblePatients(u);
    log(u,"PATIENTS_READ","patients");
    return result;
  }

  @GetMapping("/patients/{id}")
  public Patient getPatient(@PathVariable Long id,@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);Patient result=patient(id,u);log(u,"PATIENT_READ","patient:"+id);return result;
  }

  public record NewPatient(@NotBlank @Size(max=100) String firstName,@NotBlank @Size(max=100) String lastName,
    @NotNull @PastOrPresent LocalDate birthDate,@Size(max=50) String phone,@Email @Size(max=180) String email) {}

  @PostMapping("/patients") @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Patient createPatient(@Valid @RequestBody NewPatient in,@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);staff(u);
    if(in.birthDate().isAfter(LocalDate.now())) throw error(HttpStatus.BAD_REQUEST,"Data nașterii este invalidă");
    Patient p=new Patient();p.hospitalId=u.hospitalId();p.firstName=in.firstName().trim();p.lastName=in.lastName().trim();
    p.birthDate=in.birthDate();p.phone=in.phone();p.email=in.email();p.createdAt=LocalDateTime.now();
    p=patients.save(p);log(u,"PATIENT_CREATED","patient:"+p.id);return p;
  }

  @PatchMapping("/patients/{id}") @Transactional
  public Patient updatePatient(@PathVariable Long id,@Valid @RequestBody NewPatient input,@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);staff(u);Patient p=access.writePatient(id,actor(u));
    revisions.snapshot("patients",id,actor(u));
    p.firstName=input.firstName().trim();p.lastName=input.lastName().trim();p.birthDate=input.birthDate();p.phone=input.phone();p.email=input.email();
    p=patients.save(p);log(u,"PATIENT_UPDATED","patient:"+id);return p;
  }

  private List<Appointment> visibleAppointments(UserCtx u) {
    List<Appointment> list=appointments.findByHospitalIdOrderByStartAtAsc(u.hospitalId());
    if(u.role().equals("PATIENT")) return list.stream().filter(a->Objects.equals(a.patientId,u.patientId())).toList();
    if(u.role().equals("DOCTOR")) return list.stream().filter(a->Objects.equals(a.doctorId,u.doctorId())).toList();
    if(u.role().equals("STAFF")) { Set<Long> ids=visiblePatients(u).stream().map(p->p.id).collect(java.util.stream.Collectors.toSet());return list.stream().filter(a->ids.contains(a.patientId)).toList(); }
    return list;
  }
  @GetMapping("/appointments")
  public List<Appointment> appointments(@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);List<Appointment> result=visibleAppointments(u);
    log(u,"APPOINTMENTS_READ","appointments");return result;
  }

  public record NewAppointment(@NotNull Long patientId,@NotNull Long doctorId,
    @NotNull LocalDateTime startAt,@Size(max=300) String reason) {}

  @PostMapping("/appointments") @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Appointment createAppointment(@Valid @RequestBody NewAppointment in,@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);
    access.writePatient(in.patientId(),actor(u));doctor(in.doctorId(),u);
    if(u.role().equals("DOCTOR") && !Objects.equals(in.doctorId(),u.doctorId())) throw error(HttpStatus.FORBIDDEN,"Puteți programa numai la medicul asociat contului");
    if(in.startAt().isBefore(LocalDateTime.now())) throw error(HttpStatus.BAD_REQUEST,"Alegeți o dată viitoare");
    if(!schedules.available(in.doctorId(),in.startAt(),actor(u))) throw error(HttpStatus.CONFLICT,"Medicul nu are program de lucru în intervalul ales");
    boolean overlap=appointments.findByHospitalIdOrderByStartAtAsc(u.hospitalId()).stream()
      .anyMatch(a -> a.doctorId.equals(in.doctorId()) && a.status.equals("SCHEDULED") && a.startAt.isBefore(in.startAt().plusMinutes(30)) && a.startAt.plusMinutes(30).isAfter(in.startAt()));
    if(overlap) throw error(HttpStatus.CONFLICT,"Medicul are deja o programare în acest interval de 30 minute");
    Appointment a=new Appointment();a.hospitalId=u.hospitalId();a.patientId=in.patientId();a.doctorId=in.doctorId();
    a.startAt=in.startAt();a.reason=in.reason();a.status="SCHEDULED";a.createdAt=LocalDateTime.now();
    a=appointments.saveAndFlush(a);log(u,"APPOINTMENT_CREATED","appointment:"+a.id);notifications.appointment(actor(u),a,"Programare confirmată");return a;
  }

  public record Reschedule(@NotNull LocalDateTime startAt,@NotNull Long doctorId,@Size(max=300) String reason,Long version) {}
  @PatchMapping("/appointments/{id}") @Transactional
  public Appointment reschedule(@PathVariable Long id,@Valid @RequestBody Reschedule input,@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);Appointment a=appointments.findByIdAndHospitalId(id,u.hospitalId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Programare inexistentă"));
    access.writePatient(a.patientId,actor(u));
    if(input.version()!=null && input.version()!=a.version)throw error(HttpStatus.CONFLICT,"Programarea a fost modificată între timp. Reîncarcă datele înainte de reprogramare.");
    if(u.role().equals("DOCTOR") && (!Objects.equals(a.doctorId,u.doctorId()) || !Objects.equals(input.doctorId(),u.doctorId()))) throw error(HttpStatus.FORBIDDEN,"Poți modifica doar propriul calendar");
    if(!a.status.equals("SCHEDULED") || !a.startAt.isAfter(LocalDateTime.now()) || !input.startAt().isAfter(LocalDateTime.now())) throw error(HttpStatus.BAD_REQUEST,"Doar programările viitoare active se pot reprograma");
    doctor(input.doctorId(),u);
    if(!schedules.available(input.doctorId(),input.startAt(),actor(u))) throw error(HttpStatus.CONFLICT,"Interval în afara programului medicului");
    if(!a.doctorId.equals(input.doctorId()))notifications.appointment(actor(u),a,"Programare transferată la alt medic");
    a.startAt=input.startAt();a.doctorId=input.doctorId();a.reason=input.reason();a=appointments.saveAndFlush(a);
    log(u,"APPOINTMENT_RESCHEDULED","appointment:"+a.id);notifications.appointment(actor(u),a,"Programare modificată");return a;
  }

  public record AppointmentStatus(@NotBlank String status,Long version) {}
  @PatchMapping("/appointments/{id}/status")
  @Transactional
  public Appointment updateAppointment(@PathVariable Long id,@Valid @RequestBody AppointmentStatus body,@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);
    Appointment a=appointments.findByIdAndHospitalId(id,u.hospitalId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Programare inexistentă"));
    if(u.role().equals("DOCTOR") && !Objects.equals(a.doctorId,u.doctorId())) throw error(HttpStatus.FORBIDDEN,"Acces interzis");
    if(u.role().equals("PATIENT") && (!Objects.equals(a.patientId,u.patientId()) || !body.status().equals("CANCELLED") || !a.status.equals("SCHEDULED") || !a.startAt.isAfter(LocalDateTime.now()))) throw error(HttpStatus.FORBIDDEN,"Poți anula doar propriile programări viitoare active");
    if(u.role().equals("STAFF")) access.writePatient(a.patientId,actor(u));
    if(body.version()!=null && body.version()!=a.version)throw error(HttpStatus.CONFLICT,"Programarea a fost modificată între timp. Reîncarcă datele înainte de actualizare.");
    if(!Set.of("SCHEDULED","COMPLETED","CANCELLED").contains(body.status())) throw error(HttpStatus.BAD_REQUEST,"Status invalid");
    a.status=body.status();a=appointments.saveAndFlush(a);log(u,"APPOINTMENT_UPDATED","appointment:"+a.id);notifications.appointment(actor(u),a,"Status programare actualizat");return a;
  }

  @GetMapping("/encounters")
  public List<Encounter> encounters(@RequestParam(required=false) Long patientId,@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);
    List<Encounter> list=visibleEncounters(u);
    if(patientId!=null) { patient(patientId,u); list=list.stream().filter(e->e.patientId.equals(patientId)).toList(); }
    log(u,"ENCOUNTERS_READ",patientId == null ? "encounters" : "patient:"+patientId);
    return list;
  }

  public record NewEncounter(@NotNull Long patientId,@NotNull Long doctorId,Long appointmentId,
    @NotBlank @Size(min=3,max=4000) String symptoms,@Size(max=4000) String observations,@Size(max=8000) String assessment,Boolean patientVisible) {}

  @PostMapping("/encounters") @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Encounter createEncounter(@Valid @RequestBody NewEncounter in,@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);staff(u);access.decision(actor(u));access.writePatient(in.patientId(),actor(u));doctor(in.doctorId(),u);
    if(u.role().equals("DOCTOR") && !Objects.equals(in.doctorId(),u.doctorId())) throw error(HttpStatus.FORBIDDEN,"Selectați propriul profil de medic");
    if(in.appointmentId()!=null) {
      Appointment a=appointments.findByIdAndHospitalId(in.appointmentId(),u.hospitalId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Programare inexistentă"));
      if(!a.patientId.equals(in.patientId())||!a.doctorId.equals(in.doctorId())) throw error(HttpStatus.BAD_REQUEST,"Programarea nu corespunde pacientului și medicului");
    }
    Encounter e=new Encounter();e.hospitalId=u.hospitalId();e.patientId=in.patientId();e.doctorId=in.doctorId();
    e.patientVisible=in.patientVisible()==null || in.patientVisible();e.appointmentId=in.appointmentId();e.symptoms=in.symptoms();e.observations=in.observations();e.assessment=in.assessment();
    e.createdAt=LocalDateTime.now();e=encounters.save(e);log(u,"ENCOUNTER_CREATED","encounter:"+e.id);return e;
  }

  @PatchMapping("/encounters/{id}") @Transactional
  public Encounter updateEncounter(@PathVariable Long id,@Valid @RequestBody NewEncounter input,@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);access.decision(actor(u));Encounter e=access.writeEncounter(id,actor(u));
    if(!e.patientId.equals(input.patientId()) || !e.doctorId.equals(input.doctorId()) || !Objects.equals(e.appointmentId,input.appointmentId())) throw error(HttpStatus.BAD_REQUEST,"Pacientul, medicul și programarea consultației nu pot fi reasignate");
    revisions.snapshot("encounters",id,actor(u));
    if(input.patientVisible()!=null)e.patientVisible=input.patientVisible();
    e.symptoms=input.symptoms();e.observations=input.observations();e.assessment=input.assessment();e=encounters.save(e);
    log(u,"ENCOUNTER_UPDATED","encounter:"+id);return e;
  }

  @GetMapping("/ai-analyses")
  public List<AiAnalysis> analyses(@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);staff(u);
    List<AiAnalysis> list=analyses.findByHospitalIdOrderByCreatedAtDesc(u.hospitalId());
    access.decision(actor(u));
    if(!u.role().equals("ADMIN")) {
      Set<Long> own=visibleEncounters(u).stream().map(e->e.id).collect(java.util.stream.Collectors.toSet());
      list=list.stream().filter(a->own.contains(a.encounterId)).toList();
    }
    log(u,"AI_ANALYSES_READ","ai-analyses");return list;
  }

  public record MedicationSource(String title,String url) {}
  public record MedicationOption(String name,String indication,String status,List<String> checks,List<MedicationSource> sources,List<String> reasons,Double rankingScore) {}
  public record AiCondition(String code,String label,Double score,List<String> differentials,
    List<String> managementOptions,List<String> investigations,String referral) {}
  public record AiResponse(String category,String summary,String evidence,String modelVersion,Boolean demoOnly,
    Boolean insufficientData,List<String> diagnosticSuggestions,List<String> therapyOptions,String priority,List<String> basis,
    List<AiCondition> possibleConditions,List<String> investigations,String referral,String confidenceLevel,List<String> clarificationQuestions,
    List<MedicationOption> medicationOptions,List<MedicationOption> withheldMedications,String medicationNote) {}
  @PostMapping("/ai/analyze/{encounterId}")
  @Transactional
  public AiAnalysis analyze(@PathVariable Long encounterId,@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);access.decision(actor(u));Encounter e=encounter(encounterId,u);
    if(u.role().equals("DOCTOR") && !Objects.equals(e.doctorId,u.doctorId())) throw error(HttpStatus.FORBIDDEN,"Acces interzis");
    AiResponse response;
    List<Encounter> accessibleEncounters=visibleEncounters(u);
    List<String> history=accessibleEncounters.stream().filter(old->old.patientId.equals(e.patientId) && !old.id.equals(e.id) && old.createdAt.isBefore(e.createdAt))
      .sorted(Comparator.comparing((Encounter old)->old.createdAt).reversed()).limit(10)
      .map(old->{String text=old.assessment==null?old.symptoms:old.assessment;return text.substring(0,Math.min(text.length(),2000));}).toList();
    Map<String,Object> clinicalParams=new HashMap<>(Map.of("hospital",e.hospitalId,"patient",e.patientId,"at",e.createdAt));
    clinicalParams.put("encIds",accessibleEncounters.stream().filter(old->old.patientId.equals(e.patientId)).map(old->old.id).toList());
    List<String> allergyList=clinicalDb.queryForList("SELECT substance FROM allergies WHERE hospital_id=:hospital AND patient_id=:patient AND status='ACTIVE' AND created_at<=:at ORDER BY id",clinicalParams,String.class);
    List<String> conditionList=clinicalDb.queryForList("SELECT DISTINCT d.description FROM diagnoses d JOIN encounters enc ON enc.id=d.encounter_id AND enc.hospital_id=d.hospital_id WHERE d.hospital_id=:hospital AND enc.patient_id=:patient AND enc.id IN (:encIds) AND enc.created_at<=:at AND d.created_at<=:at",clinicalParams,String.class);
    List<String> prescriptionHistory=clinicalDb.queryForList("SELECT DISTINCT pr.medicine FROM prescriptions pr JOIN encounters enc ON enc.id=pr.encounter_id AND enc.hospital_id=pr.hospital_id WHERE pr.hospital_id=:hospital AND enc.patient_id=:patient AND enc.id IN (:encIds) AND enc.created_at<=:at AND pr.created_at<=:at",clinicalParams,String.class);
    Patient aiPatient=patient(e.patientId,u);
    Map<String,Object> patientContext=new HashMap<>();
    patientContext.put("ageYears",Period.between(aiPatient.birthDate,e.createdAt.toLocalDate()).getYears());
    patientContext.put("allergies",allergyList.stream().limit(100).toList());
    // An empty allergy table does not document a completed negative allergy assessment.
    patientContext.put("allergiesReviewed",!allergyList.isEmpty());
    patientContext.put("conditions",conditionList.stream().limit(40).toList());
    // Prescriptions have no end-date/current-use field. Do not silently assume historical prescriptions remain active.
    patientContext.put("currentMedicines",List.of());patientContext.put("medicinesReviewed",false);
    patientContext.put("medicationHistory",prescriptionHistory.stream().limit(40).toList());
    patientContext.put("pregnancyStatus","UNKNOWN");
    List<String> recentLabs=clinicalDb.queryForList("SELECT test_code||': '||result||' '||unit FROM lab_results WHERE hospital_id=:hospital AND patient_id=:patient AND created_at<=:at AND (encounter_id IS NULL OR encounter_id IN (:encIds)) ORDER BY created_at DESC,id DESC LIMIT 20",clinicalParams,String.class);
    patientContext.put("recentLabResults",recentLabs.stream().map(text->text.substring(0,Math.min(text.length(),4500))).toList());
    Map<String,Object> aiInput=Map.of("symptoms",e.symptoms,"observations",e.observations==null?"":e.observations,"medicalHistory",history,"patientContext",patientContext);
    try {
      response=restClient.post().uri(aiUrl+"/triage").body(aiInput).retrieve().body(AiResponse.class);
    } catch(RestClientException ex) {
      throw error(HttpStatus.SERVICE_UNAVAILABLE,"Serviciul AI demonstrativ nu este disponibil");
    }
    if(response==null || !Boolean.TRUE.equals(response.demoOnly()) || response.insufficientData()==null || response.priority()==null
        || response.basis()==null || response.diagnosticSuggestions()==null || response.therapyOptions()==null || response.category()==null || response.category().isBlank() || response.category().length()>40
        || response.summary()==null || response.summary().isBlank() || response.evidence()==null
        || response.modelVersion()==null || response.modelVersion().isBlank() || response.modelVersion().length()>80)
      throw error(HttpStatus.BAD_GATEWAY,"Răspuns AI invalid");
    if(response.medicationOptions()==null || response.withheldMedications()==null || response.medicationNote()==null)
      throw error(HttpStatus.BAD_GATEWAY,"Serviciul de analiză necesită actualizare pentru opțiunile medicamentoase. Contactează administratorul.");
    AiAnalysis a=new AiAnalysis();a.hospitalId=u.hospitalId();a.encounterId=e.id;a.category=response.category();
    a.summary=response.summary();a.evidence=response.evidence();a.modelVersion=response.modelVersion();a.createdAt=LocalDateTime.now();
    try { com.fasterxml.jackson.databind.node.ObjectNode details=json.valueToTree(response);details.set("input",json.valueToTree(aiInput));a.details=json.writeValueAsString(details); }
    catch(com.fasterxml.jackson.core.JsonProcessingException ex) {throw error(HttpStatus.BAD_GATEWAY,"Detalii AI invalide");}
    a=analyses.save(a);log(u,"DEMO_AI_ANALYSIS","analysis:"+a.id);return a;
  }

  @GetMapping("/audit")
  public List<AuditEvent> audit(@AuthenticationPrincipal Jwt jwt) {
    UserCtx u=ctx(jwt);
    if(!u.role().equals("ADMIN")) throw error(HttpStatus.FORBIDDEN,"Acces rezervat administratorului");
    return audits.findTop25ByHospitalIdOrderByCreatedAtDesc(u.hospitalId());
  }
}
