package ro.hospital.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.domain.*;
import ro.hospital.repo.*;
import ro.hospital.operations.SharingService;

class AccessPolicyTest {
  PatientRepository patients=mock(PatientRepository.class);
  EncounterRepository encounters=mock(EncounterRepository.class);
  AiAnalysisRepository analyses=mock(AiAnalysisRepository.class);
  SharingService sharing=mock(SharingService.class);
  AccessPolicy access=new AccessPolicy(patients,encounters,analyses,sharing);
  AccessPolicy.Actor doctor=new AccessPolicy.Actor(7L,1L,"DOCTOR",11L,null);
  AccessPolicy.Actor patient=new AccessPolicy.Actor(8L,1L,"PATIENT",null,21L);
  Patient p;
  @BeforeEach void setup() {
    p=new Patient();p.id=21L;p.hospitalId=1L;
    when(patients.findByIdAndHospitalId(21L,1L)).thenReturn(Optional.of(p));
  }
  @Test void doctorCannotReadUnassociatedPatient() {
    when(patients.findVisibleToDoctor(1L,11L,7L)).thenReturn(List.of());
    assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,()->access.patient(21L,doctor)).getStatusCode());
  }
  @Test void doctorCanReadAssociatedPatient() {
    when(patients.findVisibleToDoctor(1L,11L,7L)).thenReturn(List.of(p));
    assertSame(p,access.patient(21L,doctor));
  }
  @Test void crossHospitalPatientIsNotFound() {
    assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,()->access.patient(99L,doctor)).getStatusCode());
  }
  @Test void patientCannotReadAnotherPatient() {
    Patient other=new Patient();other.id=22L;other.hospitalId=1L;
    when(patients.findByIdAndHospitalId(22L,1L)).thenReturn(Optional.of(other));
    assertThrows(ResponseStatusException.class,()->access.patient(22L,patient));
    assertSame(p,access.patient(21L,patient));
  }
  @Test void doctorsCannotReadOtherDoctorsEncounters() {
    Encounter own=new Encounter();own.id=1L;own.doctorId=11L;own.patientId=21L;own.hospitalId=1L;
    Encounter other=new Encounter();other.id=2L;other.doctorId=12L;other.patientId=21L;other.hospitalId=1L;
    when(encounters.findByHospitalIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(own,other));
    when(encounters.findByIdAndHospitalId(2L,1L)).thenReturn(Optional.of(other));
    assertEquals(List.of(own),access.encounters(doctor));
    assertThrows(ResponseStatusException.class,()->access.encounter(2L,doctor));
    assertEquals(List.of(own,other),access.encounters(patient));
  }
  @Test void readGrantAllowsCollaborationButNotWrites() {
    Encounter e=new Encounter();e.id=2L;e.doctorId=12L;e.patientId=21L;e.hospitalId=1L;
    when(encounters.findByIdAndHospitalId(2L,1L)).thenReturn(Optional.of(e));
    when(sharing.patients(doctor,false)).thenReturn(Set.of(21L));
    when(sharing.patients(doctor,true)).thenReturn(Set.of());
    assertSame(e,access.encounter(2L,doctor));
    assertThrows(ResponseStatusException.class,()->access.writeEncounter(2L,doctor));
    assertThrows(ResponseStatusException.class,()->access.writePatient(21L,doctor));
  }
  @Test void staffWriteGrantDoesNotAuthorizeClinicalDecision() {
    var staff=new AccessPolicy.Actor(9L,1L,"STAFF",null,null);
    when(patients.findVisibleToDoctor(1L,-1L,9L)).thenReturn(List.of());
    when(sharing.patients(staff,false)).thenReturn(Set.of(21L));when(sharing.patients(staff,true)).thenReturn(Set.of(21L));
    assertSame(p,access.writePatient(21L,staff));assertTrue(staff.staff());
    assertThrows(ResponseStatusException.class,()->access.decision(staff));
  }
  @Test void internalEncounterIsHiddenFromItsPatient() {
    Encounter e=new Encounter();e.id=3L;e.hospitalId=1L;e.patientId=21L;e.doctorId=11L;e.patientVisible=false;
    when(encounters.findByIdAndHospitalId(3L,1L)).thenReturn(Optional.of(e));
    when(encounters.findByHospitalIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(e));
    assertTrue(access.encounters(patient).isEmpty());
    assertThrows(ResponseStatusException.class,()->access.encounter(3L,patient));
    assertSame(e,access.encounter(3L,doctor));
  }
  @Test void patientCannotReviewAi() {
    assertThrows(ResponseStatusException.class,()->access.analysis(1L,patient));
    verifyNoInteractions(analyses);
  }
}
