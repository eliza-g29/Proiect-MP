package ro.hospital.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="patients")
public class Patient {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
  public Long id;
  @Column(name="hospital_id") public Long hospitalId;
  @Column(name="first_name") public String firstName;
  @Column(name="last_name") public String lastName;
  @Column(name="birth_date") public LocalDate birthDate;
  @Column(name="phone") public String phone;
  @Column(name="email") public String email;
  @Column(name="created_at") public LocalDateTime createdAt;
  public Patient() {}
}
