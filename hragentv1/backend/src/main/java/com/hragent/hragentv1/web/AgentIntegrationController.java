package com.hragent.hragentv1.web;

import com.hragent.hragentv1.dto.AgentIntegrationDtos;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.EmploymentCertificateDtos;
import com.hragent.hragentv1.dto.OnboardingDtos;
import com.hragent.hragentv1.service.AgentIntegrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/agent/v1")
public class AgentIntegrationController {
    private final AgentIntegrationService agentIntegrationService;

    public AgentIntegrationController(AgentIntegrationService agentIntegrationService) {
        this.agentIntegrationService = agentIntegrationService;
    }

    @PostMapping("/identity/bind")
    public ApiResponse<AgentIntegrationDtos.BindingStatus> bind(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId,
            @RequestHeader(value = "X-DingTalk-Staff-Id", required = false) String dingtalkStaffId,
            @Valid @RequestBody AgentIntegrationDtos.BindRequest request
    ) {
        return ApiResponse.ok(agentIntegrationService.bind(apiKey, dingtalkUserId, dingtalkStaffId, request));
    }

    @GetMapping("/identity/status")
    public ApiResponse<AgentIntegrationDtos.BindingStatus> bindingStatus(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId
    ) {
        return ApiResponse.ok(agentIntegrationService.bindingStatus(apiKey, dingtalkUserId));
    }

    @GetMapping("/me")
    public ApiResponse<AgentIntegrationDtos.EmployeeContext> me(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId
    ) {
        return ApiResponse.ok(agentIntegrationService.context(apiKey, dingtalkUserId));
    }

    @GetMapping("/balances")
    public ApiResponse<AgentIntegrationDtos.BalanceResponse> balances(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId
    ) {
        return ApiResponse.ok(agentIntegrationService.balances(apiKey, dingtalkUserId));
    }

    @GetMapping("/personal-profile")
    public ApiResponse<AgentIntegrationDtos.PersonalProfile> personalProfile(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId
    ) {
        return ApiResponse.ok(agentIntegrationService.personalProfile(apiKey, dingtalkUserId));
    }

    @PostMapping("/certificates/requests")
    public ApiResponse<EmploymentCertificateDtos.RequestView> createCertificateRequest(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId,
            @Valid @RequestBody EmploymentCertificateDtos.CreateRequest input
    ) {
        return ApiResponse.ok(agentIntegrationService.createCertificateRequest(apiKey, dingtalkUserId, input));
    }

    @GetMapping("/certificates/requests")
    public ApiResponse<List<EmploymentCertificateDtos.RequestView>> myCertificateRequests(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId
    ) {
        return ApiResponse.ok(agentIntegrationService.myCertificateRequests(apiKey, dingtalkUserId));
    }

    @PostMapping("/leave/preview")
    public ApiResponse<AgentIntegrationDtos.LeavePreview> preview(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId,
            @Valid @RequestBody AgentIntegrationDtos.LeaveInput input
    ) {
        return ApiResponse.ok(agentIntegrationService.preview(apiKey, dingtalkUserId, input));
    }

    @PostMapping("/leave/requests")
    public ApiResponse<AgentIntegrationDtos.LeaveApplication> create(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId,
            @Valid @RequestBody AgentIntegrationDtos.LeaveInput input
    ) {
        return ApiResponse.ok(agentIntegrationService.create(apiKey, dingtalkUserId, input));
    }

    @PostMapping("/leave/requests/{id}/review")
    public ApiResponse<AgentIntegrationDtos.LeaveApplication> review(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId,
            @PathVariable Long id,
            @Valid @RequestBody AgentIntegrationDtos.ReviewInput input
    ) {
        return ApiResponse.ok(agentIntegrationService.review(apiKey, dingtalkUserId, id, input));
    }

    @PostMapping("/leave/card-action")
    public ApiResponse<AgentIntegrationDtos.LeaveApplication> cardAction(
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody AgentIntegrationDtos.CardActionRequest input
    ) {
        return ApiResponse.ok(agentIntegrationService.cardAction(apiKey, input));
    }

    @GetMapping("/leave/requests")
    public ApiResponse<List<AgentIntegrationDtos.LeaveSummary>> myRequests(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId
    ) {
        return ApiResponse.ok(agentIntegrationService.myRequests(apiKey, dingtalkUserId));
    }

    @GetMapping("/leave/pending")
    public ApiResponse<List<AgentIntegrationDtos.LeaveSummary>> pendingApprovals(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId
    ) {
        return ApiResponse.ok(agentIntegrationService.pendingApprovals(apiKey, dingtalkUserId));
    }

    @GetMapping("/leave/requests/{id}")
    public ApiResponse<AgentIntegrationDtos.LeaveSummary> requestStatus(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(agentIntegrationService.requestStatus(apiKey, dingtalkUserId, id));
    }

    @GetMapping("/onboarding/requests")
    public ApiResponse<List<OnboardingDtos.RequestView>> onboardingRequests(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-DingTalk-User-Id") String dingtalkUserId
    ) {
        return ApiResponse.ok(agentIntegrationService.onboardingRequests(apiKey, dingtalkUserId));
    }

    @GetMapping("/notifications/pending")
    public ApiResponse<List<AgentIntegrationDtos.NotificationDelivery>> pendingNotifications(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(agentIntegrationService.pendingNotifications(apiKey, limit));
    }

    @PostMapping("/notifications/{id}/delivered")
    public ApiResponse<Void> notificationDelivered(
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable Long id
    ) {
        agentIntegrationService.notificationDelivered(apiKey, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/errors")
    public ApiResponse<Void> reportError(
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody AgentIntegrationDtos.ErrorReport request
    ) {
        agentIntegrationService.reportError(apiKey, request);
        return ApiResponse.ok(null);
    }
}
