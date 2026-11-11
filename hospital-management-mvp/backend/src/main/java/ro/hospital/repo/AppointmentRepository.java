package ro.hospital.repo;
import ro.hospital.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AppointmentRepository extends JpaRepository<Appointment,Long> {
  java.util.List<Appointment> findByHospitalIdOrderByStartAtAsc(Long hospitalId);
  java.util.List<Appointment> findByPatientIdOrderByStartAtAsc(Long patientId);
  java.util.Optional<Appointment> findByIdAndHospitalId(Long id, Long hospitalId);
  long countByHospitalIdAndStatus(Long hospitalId,String status);
}
