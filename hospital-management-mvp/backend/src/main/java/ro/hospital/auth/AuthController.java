package ro.hospital.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import ro.hospital.domain.AppUser;
import ro.hospital.repo.AppUserRepository;

@RestController @RequestMapping("/api/auth")
public class AuthController {
  private final AppUserRepository users;
  private final PasswordEncoder encoder;
  private final JwtEncoder jwtEncoder;
  public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtEncoder jwtEncoder) {
    this.users=users; this.encoder=encoder; this.jwtEncoder=jwtEncoder;
  }
  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
  public record Profile(Long id, String name, String email, String role, Long hospitalId, Long doctorId, Long patientId, boolean mustChangePassword) {}
  public record LoginResponse(String token, Profile user) {}

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest input) {
    AppUser user = users.findByEmailIgnoreCase(input.email()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Date de autentificare incorecte"));
    if (!user.active || !encoder.matches(input.password(), user.passwordHash)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Date de autentificare incorecte");
    }
    return issue(user);
  }
  private LoginResponse issue(AppUser user) {
    Instant now = Instant.now();
    JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer("hospital-mvp").issuedAt(now)
      .expiresAt(now.plus(4, ChronoUnit.HOURS)).subject(String.valueOf(user.id))
      .claim("name", user.name).claim("email", user.email)
      .claim("role", user.role).claim("hospitalId", user.hospitalId).claim("tokenVersion",user.tokenVersion);
    if(user.doctorId != null) claims.claim("doctorId", user.doctorId);
    if(user.patientId != null) claims.claim("patientId", user.patientId);
    String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    return new LoginResponse(token, profile(user));
  }

  @GetMapping("/me")
  public Profile me(@AuthenticationPrincipal Jwt token) {
    Long id = Long.valueOf(token.getSubject());
    AppUser user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cont inexistent"));
    return profile(user);
  }

  private AppUser current(Jwt jwt) {
    return users.findLockedById(Long.valueOf(jwt.getSubject())).filter(u->u.active)
      .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Cont indisponibil"));
  }
  public record PersonalInfo(@NotBlank @Size(max=160) String name,@Email @NotBlank @Size(max=180) String email) {}
  public record PasswordChange(@NotBlank String currentPassword,@NotBlank @Size(min=12,max=72) String newPassword) {}
  @PatchMapping("/profile") @Transactional
  public Profile updateProfile(@Valid @RequestBody PersonalInfo input,@AuthenticationPrincipal Jwt jwt) {
    AppUser u=current(jwt);u.name=input.name().trim();u.email=input.email().trim().toLowerCase(java.util.Locale.ROOT);
    return profile(users.save(u));
  }
  @PostMapping("/password") @Transactional
  public LoginResponse changePassword(@Valid @RequestBody PasswordChange input,@AuthenticationPrincipal Jwt jwt) {
    AppUser u=current(jwt);
    if(!encoder.matches(input.currentPassword(),u.passwordHash)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Parola curentă este incorectă");
    if(input.newPassword().getBytes(StandardCharsets.UTF_8).length>72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Parola depășește 72 de octeți");
    u.passwordHash=encoder.encode(input.newPassword());u.mustChangePassword=false;u.tokenVersion++;users.save(u);return issue(u);
  }
  @PostMapping("/logout") @Transactional
  public java.util.Map<String,String> logout(@AuthenticationPrincipal Jwt jwt) {
    AppUser u=current(jwt);u.tokenVersion++;users.save(u);return java.util.Map.of("status","ok");
  }

  private Profile profile(AppUser u) { return new Profile(u.id,u.name,u.email,u.role,u.hospitalId,u.doctorId,u.patientId,u.mustChangePassword); }
}
