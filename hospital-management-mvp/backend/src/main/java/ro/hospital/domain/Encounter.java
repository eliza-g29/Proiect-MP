package ro.hospital.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="encounters")
public class Encounter {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
  public Long id;
  @Column(name="hospital_id") public Long hospitalId;
  @Column(name="patient_id") public Long patientId;
  @Column(name="doctor_id") public Long doctorId;
  @Column(name="appointment_id") public Long appointmentId;
  @Column(name="symptoms",columnDefinition="TEXT") public String symptoms;
  @Column(name="observations",columnDefinition="TEXT") public String observations;
  @Column(name="assessment",columnDefinition="TEXT") public String assessment;
  @Column(name="created_at") public LocalDateTime createdAt;
  @Column(name="patient_visible") public boolean patientVisible=true;
  public Encounter() {}
}
