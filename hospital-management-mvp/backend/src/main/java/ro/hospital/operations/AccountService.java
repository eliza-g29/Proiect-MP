package ro.hospital.operations;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import ro.hospital.domain.*;
import ro.hospital.repo.*;
import ro.hospital.service.AccessPolicy;
import ro.hospital.service.AccessPolicy.Actor;

@Service
public class AccountService {
  private final AppUserRepository users;private final DoctorRepository doctors;private final PatientRepository patients;
  private final PasswordEncoder passwords;private final NamedParameterJdbcTemplate db;private final AccessPolicy access;
  public AccountService(AppUserRepository users,DoctorRepository doctors,PatientRepository patients,PasswordEncoder passwords,NamedParameterJdbcTemplate db,AccessPolicy access) {
    this.users=users;this.doctors=doctors;this.patients=patients;this.passwords=passwords;this.db=db;this.access=access;
  }
  public record AccountInput(@NotBlank @Size(max=160) String name,@NotBlank @Email @Size(max=180) String email,
    @NotBlank String role,Long profileId,@NotBlank @Size(min=12,max=72) String password,@Size(max=120) String specialty,LocalDate birthDate) {}
  public record AccountUpdate(@NotBlank @Size(max=160) String name,@NotBlank @Email @Size(max=180) String email,
    @NotBlank String role,Long profileId,boolean active,@Size(max=120) String specialty) {}
  public record ResetInput(@NotBlank @Size(min=12,max=72) String temporaryPassword) {}
  private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
  private void audit(Actor a,String action,String resource) {
    db.update("INSERT INTO audit_events(hospital_id,actor_id,action,resource) VALUES(:h,:a,:action,:resource)",Map.of("h",a.hospitalId(),"a",a.id(),"action",action,"resource",resource));
  }
  public List<Map<String,Object>> list(Actor a) {
    access.admin(a);return db.queryForList("SELECT id,name,email,role,doctor_id,patient_id,active,must_change_password,staff_specialty FROM app_users WHERE hospital_id=:h ORDER BY name",Map.of("h",a.hospitalId()));
  }
  private void profile(AppUser u,String role,Long id) {
    if(!Set.of("ADMIN","DOCTOR","STAFF","PATIENT").contains(role)) throw bad("Rol necunoscut");
    u.role=role;u.doctorId=null;u.patientId=null;
    if(role.equals("DOCTOR")) {
      if(id==null || doctors.findByIdAndHospitalId(id,u.hospitalId).isEmpty()) throw bad("Selectează un profil de medic din spital");u.doctorId=id;
    } else if(role.equals("PATIENT")) {
      if(id==null || patients.findByIdAndHospitalId(id,u.hospitalId).isEmpty()) throw bad("Selectează un profil de pacient din spital");u.patientId=id;
    }
  }
  private void checkedAdmin(Actor a) {
    access.admin(a);db.queryForObject("SELECT id FROM hospitals WHERE id=:h FOR UPDATE",Map.of("h",a.hospitalId()),Long.class);
    if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT FROM app_users WHERE hospital_id=:h AND id=:actor AND active AND role='ADMIN')",Map.of("h",a.hospitalId(),"actor",a.id()),Boolean.class)))throw access.denied();
  }
  private String encode(String password) {
    if(password.length()<12) throw bad("Parola trebuie să aibă minimum 12 caractere");
    if(password.getBytes(StandardCharsets.UTF_8).length>72) throw bad("Parola depășește 72 de octeți");return passwords.encode(password);
  }
  @Transactional public Map<String,Object> create(AccountInput input,Actor a) {
    checkedAdmin(a);AppUser u=new AppUser();u.hospitalId=a.hospitalId();u.name=input.name().trim();u.email=input.email().trim().toLowerCase(Locale.ROOT);
    Long profileId=input.profileId();
    if(profileId==null && input.role().equals("DOCTOR")) {
      if(input.specialty()==null || input.specialty().isBlank()) throw bad("Specializarea este obligatorie");
      Doctor d=new Doctor();d.hospitalId=a.hospitalId();d.name=u.name;d.email=u.email;d.specialty=input.specialty().trim();profileId=doctors.save(d).id;
    }
    if(profileId==null && input.role().equals("PATIENT")) {
      if(input.birthDate()==null || input.birthDate().isAfter(LocalDate.now())) throw bad("Data nașterii este obligatorie și trebuie să fie validă");
      Patient p=new Patient();p.hospitalId=a.hospitalId();p.firstName=u.name;p.lastName="";p.birthDate=input.birthDate();p.email=u.email;p.createdAt=LocalDateTime.now();
      // The user can choose an existing patient instead, preserving structured names.
      String[] names=u.name.split("\\s+",2);p.firstName=names[0];p.lastName=names.length>1?names[1]:names[0];
      if(p.firstName.length()>100 || p.lastName.length()>100) throw bad("Numele sau prenumele depășește 100 de caractere");
      profileId=patients.save(p).id;
    }
    profile(u,input.role(),profileId);u.staffSpecialty=input.role().equals("STAFF")?input.specialty():null;u.passwordHash=encode(input.password());u.mustChangePassword=true;users.saveAndFlush(u);
    audit(a,"ACCOUNT_CREATED","user:"+u.id);return Map.of("id",u.id);
  }
  @Transactional public Map<String,Object> update(Long id,AccountUpdate input,Actor a) {
    checkedAdmin(a);AppUser u=users.findLockedById(id).filter(x->x.hospitalId.equals(a.hospitalId())).orElseThrow(()->bad("Cont inexistent"));
    if(u.id.equals(a.id()) && (!input.active() || !input.role().equals("ADMIN"))) throw bad("Nu poți dezactiva sau retrograda propriul cont de administrator");
    u.name=input.name().trim();u.email=input.email().trim().toLowerCase(Locale.ROOT);profile(u,input.role(),input.profileId());u.active=input.active();u.staffSpecialty=input.role().equals("STAFF")?input.specialty():null;u.tokenVersion++;users.saveAndFlush(u);
    audit(a,"ACCOUNT_UPDATED","user:"+u.id);return Map.of("id",u.id);
  }
  @Transactional public void reset(Long id,ResetInput input,Actor a) {
    checkedAdmin(a);AppUser u=users.findLockedById(id).filter(x->x.hospitalId.equals(a.hospitalId())).orElseThrow(()->bad("Cont inexistent"));
    if(id.equals(a.id())) throw bad("Folosește schimbarea parolei din Contul meu");
    u.passwordHash=encode(input.temporaryPassword());u.mustChangePassword=true;u.tokenVersion++;users.save(u);
    audit(a,"ACCESS_RECOVERED","user:"+id);
  }
}
