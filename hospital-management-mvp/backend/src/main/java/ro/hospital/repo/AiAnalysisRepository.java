package ro.hospital.repo;
import ro.hospital.domain.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis,Long> {
  java.util.List<AiAnalysis> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
  java.util.List<AiAnalysis> findByEncounterIdOrderByCreatedAtDesc(Long encounterId);
  long countByHospitalId(Long hospitalId);
}
