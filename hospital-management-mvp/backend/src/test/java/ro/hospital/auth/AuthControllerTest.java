package ro.hospital.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import ro.hospital.config.SecurityConfig;
import ro.hospital.domain.AppUser;
import ro.hospital.repo.AppUserRepository;

@WebMvcTest(value = AuthController.class, properties = "app.cors-origin=http://localhost:3000")
@Import(SecurityConfig.class)
class AuthControllerTest {
  @Autowired MockMvc mvc;
  @Autowired PasswordEncoder passwords;
  @Autowired ObjectMapper json;
  @MockitoBean AppUserRepository users;
  private AppUser user;

  @BeforeEach
  void setup() {
    user = new AppUser();
    user.id = 1L;
    user.hospitalId = 1L;
    user.name = "Demo";
    user.email = "admin@demo.ro";
    user.role = "ADMIN";
    user.passwordHash = passwords.encode("Demo1234!");
    when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));
    when(users.findById(1L)).thenReturn(Optional.of(user));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMIN", "DOCTOR", "STAFF", "PATIENT"})
  void browserLoginIssuesTokenThatCanAccessProfile(String role) throws Exception {
    user.role = role;
    user.doctorId = role.equals("DOCTOR") ? 11L : null;
    user.patientId = role.equals("PATIENT") ? 21L : null;
    String response = mvc.perform(post("/api/auth/login")
        .header("Host", "localhost:3000")
        .header("Origin", "http://localhost:3000")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"admin@demo.ro\",\"password\":\"Demo1234!\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.user.role").value(role))
      .andExpect(jsonPath("$.token").isNotEmpty())
      .andReturn().getResponse().getContentAsString();
    String token = json.readTree(response).get("token").asText();
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(user.email));
  }

  @Test
  void loginAllowsConfiguredOriginEvenWhenProxyDropsHostPort() throws Exception {
    mvc.perform(post("/api/auth/login")
        .header("Host", "localhost")
        .header("Origin", "http://localhost:3000")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"admin@demo.ro\",\"password\":\"Demo1234!\"}"))
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
  }

  @Test
  void incorrectPasswordReturns401() throws Exception {
    mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:3000")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"admin@demo.ro\",\"password\":\"wrong\"}"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void allowedOriginCanPreflight() throws Exception {
    mvc.perform(options("/api/auth/login")
        .header("Origin", "http://localhost:3000")
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "content-type"))
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
  }

  @Test
  void untrustedOriginIsRejected() throws Exception {
    mvc.perform(post("/api/auth/login").header("Origin", "https://untrusted.example")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"admin@demo.ro\",\"password\":\"Demo1234!\"}"))
      .andExpect(status().isForbidden());
  }

  @Test
  void profileRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }
}
