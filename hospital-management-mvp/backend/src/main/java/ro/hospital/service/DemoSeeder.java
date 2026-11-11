package ro.hospital.service;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import ro.hospital.domain.*;
import ro.hospital.repo.*;

@Configuration
public class DemoSeeder {
  @Bean CommandLineRunner demoData(
      @Value("${app.demo-seed}") boolean enabled,
      HospitalRepository hospitals, DoctorRepository doctors, PatientRepository patients,
      AppUserRepository users, AppointmentRepository appointments, EncounterRepository encounters,
      PasswordEncoder encoder, PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
    return args -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      if (!enabled || hospitals.count() > 0) return;
      Hospital h = new Hospital(); h.name="Spitalul Demo București"; h.address="București (date fictive)"; h=hospitals.save(h);
      Doctor d1=doctor(h.id,"Dr. Andrei Popescu","Medicină internă","andrei@demo.ro"); doctors.save(d1);
      Doctor d2=doctor(h.id,"Dr. Maria Ionescu","Cardiologie","maria@demo.ro"); doctors.save(d2);
      Doctor d3=doctor(h.id,"Dr. Elena Radu","Medicină de familie","elena@demo.ro"); doctors.save(d3);
      Patient p1=patient(h.id,"Ana","Marinescu",LocalDate.of(1994,4,12),"0722000001","ana@example.test"); patients.save(p1);
      Patient p2=patient(h.id,"Mihai","Dumitrescu",LocalDate.of(1982,9,5),"0722000002","mihai@example.test"); patients.save(p2);
      Patient p3=patient(h.id,"Ioana","Georgescu",LocalDate.of(2001,1,21),"0722000003","ioana@example.test"); patients.save(p3);
      Patient p4=patient(h.id,"Victor","Petrescu",LocalDate.of(1975,7,19),"0722000004","victor@example.test"); patients.save(p4);
      Patient p5=patient(h.id,"Alexandra","Stan",LocalDate.of(1999,2,28),"0722000005","alexandra@example.test"); patients.save(p5);
      String pass=encoder.encode("Demo1234!");
      users.save(user(h.id,"Administrator Demo","admin@demo.ro",pass,"ADMIN",null,null));
      users.save(user(h.id,"Dr. Andrei Popescu","doctor@demo.ro",pass,"DOCTOR",d1.id,null));
      users.save(user(h.id,"Ana Marinescu","pacient@demo.ro",pass,"PATIENT",null,p1.id));
      LocalDate day = LocalDate.now().plusDays(1);
      for(Doctor doctor : java.util.List.of(d1,d2,d3)) {
        for(int offset=0;offset<31;offset++) {
          LocalDate workDay=LocalDate.now().plusDays(offset);
          jdbc.update("INSERT INTO work_periods(hospital_id,doctor_id,start_at,end_at) VALUES(?,?,?,?)",
            h.id,doctor.id,java.sql.Timestamp.valueOf(workDay.atTime(8,0)),java.sql.Timestamp.valueOf(workDay.atTime(20,0)));
        }
      }
      appointments.save(appt(h.id,p1.id,d1.id,day.atTime(9,0),"Consult de rutină"));
      appointments.save(appt(h.id,p2.id,d2.id,day.atTime(10,30),"Evaluare clinică"));
      appointments.save(appt(h.id,p3.id,d3.id,day.atTime(12,0),"Control periodic"));
      appointments.save(appt(h.id,p4.id,d1.id,day.plusDays(1).atTime(11,0),"Consultație"));
      Encounter e = new Encounter(); e.hospitalId=h.id; e.patientId=p1.id; e.doctorId=d1.id;
      e.symptoms="Oboseală ușoară, pentru demonstrație";
      e.observations="Date exclusiv fictive"; e.assessment="Exemplu de notă medicală demonstrativă";
      e.createdAt=LocalDateTime.now().minusDays(2); encounters.save(e);
    });
  }
  private static Doctor doctor(Long h,String n,String s,String e) {
    Doctor x=new Doctor(); x.hospitalId=h;x.name=n;x.specialty=s;x.email=e;return x;
  }
  private static Patient patient(Long h,String f,String l,LocalDate b,String p,String e) {
    Patient x=new Patient();x.hospitalId=h;x.firstName=f;x.lastName=l;x.birthDate=b;x.phone=p;x.email=e;x.createdAt=LocalDateTime.now();return x;
  }
  private static AppUser user(Long h,String n,String e,String p,String r,Long d,Long pt) {
    AppUser x=new AppUser();x.hospitalId=h;x.name=n;x.email=e;x.passwordHash=p;x.role=r;x.doctorId=d;x.patientId=pt;return x;
  }
  private static Appointment appt(Long h,Long p,Long d,LocalDateTime t,String reason) {
    Appointment x=new Appointment();x.hospitalId=h;x.patientId=p;x.doctorId=d;x.startAt=t;x.reason=reason;x.status="SCHEDULED";x.createdAt=LocalDateTime.now();return x;
  }
}
