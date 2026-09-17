package com.hragent.hragentv1.web;
import com.hragent.hragentv1.service.*;
import com.hragent.hragentv1.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
@RestController
@RequestMapping("/leave")
public class LeaveMedicalController {
 private final AuthService auth;private final LeaveMedicalService medical;
 public LeaveMedicalController(AuthService auth,LeaveMedicalService medical){this.auth=auth;this.medical=medical;}
 @PostMapping("/medical-records") public ApiResponse<LeaveMedicalService.Scan> upload(HttpServletRequest request,@RequestParam MultipartFile file,@RequestParam LocalDate startDate,@RequestParam LocalDate endDate){return ApiResponse.ok(medical.upload(auth.requireUser(request),file,startDate,endDate));}
 @GetMapping("/{id}/medical-record") public ResponseEntity<byte[]> download(HttpServletRequest request,@PathVariable Long id){var d=medical.download(auth.requireUser(request),id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.fileName(),StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType(d.mediaType())).body(d.bytes());}
}
