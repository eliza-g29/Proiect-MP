package ro.hospital.repo;
import ro.hospital.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditEventRepository extends JpaRepository<AuditEvent,Long> {
  java.util.List<AuditEvent> findTop25ByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
