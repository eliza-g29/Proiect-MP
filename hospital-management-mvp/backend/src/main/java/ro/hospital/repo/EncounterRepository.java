package ro.hospital.repo;
import ro.hospital.domain.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EncounterRepository extends JpaRepository<Encounter,Long> {
  java.util.List<Encounter> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
  java.util.List<Encounter> findByPatientIdOrderByCreatedAtDesc(Long patientId);
  java.util.Optional<Encounter> findByIdAndHospitalId(Long id, Long hospitalId);
}
