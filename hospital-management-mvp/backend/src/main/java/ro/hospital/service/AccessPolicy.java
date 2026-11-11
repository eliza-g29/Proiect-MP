package ro.hospital.service;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.domain.*;
import ro.hospital.repo.*;
import ro.hospital.operations.SharingService;

@Service
public class AccessPolicy {
  public record Actor(Long id, Long hospitalId, String role, Long doctorId, Long patientId) {
    public boolean staff() { return role.equals("ADMIN") || role.equals("DOCTOR") || role.equals("STAFF"); }
  }
  private final SharingService sharing;
  private final PatientRepository patients;
  private final EncounterRepository encounters;
  private final AiAnalysisRepository analyses;
  public AccessPolicy(PatientRepository patients, EncounterRepository encounters, AiAnalysisRepository analyses, SharingService sharing) {
    this.sharing=sharing;this.patients=patients;this.encounters=encounters;this.analyses=analyses;
  }
  public void staff(Actor a) { if(!a.staff()) throw denied(); }
  public void admin(Actor a) { if(!a.role().equals("ADMIN")) throw denied(); }
  public ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN,"Acces interzis pentru acest profil"); }
  public List<Patient> patients(Actor a) {
    if(a.role().equals("PATIENT")) return List.of(patient(a.patientId(),a));
    if(a.role().equals("ADMIN")) return patients.findByHospitalIdOrderByLastNameAsc(a.hospitalId());
    Map<Long,Patient> result=new LinkedHashMap<>();
    associated(a).forEach(p->result.put(p.id,p));
    sharing.patients(a,false).forEach(id->patients.findByIdAndHospitalId(id,a.hospitalId()).ifPresent(p->result.put(p.id,p)));
    return new ArrayList<>(result.values());
  }
  public Patient patient(Long id,Actor a) {
    Patient p=patients.findByIdAndHospitalId(id,a.hospitalId())
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Pacient inexistent"));
    if(a.role().equals("PATIENT") && !Objects.equals(a.patientId(),id)) throw denied();
    if(!a.role().equals("ADMIN") && !a.role().equals("PATIENT") && patients(a).stream().noneMatch(x->x.id.equals(id))) throw denied();
    return p;
  }
  private List<Patient> associated(Actor a) {
    return patients.findVisibleToDoctor(a.hospitalId(),a.doctorId()==null?-1L:a.doctorId(),a.id());
  }
  public Patient writePatient(Long id,Actor a) {
    Patient p=patient(id,a);
    if(!a.role().equals("ADMIN") && !a.role().equals("PATIENT")
        && associated(a).stream().noneMatch(x->x.id.equals(id)) && !sharing.patients(a,true).contains(id)) throw denied();
    return p;
  }
  public Encounter writeEncounter(Long id,Actor a) {
    staff(a);Encounter e=encounter(id,a);
    if(!a.role().equals("ADMIN") && !Objects.equals(e.doctorId,a.doctorId()) && !sharing.patients(a,true).contains(e.patientId)) throw denied();
    return e;
  }
  public void decision(Actor a) { if(!a.role().equals("ADMIN") && !a.role().equals("DOCTOR")) throw denied(); }
  public List<Encounter> encounters(Actor a) {
    Set<Long> shared=a.staff() && !a.role().equals("ADMIN")?sharing.patients(a,false):Set.of();
    return encounters.findByHospitalIdOrderByCreatedAtDesc(a.hospitalId()).stream()
      .filter(e->a.role().equals("ADMIN") || (a.role().equals("DOCTOR") && Objects.equals(e.doctorId,a.doctorId()))
        || (a.role().equals("PATIENT") && e.patientVisible && Objects.equals(e.patientId,a.patientId())) || shared.contains(e.patientId)).toList();
  }
  public Encounter encounter(Long id,Actor a) {
    Encounter e=encounters.findByIdAndHospitalId(id,a.hospitalId())
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Consultație inexistentă"));
    if(a.staff() && !a.role().equals("ADMIN") && !Objects.equals(e.doctorId,a.doctorId()) && !sharing.patients(a,false).contains(e.patientId)) throw denied();
    if(a.role().equals("PATIENT") && (!e.patientVisible || !Objects.equals(e.patientId,a.patientId()))) throw denied();
    return e;
  }
  public AiAnalysis analysis(Long id,Actor a) {
    staff(a);
    AiAnalysis v=analyses.findById(id).filter(x->x.hospitalId.equals(a.hospitalId()))
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Analiză inexistentă"));
    encounter(v.encounterId,a);return v;
  }
}
