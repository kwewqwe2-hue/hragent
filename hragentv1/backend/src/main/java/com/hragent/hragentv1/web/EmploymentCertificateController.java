package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.CertificateLanguage;
import com.hragent.hragentv1.domain.EmploymentCertificateType;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.EmploymentCertificateDtos;
import com.hragent.hragentv1.service.AuthService;
import com.hragent.hragentv1.service.EmploymentCertificateService;
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
@RequestMapping("/employment-certificates")
public class EmploymentCertificateController {
    private final AuthService authService;
    private final EmploymentCertificateService certificateService;

    public EmploymentCertificateController(
            AuthService authService,
            EmploymentCertificateService certificateService
    ) {
        this.authService = authService;
        this.certificateService = certificateService;
    }

    @GetMapping("/options")
    public ApiResponse<EmploymentCertificateDtos.FormOptions> options() {
        return ApiResponse.ok(certificateService.options());
    }

    @PostMapping
    public ApiResponse<EmploymentCertificateDtos.RequestView> create(
            HttpServletRequest servletRequest,
            @Valid @RequestBody EmploymentCertificateDtos.CreateRequest request
    ) {
        UserAccount actor = authService.requireUser(servletRequest);
        return ApiResponse.ok("申请已提交，等待 HR 审核", certificateService.create(actor, request));
    }

    @PostMapping(path = "/with-template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EmploymentCertificateDtos.RequestView> createWithTemplate(
            HttpServletRequest servletRequest,
            @RequestPart("file") MultipartFile file,
            @RequestParam String templateName,
            @RequestParam CertificateLanguage language,
            @RequestParam String purpose,
            @RequestParam String destinationCountry,
            @RequestParam String consulateName,
            @RequestParam(defaultValue = "false") boolean includeSalary,
            @RequestParam(required = false) String remarks
    ) {
        UserAccount actor = authService.requireUser(servletRequest);
        EmploymentCertificateDtos.CreateRequest input = new EmploymentCertificateDtos.CreateRequest(
                EmploymentCertificateType.VISA,
                language,
                purpose,
                destinationCountry,
                consulateName,
                includeSalary,
                remarks
        );
        return ApiResponse.ok(
                "申请和模板已提交，等待 HR 一次审核",
                certificateService.createWithTemplate(actor, input, file, templateName)
        );
    }

    @GetMapping("/my")
    public ApiResponse<List<EmploymentCertificateDtos.RequestView>> mine(HttpServletRequest request) {
        return ApiResponse.ok(certificateService.mine(authService.requireUser(request)));
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<EmploymentCertificateDtos.RequestView> cancel(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(certificateService.cancel(authService.requireUser(request), id));
    }

    @GetMapping("/hr/pending")
    public ApiResponse<List<EmploymentCertificateDtos.RequestView>> hrPending(HttpServletRequest request) {
        UserAccount actor = authService.requireUser(request);
        authService.requireRole(actor, Role.HR);
        return ApiResponse.ok(certificateService.hrPending(actor));
    }

    @GetMapping("/hr/all")
    public ApiResponse<List<EmploymentCertificateDtos.RequestView>> hrAll(HttpServletRequest request) {
        UserAccount actor = authService.requireUser(request);
        authService.requireRole(actor, Role.HR);
        return ApiResponse.ok(certificateService.hrAll(actor));
    }

    @PutMapping("/hr/{id}/review")
    public ApiResponse<EmploymentCertificateDtos.RequestView> review(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody EmploymentCertificateDtos.ReviewRequest input
    ) {
        UserAccount actor = authService.requireUser(request);
        authService.requireRole(actor, Role.HR);
        return ApiResponse.ok(certificateService.review(actor, id, input));
    }

    @PostMapping("/hr/{id}/generate")
    public ApiResponse<EmploymentCertificateDtos.RequestView> retryGeneration(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        UserAccount actor = authService.requireUser(request);
        authService.requireRole(actor, Role.HR);
        return ApiResponse.ok(certificateService.retryGeneration(actor, id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        UserAccount actor = authService.requireUser(request);
        EmploymentCertificateService.DocumentDownload document = certificateService.download(actor, id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                ))
                .contentLength(document.content().length)
                .body(document.content());
    }
}
