package ro.hospital.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="appointments")
public class Appointment {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
  public Long id;
  @Column(name="hospital_id") public Long hospitalId;
  @Column(name="patient_id") public Long patientId;
  @Column(name="doctor_id") public Long doctorId;
  @Column(name="start_at") public LocalDateTime startAt;
  @Column(name="reason") public String reason;
  @Column(name="status") public String status;
  @Column(name="created_at") public LocalDateTime createdAt;
  @Version @Column(name="version") public long version;
  public Appointment() {}
}
