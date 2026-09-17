package com.hragent.hragentv1.web;
import com.hragent.hragentv1.service.*;
import com.hragent.hragentv1.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;

@RestController @RequestMapping("/lifecycle")
public class LifecycleController {
 private final AuthService auth; private final LifecycleService service;
 public LifecycleController(AuthService auth,LifecycleService service){this.auth=auth;this.service=service;}
 public record Review(@NotBlank String action,@NotBlank @Size(max=2000) String opinion){}
 @PostMapping("/{id}/medical-record") public ApiResponse<?> medical(HttpServletRequest r,@PathVariable Long id,@RequestPart("file") MultipartFile file){return ApiResponse.ok(service.medicalUpload(auth.requireUser(r),id,file));}
 @PostMapping("/{id}/certificate-template") public ApiResponse<?> certificate(HttpServletRequest r,@PathVariable Long id,@RequestPart("file") MultipartFile file){return ApiResponse.ok(service.certificateUpload(auth.requireUser(r),id,file));}
 @GetMapping("/{id}/certificate-template") public ApiResponse<?> certificateState(HttpServletRequest r,@PathVariable Long id){return ApiResponse.ok(service.certificateTemplateState(auth.requireUser(r),id));}
 @PutMapping("/{id}/certificate-template") public ApiResponse<?> certificateFields(HttpServletRequest r,@PathVariable Long id,@RequestBody java.util.Map<String,String> fields){return ApiResponse.ok(service.confirmCertificateTemplate(auth.requireUser(r),id,fields));}
 public record Supplement(@NotBlank @Size(max=1000) String text){}
 public record Pay(@NotNull Long employeeId,@NotBlank String month,@NotBlank @Size(max=4000) String details){}
 @GetMapping("/mine") public ApiResponse<?> mine(HttpServletRequest r){return ApiResponse.ok(service.mine(auth.requireLifecycleUser(r)));}
 @GetMapping("/hr/queue") public ApiResponse<?> queue(HttpServletRequest r){return ApiResponse.ok(service.queue(auth.requireUser(r)));}
 @PostMapping("/{id}/supplement") public ApiResponse<?> supplement(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody Supplement input){return ApiResponse.ok(service.supplement(auth.requireLifecycleUser(r),id,input.text()));}
 @PostMapping("/hr/{id}/review") public ApiResponse<?> review(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody Review input){return ApiResponse.ok(service.review(auth.requireUser(r),id,input.action(),input.opinion()));}
 @PostMapping("/hr/{id}/file") public ApiResponse<?> upload(HttpServletRequest r,@PathVariable Long id,@RequestPart("file") MultipartFile file)throws java.io.IOException{service.resultFile(auth.requireUser(r),id,file.getOriginalFilename(),file.getBytes());return ApiResponse.ok("已上传，办结后员工可下载");}
 @GetMapping("/{id}/file") public ResponseEntity<byte[]> download(HttpServletRequest r,@PathVariable Long id){return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Content-Disposition","attachment; filename=service-"+id+".pdf").header("Cache-Control","no-store").body(service.download(auth.requireLifecycleUser(r),id));}
 @PostMapping("/hr/payslips") public ApiResponse<?> pay(HttpServletRequest r,@Valid @RequestBody Pay input){service.publishPay(auth.requireUser(r),input.employeeId(),input.month(),input.details());return ApiResponse.ok("工资明细已发布，仅该员工及 HR 可查询");}
 @PostMapping("/{id}/materials") public ApiResponse<?> materials(HttpServletRequest r,@PathVariable Long id,@RequestPart("file") MultipartFile file)throws java.io.IOException{service.materials(auth.requireLifecycleUser(r),id,file.getBytes());return ApiResponse.ok("材料已加密保存，供 HRSSC 核验");}
 @GetMapping("/hr/{id}/materials") public ResponseEntity<byte[]> hrMaterials(HttpServletRequest r,@PathVariable Long id){return materialResponse(service.materialsDownload(auth.requireUser(r),id,true));}
 @GetMapping("/{id}/materials") public ResponseEntity<byte[]> ownMaterials(HttpServletRequest r,@PathVariable Long id){return materialResponse(service.materialsDownload(auth.requireLifecycleUser(r),id,false));}
 private ResponseEntity<byte[]> materialResponse(byte[] data){return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Content-Disposition","attachment; filename=materials.pdf").header("Cache-Control","no-store").body(data);}
}
