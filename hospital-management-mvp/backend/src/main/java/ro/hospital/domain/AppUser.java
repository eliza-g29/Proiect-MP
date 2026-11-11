package ro.hospital.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="app_users")
public class AppUser {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
  public Long id;
  @Column(name="hospital_id") public Long hospitalId;
  @Column(name="name") public String name;
  @Column(name="email") public String email;
  @Column(name="password_hash") public String passwordHash;
  @Column(name="role") public String role;
  @Column(name="doctor_id") public Long doctorId;
  @Column(name="patient_id") public Long patientId;
  @Column(name="active") public boolean active=true;
  @Column(name="token_version") public long tokenVersion=0;
  @Column(name="must_change_password") public boolean mustChangePassword=false;
  @Column(name="staff_specialty") public String staffSpecialty;
  public AppUser() {}
}
