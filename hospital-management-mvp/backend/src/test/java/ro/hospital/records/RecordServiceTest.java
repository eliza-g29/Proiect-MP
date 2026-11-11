package ro.hospital.records;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.repo.AuditEventRepository;
import ro.hospital.service.AccessPolicy;

class RecordServiceTest {
  @TempDir Path storage;
  NamedParameterJdbcTemplate db=mock(NamedParameterJdbcTemplate.class);
  AccessPolicy access=mock(AccessPolicy.class);
  AuditEventRepository audits=mock(AuditEventRepository.class);
  AccessPolicy.Actor doctor=new AccessPolicy.Actor(7L,1L,"DOCTOR",11L,null);
  RecordService service;
  @BeforeEach void setup() {
    service=new RecordService(db,access,audits,storage.toString());
    var analysis=new ro.hospital.domain.AiAnalysis();analysis.id=5L;analysis.encounterId=1L;
    when(access.analysis(5L,doctor)).thenReturn(analysis);
    when(db.queryForMap(anyString(),anyMap())).thenAnswer(i->{Map<String,Object> row=new HashMap<>(i.<Map<String,Object>>getArgument(1));row.put("id",1L);return row;});
  }
  Map<String,Object> diagnosis() {
    return new HashMap<>(Map.of("encounter_id","1","code","DEMO","description","Diagnostic fictiv",
      "coding_system","LOCAL_DEMO","coding_version","1"));
  }
  @Test void diagnosisAuthorComesFromAuthenticatedAccount() {
    Map<String,Object> row=service.create("diagnoses",diagnosis(),doctor);
    assertEquals(7L,row.get("confirmed_by"));assertEquals(1L,row.get("hospital_id"));
    verify(access).staff(doctor);verify(access).writeEncounter(1L,doctor);
  }
  @Test void forgedConfirmationOrTenantIsRejected() {
    Map<String,Object> input=diagnosis();input.put("confirmed_by",99L);
    assertThrows(ResponseStatusException.class,()->service.create("diagnoses",input,doctor));
    input.remove("confirmed_by");input.put("hospital_id",2L);
    assertThrows(ResponseStatusException.class,()->service.create("diagnoses",input,doctor));
    verifyNoInteractions(db);
  }
  @Test void missingCodingProvenanceIsRejected() {
    Map<String,Object> input=diagnosis();input.remove("coding_version");
    assertThrows(ResponseStatusException.class,()->service.create("diagnoses",input,doctor));
    verifyNoInteractions(db);
  }
  @Test void tableNameCannotBeInjected() {
    assertThrows(ResponseStatusException.class,()->service.list("app_users",doctor));
    assertThrows(ResponseStatusException.class,()->service.create("diagnoses; DROP TABLE patients",diagnosis(),doctor));
    verifyNoInteractions(db);
  }
  @Test void outOfScopeEncounterCannotBeWritten() {
    when(access.writeEncounter(1L,doctor)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
    assertThrows(ResponseStatusException.class,()->service.create("diagnoses",diagnosis(),doctor));
    verifyNoInteractions(db);
  }
  @Test void acceptingAiStoresReviewOnly() {
    Map<String,Object> row=service.create("ai-reviews",Map.of("analysis_id","5","decision","ACCEPTED","notes","Revizuire fictivă"),doctor);
    assertEquals(7L,row.get("reviewer_id"));
    ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);
    verify(db,times(1)).queryForMap(sql.capture(),anyMap());
    assertTrue(sql.getValue().startsWith("INSERT INTO ai_reviews "));
    verify(access).analysis(5L,doctor);
  }
  @Test void departmentalWritesRequireAdministrator() {
    doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(access).admin(doctor);
    assertThrows(ResponseStatusException.class,()->service.create("departments",Map.of("name","Demo"),doctor));
    verifyNoInteractions(db);
  }
  @Test void documentDownloadChecksPatientBeforeOpeningFile() throws Exception {
    when(db.queryForList(anyString(),anyMap())).thenReturn(List.of(new HashMap<>(Map.of(
      "patient_id",21L,"storage_key","private-file","original_name","demo.txt"))));
    when(access.patient(21L,doctor)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
    assertThrows(ResponseStatusException.class,()->service.download(1L,doctor));
    verifyNoInteractions(audits);
  }
  @Test void authorizedDocumentDownloadReturnsStoredContent() throws Exception {
    java.nio.file.Files.writeString(storage.resolve("private-file"),"Document fictiv");
    when(db.queryForList(anyString(),anyMap())).thenReturn(List.of(new HashMap<>(Map.of(
      "patient_id",21L,"storage_key","private-file","original_name","demo.txt"))));
    var doc=service.download(1L,doctor);
    assertEquals("demo.txt",doc.name());
    assertEquals("Document fictiv",new String(doc.content(),java.nio.charset.StandardCharsets.UTF_8));
    verify(access).patient(21L,doctor);verify(audits).save(any());
  }
  @Test void internalDocumentCannotBeDownloadedByItsPatient() throws Exception {
    when(db.queryForList(anyString(),anyMap())).thenReturn(List.of(new HashMap<>(Map.of(
      "patient_id",21L,"patient_visible",false,"storage_key","private-file","original_name","demo.txt"))));
    var patient=new AccessPolicy.Actor(8L,1L,"PATIENT",null,21L);
    assertThrows(ResponseStatusException.class,()->service.download(1L,patient));
    verifyNoInteractions(audits);
  }
  @Test void readOnlyGrantCannotBeUsedToReviewAiAnalysis() {
    when(access.writeEncounter(1L,doctor)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
    assertThrows(ResponseStatusException.class,()->service.create("ai-reviews",Map.of("analysis_id","5","decision","ACCEPTED","notes","Demo"),doctor));
    verifyNoInteractions(db);
  }
  @Test void patientListNeverQueriesUnscopedRows() {
    AccessPolicy.Actor patient=new AccessPolicy.Actor(8L,1L,"PATIENT",null,21L);
    when(access.patients(patient)).thenReturn(List.of());
    when(db.queryForList(anyString(),anyMap())).thenReturn(new ArrayList<>());
    service.list("allergies",patient);
    ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);
    verify(db).queryForList(sql.capture(),anyMap());
    assertTrue(sql.getValue().contains("r.hospital_id=:hospital"));
    assertTrue(sql.getValue().contains("r.patient_id IN (:ids)"));
  }
  @Test void patientFilterRequiresAuthorizationBeforeQuerying() {
    when(access.patient(99L,doctor)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
    assertThrows(ResponseStatusException.class,()->service.list("lab-results",99L,doctor));
    verifyNoInteractions(db);
  }
  @Test void patientFilterRetainsTenantAndExistingRecordVisibilityScope() {
    when(db.queryForList(anyString(),anyMap())).thenReturn(new ArrayList<>());
    service.list("lab-results",21L,doctor);
    var sql=ArgumentCaptor.forClass(String.class);
    verify(db).queryForList(sql.capture(),anyMap());
    verify(access).patient(21L,doctor);
    assertTrue(sql.getValue().contains("r.hospital_id=:hospital"));
    assertTrue(sql.getValue().contains("r.patient_id=:patient"));
    assertTrue(sql.getValue().contains("r.patient_id IN (:ids)"));
    assertTrue(sql.getValue().contains("r.encounter_id IN (:encIds)"));
  }
  Map<String,Object> lab(boolean visible) {
    return new HashMap<>(Map.of("patient_id","21","test_code","HB","result","13.2","unit","g/dl","coding_system","LOCAL","coding_version","1","patient_visible",visible));
  }
  @Test void visibleLabResultNotifiesOnlyItsPatientAccount() {
    service.create("lab-results",lab(true),doctor);
    var sql=ArgumentCaptor.forClass(String.class);
    verify(db).update(sql.capture(),anyMap());
    assertTrue(sql.getValue().contains("hospital_id=:h AND patient_id=:patient AND active"));
  }
  @Test void internalLabResultDoesNotNotifyPatient() {
    service.create("lab-results",lab(false),doctor);
    verify(db,never()).update(contains("INSERT INTO notifications"),anyMap());
  }

}
