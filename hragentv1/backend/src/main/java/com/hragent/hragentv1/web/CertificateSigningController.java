package com.hragent.hragentv1.web;
import com.hragent.hragentv1.service.*;
import com.hragent.hragentv1.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/employment-certificates")
public class CertificateSigningController {
 private final AuthService auth;private final CertificateSigningService signing;
 public CertificateSigningController(AuthService auth,CertificateSigningService signing){this.auth=auth;this.signing=signing;}
 @GetMapping("/esign/config") public ApiResponse<?> config(HttpServletRequest r){return ApiResponse.ok(signing.config(auth.requireUser(r)));}
 @PutMapping("/esign/config") public ApiResponse<?> configure(HttpServletRequest r,@RequestBody CertificateSigningService.ConfigInput input){return ApiResponse.ok(signing.configure(auth.requireUser(r),input));}
 @GetMapping("/{id}/esign") public ApiResponse<?> status(HttpServletRequest r,@PathVariable Long id){return ApiResponse.ok(signing.status(auth.requireUser(r),id));}
 @PostMapping("/{id}/esign") public ApiResponse<?> start(HttpServletRequest r,@PathVariable Long id){return ApiResponse.ok(signing.start(auth.requireUser(r),id));}
 public record Reconcile(String flowId){}
 @PutMapping("/{id}/esign/reconcile") public ApiResponse<?> reconcile(HttpServletRequest r,@PathVariable Long id,@RequestBody Reconcile input){return ApiResponse.ok(signing.reconcile(auth.requireUser(r),id,input.flowId()));}
 @GetMapping("/{id}/signed-document") public ResponseEntity<byte[]> download(HttpServletRequest r,@PathVariable Long id){return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Cache-Control","no-store").header("Content-Disposition","attachment; filename=certificate-"+id+"-signed.pdf").body(signing.download(auth.requireUser(r),id));}
}
