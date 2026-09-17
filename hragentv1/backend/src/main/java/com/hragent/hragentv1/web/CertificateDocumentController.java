package com.hragent.hragentv1.web;
import com.hragent.hragentv1.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/employment-certificates")
public class CertificateDocumentController {
 private final AuthService auth;private final EmploymentCertificateService certificates;private final CertificateSigningService signing;private final CertificatePdfService pdf;
 public CertificateDocumentController(AuthService auth,EmploymentCertificateService certificates,CertificateSigningService signing,CertificatePdfService pdf){this.auth=auth;this.certificates=certificates;this.signing=signing;this.pdf=pdf;}
 @GetMapping("/{id}/pdf") public ResponseEntity<byte[]> document(HttpServletRequest request,@PathVariable Long id,@RequestParam(defaultValue="false") boolean inline){
  var actor=auth.requireUser(request);var original=certificates.download(actor,id);
  boolean signed=Boolean.TRUE.equals(((Map<?,?>)signing.status(actor,id)).get("signed"));
  byte[] bytes=signed?signing.download(actor,id):pdf.preview(original.content());CertificatePdfService.validate(bytes);
  String name="在职证明-"+id+(signed?"-已签章.pdf":"-未签章预览.pdf");
  return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(bytes.length).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").header("X-Certificate-Signed",Boolean.toString(signed)).header(HttpHeaders.CONTENT_DISPOSITION,(inline?ContentDisposition.inline():ContentDisposition.attachment()).filename(name,StandardCharsets.UTF_8).build().toString()).body(bytes);
 }
}
