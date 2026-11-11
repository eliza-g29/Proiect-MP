package ro.hospital.operations;

import java.time.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ro.hospital.repo.DoctorRepository;
import ro.hospital.service.ActorResolver;
import ro.hospital.service.AccessPolicy;

@RestController @RequestMapping("/api")
public class OperationsController {
  private final ActorResolver identity;private final AccountService accounts;private final ScheduleService schedules;
  private final NotificationService notifications;private final SharingService sharing;private final ReportService reports;
  private final StaffScheduleService staffSchedules;
  private final DoctorRepository doctors;private final AccessPolicy access;
  public OperationsController(ActorResolver identity,AccountService accounts,ScheduleService schedules,NotificationService notifications,SharingService sharing,ReportService reports,DoctorRepository doctors,AccessPolicy access,StaffScheduleService staffSchedules) {
    this.staffSchedules=staffSchedules;this.identity=identity;this.accounts=accounts;this.schedules=schedules;this.notifications=notifications;this.sharing=sharing;this.reports=reports;this.doctors=doctors;this.access=access;
  }
  @GetMapping("/accounts") public Object accounts(@AuthenticationPrincipal Jwt jwt) { return accounts.list(identity.actor(jwt)); }
  @PostMapping("/accounts") @ResponseStatus(HttpStatus.CREATED)
  public Object createAccount(@Valid @RequestBody AccountService.AccountInput input,@AuthenticationPrincipal Jwt jwt) {return accounts.create(input,identity.actor(jwt));}
  @PatchMapping("/accounts/{id}") public Object account(@PathVariable Long id,@Valid @RequestBody AccountService.AccountUpdate input,@AuthenticationPrincipal Jwt jwt) {return accounts.update(id,input,identity.actor(jwt));}
  @PostMapping("/accounts/{id}/recover") public Map<String,String> recover(@PathVariable Long id,@Valid @RequestBody AccountService.ResetInput input,@AuthenticationPrincipal Jwt jwt) {accounts.reset(id,input,identity.actor(jwt));return Map.of("status","ok");}
  @GetMapping("/work-periods") public Object work(@AuthenticationPrincipal Jwt jwt) {return schedules.periods(identity.actor(jwt));}
  @PostMapping("/work-periods") public Object work(@Valid @RequestBody ScheduleService.Period input,@AuthenticationPrincipal Jwt jwt) {return schedules.save(null,input,identity.actor(jwt));}
  @PatchMapping("/work-periods/{id}") public Object work(@PathVariable Long id,@Valid @RequestBody ScheduleService.Period input,@AuthenticationPrincipal Jwt jwt) {return schedules.save(id,input,identity.actor(jwt));}
  @DeleteMapping("/work-periods/{id}") public Map<String,String> deleteWork(@PathVariable Long id,@AuthenticationPrincipal Jwt jwt) {schedules.delete(id,identity.actor(jwt));return Map.of("status","ok");}
  @GetMapping("/staff-work-periods") public Object staffWork(@AuthenticationPrincipal Jwt jwt) {return staffSchedules.list(identity.actor(jwt));}
  @PostMapping("/staff-work-periods") public Object staffWork(@Valid @RequestBody StaffScheduleService.Period input,@AuthenticationPrincipal Jwt jwt) {return staffSchedules.save(null,input,identity.actor(jwt));}
  @PatchMapping("/staff-work-periods/{id}") public Object staffWork(@PathVariable Long id,@Valid @RequestBody StaffScheduleService.Period input,@AuthenticationPrincipal Jwt jwt) {return staffSchedules.save(id,input,identity.actor(jwt));}
  @DeleteMapping("/staff-work-periods/{id}") public Map<String,String> deleteStaffWork(@PathVariable Long id,@AuthenticationPrincipal Jwt jwt) {staffSchedules.delete(id,identity.actor(jwt));return Map.of("status","ok");}
  @GetMapping("/availability/calendar") public Object calendar(@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(defaultValue="7") int days,@RequestParam(required=false) Long doctorId,@AuthenticationPrincipal Jwt jwt) {return schedules.calendar(from,days,doctorId,identity.actor(jwt));}
  @GetMapping("/availability") public Object slots(@RequestParam Long doctorId,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,@AuthenticationPrincipal Jwt jwt) {return schedules.slots(doctorId,date,identity.actor(jwt));}
  @GetMapping("/notifications") public Object notifications(@AuthenticationPrincipal Jwt jwt) {return notifications.list(identity.actor(jwt));}
  @PatchMapping("/notifications/{id}/read") public Map<String,String> read(@PathVariable Long id,@AuthenticationPrincipal Jwt jwt) {notifications.read(id,identity.actor(jwt));return Map.of("status","ok");}
  @PostMapping("/announcements") public Map<String,String> announce(@Valid @RequestBody NotificationService.Announcement input,@AuthenticationPrincipal Jwt jwt) {notifications.announce(input,identity.actor(jwt));return Map.of("status","ok");}
  @GetMapping("/patient-access") public Object sharing(@AuthenticationPrincipal Jwt jwt) {return sharing.list(identity.actor(jwt));}
  @PostMapping("/patient-access") public Object share(@Valid @RequestBody SharingService.Grant input,@AuthenticationPrincipal Jwt jwt) {return sharing.create(input,identity.actor(jwt));}
  @DeleteMapping("/patient-access/{id}") public Map<String,String> revoke(@PathVariable Long id,@AuthenticationPrincipal Jwt jwt) {sharing.revoke(id,identity.actor(jwt));return Map.of("status","ok");}
  @GetMapping("/reports") public Object reports(@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,@AuthenticationPrincipal Jwt jwt) {return reports.report(from,to,identity.actor(jwt));}
  @GetMapping("/reports.csv") public ResponseEntity<String> csv(@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok().contentType(new MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8)).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=medflow-report.csv").body(reports.csv(reports.report(from,to,identity.actor(jwt))));
  }
  public record DoctorInfo(@NotBlank @Size(max=160) String name,@NotBlank @Size(max=120) String specialty,@Email @Size(max=180) String email) {}
  @PatchMapping("/doctors/{id}") @org.springframework.transaction.annotation.Transactional
  public Object doctor(@PathVariable Long id,@Valid @RequestBody DoctorInfo input,@AuthenticationPrincipal Jwt jwt) {
    var a=identity.actor(jwt);access.admin(a);var d=doctors.findByIdAndHospitalId(id,a.hospitalId()).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Medic inexistent"));
    d.name=input.name().trim();d.specialty=input.specialty().trim();d.email=input.email();return doctors.save(d);
  }
}
