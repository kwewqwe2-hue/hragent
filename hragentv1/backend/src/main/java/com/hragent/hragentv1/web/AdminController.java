package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.AiCallRecord;
import com.hragent.hragentv1.domain.AuditLog;
import com.hragent.hragentv1.domain.KnowledgeArticle;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.AdminDtos;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.service.AdminService;
import com.hragent.hragentv1.service.AuthService;
import com.hragent.hragentv1.service.ImportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AuthService authService;
    private final AdminService adminService;
    private final ImportService importService;

    public AdminController(AuthService authService, AdminService adminService, ImportService importService) {
        this.authService = authService;
        this.adminService = adminService;
        this.importService = importService;
    }

    @GetMapping("/employees")
    public ApiResponse<List<AdminDtos.EmployeeView>> employees(HttpServletRequest request) {
        UserAccount user = requireHr(request);
        return ApiResponse.ok(adminService.employees(user.getTenantId()));
    }

    @PostMapping("/employees")
    public ApiResponse<AdminDtos.EmployeeView> createEmployee(
            HttpServletRequest servletRequest,
            @Valid @RequestBody AdminDtos.EmployeeUpsertRequest request
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.createEmployee(user, request));
    }

    @PutMapping("/employees/{id}")
    public ApiResponse<AdminDtos.EmployeeView> updateEmployee(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @Valid @RequestBody AdminDtos.EmployeeUpsertRequest request
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.updateEmployee(user, id, request));
    }

    @PostMapping("/employees/{id}/reset-password")
    public ApiResponse<AdminDtos.EmployeeView> resetPassword(HttpServletRequest servletRequest, @PathVariable Long id) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.resetEmployeePassword(user, id));
    }

    @GetMapping("/employees/{id}/balances")
    public ApiResponse<List<com.hragent.hragentv1.dto.LeaveDtos.BalanceView>> employeeBalances(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        UserAccount user = requireHr(request);
        return ApiResponse.ok(adminService.employeeBalances(user, id));
    }

    @PutMapping("/employees/{id}/balances")
    public ApiResponse<List<com.hragent.hragentv1.dto.LeaveDtos.BalanceView>> updateEmployeeBalances(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @Valid @RequestBody AdminDtos.EmployeeBalanceUpdateRequest request
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.updateEmployeeBalances(user, id, request));
    }

    @GetMapping("/departments")
    public ApiResponse<List<AdminDtos.DepartmentView>> departments(HttpServletRequest request) {
        UserAccount user = requireHr(request);
        return ApiResponse.ok(adminService.departments(user.getTenantId()));
    }

    @PostMapping("/departments")
    public ApiResponse<AdminDtos.DepartmentView> createDepartment(
            HttpServletRequest servletRequest,
            @Valid @RequestBody AdminDtos.BasicConfigRequest request
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.upsertDepartment(user, null, request));
    }

    @PutMapping("/departments/{id}")
    public ApiResponse<AdminDtos.DepartmentView> updateDepartment(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @Valid @RequestBody AdminDtos.BasicConfigRequest request
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.upsertDepartment(user, id, request));
    }

    @GetMapping("/job-titles")
    public ApiResponse<List<AdminDtos.JobTitleView>> jobTitles(HttpServletRequest request) {
        UserAccount user = requireHr(request);
        return ApiResponse.ok(adminService.jobTitles(user.getTenantId()));
    }

    @PostMapping("/job-titles")
    public ApiResponse<AdminDtos.JobTitleView> createJobTitle(
            HttpServletRequest servletRequest,
            @Valid @RequestBody AdminDtos.BasicConfigRequest request
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.upsertJobTitle(user, null, request));
    }

    @PutMapping("/job-titles/{id}")
    public ApiResponse<AdminDtos.JobTitleView> updateJobTitle(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @Valid @RequestBody AdminDtos.BasicConfigRequest request
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.upsertJobTitle(user, id, request));
    }

    @PostMapping("/import/employees")
    public ApiResponse<AdminDtos.ImportResult> importEmployees(
            HttpServletRequest servletRequest,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "commit", defaultValue = "false") boolean commit
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(importService.importEmployees(user, file, commit));
    }

    @PostMapping("/import/balances")
    public ApiResponse<AdminDtos.ImportResult> importBalances(
            HttpServletRequest servletRequest,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "commit", defaultValue = "false") boolean commit
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(importService.importBalances(user, file, commit));
    }

    @GetMapping("/imports")
    public ApiResponse<List<AdminDtos.ImportBatchView>> imports(HttpServletRequest request) {
        UserAccount user = requireHr(request);
        return ApiResponse.ok(adminService.importBatches(user.getTenantId()));
    }

    @GetMapping("/knowledge")
    public ApiResponse<List<KnowledgeArticle>> knowledge(HttpServletRequest request) {
        UserAccount user = authService.requireUser(request);
        return ApiResponse.ok(adminService.knowledge(user.getTenantId(), user.getRole() == Role.HR));
    }

    @PostMapping("/knowledge")
    public ApiResponse<KnowledgeArticle> createKnowledge(
            HttpServletRequest servletRequest,
            @Valid @RequestBody AdminDtos.KnowledgeUpsertRequest request
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.createKnowledge(user, request));
    }

    @PostMapping(value = "/knowledge/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeArticle> uploadKnowledge(
            HttpServletRequest servletRequest,
            @RequestPart("file") MultipartFile file,
            @RequestParam String category,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Long articleId
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.uploadKnowledge(user, file, category, source, region, articleId));
    }

    @PutMapping("/knowledge/{id}")
    public ApiResponse<KnowledgeArticle> updateKnowledge(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @Valid @RequestBody AdminDtos.KnowledgeUpsertRequest request
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.updateKnowledge(user, id, request));
    }

    @DeleteMapping("/knowledge/{id}")
    public ApiResponse<Void> deleteKnowledge(HttpServletRequest request, @PathVariable Long id) {
        UserAccount user = requireHr(request);
        adminService.deleteKnowledge(user, id);
        return ApiResponse.ok("Deleted", null);
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AuditLog>> auditLogs(HttpServletRequest request) {
        UserAccount user = requireHr(request);
        return ApiResponse.ok(adminService.auditLogs(user.getTenantId()));
    }

    @GetMapping("/ai-calls")
    public ApiResponse<List<AiCallRecord>> aiCalls(HttpServletRequest request) {
        UserAccount user = requireHr(request);
        return ApiResponse.ok(adminService.aiCalls(user.getTenantId()));
    }

    @GetMapping("/ai-config")
    public ApiResponse<AdminDtos.AiConfigView> aiConfig(HttpServletRequest request) {
        UserAccount user = requireHr(request);
        return ApiResponse.ok(adminService.aiConfig(user.getTenantId()));
    }

    @PutMapping("/ai-config")
    public ApiResponse<AdminDtos.AiConfigView> updateAiConfig(
            HttpServletRequest servletRequest,
            @Valid @RequestBody AdminDtos.AiConfigUpdateRequest request
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.updateAiConfig(user, request));
    }

    @PostMapping("/ai-config/test")
    public ApiResponse<AdminDtos.AiConfigTestResult> testAiConfig(HttpServletRequest request) {
        UserAccount user = requireHr(request);
        return ApiResponse.ok(adminService.testAiConfig(user));
    }

    @GetMapping("/api-keys")
    public ApiResponse<List<AdminDtos.ApiKeyView>> apiKeys(HttpServletRequest request) {
        UserAccount user = requireHr(request);
        return ApiResponse.ok(adminService.apiKeys(user.getTenantId()));
    }

    @PostMapping("/api-keys")
    public ApiResponse<AdminDtos.ApiKeyCreateResponse> createApiKey(
            HttpServletRequest servletRequest,
            @Valid @RequestBody AdminDtos.ApiKeyCreateRequest request
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.createApiKey(user, request));
    }

    @PatchMapping("/api-keys/{id}/active")
    public ApiResponse<AdminDtos.ApiKeyView> setApiKeyActive(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @RequestParam boolean active
    ) {
        UserAccount user = requireHr(servletRequest);
        return ApiResponse.ok(adminService.setApiKeyActive(user, id, active));
    }

    @GetMapping("/api-call-logs")
    public ApiResponse<List<AdminDtos.ApiCallLogView>> apiCallLogs(HttpServletRequest request) {
        UserAccount user = requireHr(request);
        return ApiResponse.ok(adminService.apiCallLogs(user.getTenantId()));
    }

    private UserAccount requireHr(HttpServletRequest request) {
        UserAccount user = authService.requireUser(request);
        authService.requireRole(user, Role.HR);
        return user;
    }
}
