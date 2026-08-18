package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.DemoPolicyDtos;
import com.hragent.hragentv1.service.AuthService;
import com.hragent.hragentv1.service.PolicyMonitorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PolicyMonitorController {
    private final PolicyMonitorService policyMonitorService;
    private final AuthService authService;

    public PolicyMonitorController(PolicyMonitorService policyMonitorService, AuthService authService) {
        this.policyMonitorService = policyMonitorService;
        this.authService = authService;
    }

    @PostMapping("/internal/agent/v1/policy-monitor/check")
    public ApiResponse<DemoPolicyDtos.MonitorCheckResult> check(
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody DemoPolicyDtos.CandidateInput input
    ) {
        return ApiResponse.ok(policyMonitorService.check(apiKey, input));
    }

    @GetMapping("/admin/policy-monitor/candidates")
    public ApiResponse<List<DemoPolicyDtos.CandidateView>> list(HttpServletRequest request) {
        UserAccount actor = authService.requireUser(request);
        authService.requireRole(actor, Role.HR);
        return ApiResponse.ok(policyMonitorService.list(actor.getTenantId()));
    }

    @PostMapping("/admin/policy-monitor/candidates/{id}/review")
    public ApiResponse<DemoPolicyDtos.CandidateView> review(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody DemoPolicyDtos.ReviewRequest reviewRequest
    ) {
        UserAccount actor = authService.requireUser(request);
        authService.requireRole(actor, Role.HR);
        return ApiResponse.ok(policyMonitorService.review(actor, id, reviewRequest));
    }
}
