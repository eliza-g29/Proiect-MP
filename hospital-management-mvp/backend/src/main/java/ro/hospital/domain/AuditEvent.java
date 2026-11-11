package ro.hospital.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="audit_events")
public class AuditEvent {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
  public Long id;
  @Column(name="hospital_id") public Long hospitalId;
  @Column(name="actor_id") public Long actorId;
  @Column(name="action") public String action;
  @Column(name="resource") public String resource;
  @Column(name="created_at") public LocalDateTime createdAt;
  public AuditEvent() {}
}
