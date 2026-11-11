package ro.hospital.web;
import java.util.Map;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<Map<String,String>> concurrentChange(OptimisticLockingFailureException ex) {
    return ResponseEntity.status(409).body(Map.of("error","Înregistrarea a fost modificată între timp. Reîncarcă datele și încearcă din nou."));
  }
  @ExceptionHandler(IOException.class)
  ResponseEntity<Map<String,String>> storage(IOException ex) {
    return ResponseEntity.status(503).body(Map.of("error","Stocarea documentelor este temporar indisponibilă"));
  }
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Map<String,String>> uploadLimit(MaxUploadSizeExceededException ex) {
    return ResponseEntity.status(413).body(Map.of("error","Fișierul depășește limita de 10 MB"));
  }
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String,String>> malformed(HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest().body(Map.of("error","Cererea conține date invalide"));
  }
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Map<String,String>> conflict(DataIntegrityViolationException ex) {
    return ResponseEntity.status(409).body(Map.of("error",
      "Operația intră în conflict cu datele existente. Verifică intervalul programării și legăturile dintre înregistrări."));
  }
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String,String>> status(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()==null ? "Eroare" : ex.getReason()));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String,String>> validation(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream().map(f-> f.getField()+": "+f.getDefaultMessage()).findFirst().orElse("Date invalide");
    return ResponseEntity.badRequest().body(Map.of("error",message));
  }
}
