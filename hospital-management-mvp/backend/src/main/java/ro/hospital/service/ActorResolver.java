package ro.hospital.service;

import java.util.Set;
import ro.hospital.repo.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ActorResolver {
  private final AppUserRepository users;
  public ActorResolver(AppUserRepository users) { this.users=users; }
  private Long number(Object value) { return value==null?null:((Number)value).longValue(); }
  public AccessPolicy.Actor actor(Jwt jwt) {
    var a=new AccessPolicy.Actor(Long.valueOf(jwt.getSubject()),number(jwt.getClaim("hospitalId")),
      jwt.getClaimAsString("role"),number(jwt.getClaim("doctorId")),number(jwt.getClaim("patientId")));
    if(a.hospitalId()==null || a.role()==null || !Set.of("ADMIN","DOCTOR","STAFF","PATIENT").contains(a.role())
        || (a.role().equals("DOCTOR") && a.doctorId()==null)
        || (a.role().equals("PATIENT") && a.patientId()==null))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Cont fără profil valid pentru rolul atribuit");
    var account=users.findById(a.id()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Cont inexistent"));
    if(account.mustChangePassword) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Schimbă parola temporară din pagina Contul meu");
    return a;
  }
}
