package ro.hospital.operations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.domain.AppUser;
import ro.hospital.repo.*;
import ro.hospital.service.AccessPolicy;

class AccountServiceTest {
 AppUserRepository users=mock(AppUserRepository.class);DoctorRepository doctors=mock(DoctorRepository.class);PatientRepository patients=mock(PatientRepository.class);
 PasswordEncoder passwords=mock(PasswordEncoder.class);NamedParameterJdbcTemplate db=mock(NamedParameterJdbcTemplate.class);AccessPolicy access=mock(AccessPolicy.class);
 AccountService service=new AccountService(users,doctors,patients,passwords,db,access);
 AccessPolicy.Actor admin=new AccessPolicy.Actor(1L,1L,"ADMIN",null,null);
 AppUser target;
 @BeforeEach void setup() {
  AppUser owner=new AppUser();owner.id=1L;owner.hospitalId=1L;owner.role="ADMIN";
  target=new AppUser();target.id=2L;target.hospitalId=1L;target.role="STAFF";target.tokenVersion=5;
  when(db.queryForObject(anyString(),anyMap(),eq(Boolean.class))).thenReturn(true);
  when(users.findById(1L)).thenReturn(Optional.of(owner));when(users.findLockedById(1L)).thenReturn(Optional.of(owner));
  when(users.findLockedById(2L)).thenReturn(Optional.of(target));when(passwords.encode(anyString())).thenReturn("hashed-password");
 }
 @Test void recoveryRevokesOldTokensAndRequiresPasswordChange() {
  service.reset(2L,new AccountService.ResetInput("Temporary-Password-123!"),admin);
  assertEquals(6,target.tokenVersion);assertTrue(target.mustChangePassword);assertEquals("hashed-password",target.passwordHash);
 }
 @Test void deactivationRevokesOldTokens() {
  service.update(2L,new AccountService.AccountUpdate("Demo","staff@demo.ro","STAFF",null,false,null),admin);
  assertFalse(target.active);assertEquals(6,target.tokenVersion);
 }
 @Test void administratorCannotDisableOwnAccount() {
  assertThrows(ResponseStatusException.class,()->service.update(1L,new AccountService.AccountUpdate("Admin","admin@demo.ro","ADMIN",null,false,null),admin));
  verify(users,never()).saveAndFlush(any());
 }
 @Test void profilesFromAnotherHospitalAreRejected() {
  var input=new AccountService.AccountInput("Demo","new@demo.ro","DOCTOR",99L,"Temporary-Password-123!",null,null);
  assertThrows(ResponseStatusException.class,()->service.create(input,admin));verify(users,never()).saveAndFlush(any());
 }
 @Test void disabledAdministratorCannotRaceAnotherAdministrativeChange() {
  AppUser disabled=new AppUser();disabled.id=1L;disabled.hospitalId=1L;disabled.role="ADMIN";disabled.active=false;
  when(db.queryForObject(anyString(),anyMap(),eq(Boolean.class))).thenReturn(false);when(access.denied()).thenReturn(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
  assertThrows(ResponseStatusException.class,()->service.reset(2L,new AccountService.ResetInput("Temporary-Password-123!"),admin));
  verify(users,never()).save(any());
 }
}
