package ro.hospital.records;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ro.hospital.service.ActorResolver;

@RestController
@RequestMapping("/api")
public class RecordsController {
  private final RecordService records;
  private final ActorResolver identity;
  public RecordsController(RecordService records,ActorResolver identity) { this.records=records;this.identity=identity; }
  @GetMapping("/staff")
  public List<Map<String,Object>> staff(@AuthenticationPrincipal Jwt jwt) {
    return records.staffDirectory(identity.actor(jwt));
  }
  @GetMapping("/records/{kind}")
  public List<Map<String,Object>> list(@PathVariable String kind,@RequestParam(required=false) Long patientId,@AuthenticationPrincipal Jwt jwt) {
    return records.list(kind,patientId,identity.actor(jwt));
  }
  @PostMapping("/records/{kind}")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> create(@PathVariable String kind,@RequestBody Map<String,Object> input,@AuthenticationPrincipal Jwt jwt) {
    return records.create(kind,input,identity.actor(jwt));
  }
  @PatchMapping("/records/{kind}/{id}")
  public Map<String,Object> update(@PathVariable String kind,@PathVariable Long id,@RequestBody Map<String,Object> input,@AuthenticationPrincipal Jwt jwt) {
    return records.update(kind,id,input,identity.actor(jwt));
  }
  @PostMapping(value="/medical-documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> upload(@RequestParam Long patientId,@RequestParam String documentType,
      @RequestParam MultipartFile file,@RequestParam(defaultValue="true") boolean patientVisible,@AuthenticationPrincipal Jwt jwt) throws IOException {
    return records.upload(patientId,documentType,file,patientVisible,identity.actor(jwt));
  }
  @GetMapping("/medical-documents/{id}/download")
  public ResponseEntity<byte[]> download(@PathVariable Long id,@AuthenticationPrincipal Jwt jwt) throws IOException {
    var doc=records.download(id,identity.actor(jwt));
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
      .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(doc.name(),StandardCharsets.UTF_8).build().toString())
      .cacheControl(CacheControl.noStore()).body(doc.content());
  }
}
