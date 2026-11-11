package ro.hospital.repo;
import ro.hospital.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface PatientRepository extends JpaRepository<Patient,Long> {
  java.util.List<Patient> findByHospitalIdOrderByLastNameAsc(Long hospitalId);
  java.util.Optional<Patient> findByIdAndHospitalId(Long id, Long hospitalId);
  long countByHospitalId(Long hospitalId);
  @Query("""
    select p from Patient p where p.hospitalId = :hospitalId and (
      exists (select a.id from Appointment a where a.hospitalId = :hospitalId
        and a.patientId = p.id and a.doctorId = :doctorId)
      or exists (select e.id from Encounter e where e.hospitalId = :hospitalId
        and e.patientId = p.id and e.doctorId = :doctorId)
      or exists (select v.id from AuditEvent v where v.hospitalId = :hospitalId
        and v.actorId = :actorId and v.action = 'PATIENT_CREATED'
        and v.resource = concat('patient:', cast(p.id as string))))
    order by p.lastName asc
    """)
  java.util.List<Patient> findVisibleToDoctor(@Param("hospitalId") Long hospitalId,
    @Param("doctorId") Long doctorId, @Param("actorId") Long actorId);
}
