package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.CertificateLanguage;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.EmploymentCertificateTemplateDtos;
import com.hragent.hragentv1.service.AuthService;
import com.hragent.hragentv1.service.EmploymentCertificateTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/employment-certificate-templates")
public class EmploymentCertificateTemplateController {
    private final AuthService authService;
    private final EmploymentCertificateTemplateService templateService;

    public EmploymentCertificateTemplateController(
            AuthService authService,
            EmploymentCertificateTemplateService templateService
    ) {
        this.authService = authService;
        this.templateService = templateService;
    }

    @GetMapping
    public ApiResponse<List<EmploymentCertificateTemplateDtos.TemplateView>> list(HttpServletRequest request) {
        UserAccount actor = authService.requireUser(request);
        return ApiResponse.ok(templateService.list(actor));
    }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EmploymentCertificateTemplateDtos.TemplatePreview> preview(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file
    ) {
        UserAccount actor = authService.requireUser(request);
        return ApiResponse.ok(templateService.preview(actor, file));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EmploymentCertificateTemplateDtos.TemplateView> upload(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestParam String name,
            @RequestParam String destinationCountry,
            @RequestParam String consulateName,
            @RequestParam CertificateLanguage language
    ) {
        UserAccount actor = requireHr(request);
        return ApiResponse.ok(
                "模板已上传",
                templateService.upload(actor, file, name, destinationCountry, consulateName, language)
        );
    }

    @PutMapping("/{id}/active")
    public ApiResponse<EmploymentCertificateTemplateDtos.TemplateView> setActive(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody EmploymentCertificateTemplateDtos.ActiveRequest input
    ) {
        UserAccount actor = requireHr(request);
        return ApiResponse.ok(templateService.setActive(actor, id, input.active()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(HttpServletRequest request, @PathVariable Long id) {
        UserAccount actor = authService.requireUser(request);
        EmploymentCertificateTemplateService.TemplateDownload template = templateService.download(actor, id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(template.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(template.contentType()))
                .contentLength(template.content().length)
                .body(template.content());
    }

    private UserAccount requireHr(HttpServletRequest request) {
        UserAccount actor = authService.requireUser(request);
        authService.requireRole(actor, Role.HR);
        return actor;
    }
}
