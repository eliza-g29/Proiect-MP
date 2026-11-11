package ro.hospital.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="hospitals")
public class Hospital {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
  public Long id;
  @Column(name="name") public String name;
  @Column(name="address") public String address;
  public Hospital() {}
}
