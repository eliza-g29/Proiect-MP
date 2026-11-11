package ro.hospital.repo;
import ro.hospital.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AppUserRepository extends JpaRepository<AppUser,Long> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id=:id")
  java.util.Optional<AppUser> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
  java.util.Optional<AppUser> findByEmailIgnoreCase(String email);
}
