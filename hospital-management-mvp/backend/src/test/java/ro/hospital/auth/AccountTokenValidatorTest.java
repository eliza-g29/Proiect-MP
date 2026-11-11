package ro.hospital.auth;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import ro.hospital.domain.AppUser;
import ro.hospital.repo.AppUserRepository;
class AccountTokenValidatorTest {
 @Test void deactivationAndPasswordVersionInvalidateExistingJwt() {
  var users=mock(AppUserRepository.class);var account=new AppUser();account.id=1L;account.hospitalId=1L;account.role="DOCTOR";account.tokenVersion=3;
  when(users.findById(1L)).thenReturn(Optional.of(account));var validator=new AccountTokenValidator(users);
  var jwt=Jwt.withTokenValue("test").header("alg","HS256").subject("1").claim("hospitalId",1L).claim("role","DOCTOR").claim("tokenVersion",3L).build();
  assertFalse(validator.validate(jwt).hasErrors());account.active=false;assertTrue(validator.validate(jwt).hasErrors());
  account.active=true;account.tokenVersion=4;assertTrue(validator.validate(jwt).hasErrors());
 }
 @Test void oldJwtWithoutVersionIsRejected() {
  var users=mock(AppUserRepository.class);var account=new AppUser();account.id=1L;account.hospitalId=1L;account.role="ADMIN";
  when(users.findById(1L)).thenReturn(Optional.of(account));
  var jwt=Jwt.withTokenValue("test").header("alg","HS256").subject("1").claim("hospitalId",1L).claim("role","ADMIN").build();
  assertTrue(new AccountTokenValidator(users).validate(jwt).hasErrors());
 }
}
