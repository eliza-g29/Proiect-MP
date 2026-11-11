package ro.hospital.auth;

import java.util.Objects;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import ro.hospital.repo.AppUserRepository;

public class AccountTokenValidator implements OAuth2TokenValidator<Jwt> {
  private final AppUserRepository users;
  public AccountTokenValidator(AppUserRepository users) { this.users=users; }
  public OAuth2TokenValidatorResult validate(Jwt token) {
    try {
      var user=users.findById(Long.valueOf(token.getSubject())).orElse(null);
      Number version=token.getClaim("tokenVersion");
      Number hospital=token.getClaim("hospitalId");
      if(user!=null && user.active && version!=null && hospital!=null
          && user.tokenVersion==version.longValue() && user.hospitalId.longValue()==hospital.longValue()
          && Objects.equals(user.role,token.getClaimAsString("role"))) return OAuth2TokenValidatorResult.success();
    } catch(IllegalArgumentException | ClassCastException ex) { /* Invalid subject is an invalid credential. */ }
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Sesiune revocată sau cont dezactivat",null));
  }
}
