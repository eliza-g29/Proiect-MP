package ro.hospital.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="ai_analyses")
public class AiAnalysis {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
  public Long id;
  @Column(name="hospital_id") public Long hospitalId;
  @Column(name="encounter_id") public Long encounterId;
  @Column(name="category") public String category;
  @Column(name="summary",columnDefinition="TEXT") public String summary;
  @Column(name="evidence",columnDefinition="TEXT") public String evidence;
  @Column(name="model_version") public String modelVersion;
  @Column(name="created_at") public LocalDateTime createdAt;
  @Column(name="details",columnDefinition="TEXT") public String details="{}";
  public AiAnalysis() {}
}
