package ro.hospital.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="doctors")
public class Doctor {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
  public Long id;
  @Column(name="hospital_id") public Long hospitalId;
  @Column(name="name") public String name;
  @Column(name="specialty") public String specialty;
  @Column(name="email") public String email;
  public Doctor() {}
}
