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
    private final com.hragent.hragentv1.service.OfficialPolicyCrawler crawler;

    public PolicyMonitorController(PolicyMonitorService policyMonitorService, AuthService authService, com.hragent.hragentv1.service.OfficialPolicyCrawler crawler) {
        this.policyMonitorService = policyMonitorService;
        this.authService = authService;
        this.crawler = crawler;
    }

    @GetMapping("/policy-monitor/progress")
    public ApiResponse<java.util.Map<String,Object>> progress(HttpServletRequest request) {
        var actor=authService.requireLifecycleUser(request);
        var result=new java.util.LinkedHashMap<String,Object>(crawler.status());
        // Aggregate only this workspace's candidates; never expose another tenant's review data.
        var rows=policyMonitorService.list(actor.getTenantId()).stream().filter(c->c.sourceId().startsWith("official-")).toList();
        result.put("pendingCount",rows.stream().filter(c->c.reviewStatus()==com.hragent.hragentv1.domain.PolicyReviewStatus.PENDING_REVIEW).count());
        result.put("approvedCount",rows.stream().filter(c->c.reviewStatus()==com.hragent.hragentv1.domain.PolicyReviewStatus.APPROVED).count());
        return ApiResponse.ok(result);
    }
    @GetMapping("/admin/policy-monitor/sources")
    public ApiResponse<java.util.Map<String,Object>> sources(HttpServletRequest request) {
        authService.requireRole(authService.requireUser(request), Role.HR);
        return ApiResponse.ok(crawler.status());
    }
    @PostMapping("/admin/policy-monitor/scan")
    public ApiResponse<java.util.Map<String,Object>> scan(HttpServletRequest request) {
        authService.requireRole(authService.requireUser(request), Role.HR);
        boolean started=crawler.trigger();
        return ApiResponse.ok(java.util.Map.of("started",started,"message",started?"已开始检查官方政策源":"已有检查正在运行"));
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
