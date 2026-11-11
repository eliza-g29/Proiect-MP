package ro.hospital.repo;
import ro.hospital.domain.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DoctorRepository extends JpaRepository<Doctor,Long> {
  java.util.List<Doctor> findByHospitalIdOrderByNameAsc(Long hospitalId);
  java.util.Optional<Doctor> findByIdAndHospitalId(Long id, Long hospitalId);
}
