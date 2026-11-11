package ro.hospital.repo;
import ro.hospital.domain.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
public interface HospitalRepository extends JpaRepository<Hospital,Long> {

}
