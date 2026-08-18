package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.ApiCallLog;
import com.hragent.hragentv1.domain.AgentNotification;
import com.hragent.hragentv1.domain.IntegrationApiKey;
import com.hragent.hragentv1.domain.LeaveRequest;
import com.hragent.hragentv1.domain.RequestStatus;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.AgentIntegrationDtos;
import com.hragent.hragentv1.dto.EmployeePersonalProfileDtos;
import com.hragent.hragentv1.dto.EmploymentCertificateDtos;
import com.hragent.hragentv1.dto.LeaveDtos;
import com.hragent.hragentv1.dto.OnboardingDtos;
import com.hragent.hragentv1.repo.ApiCallLogRepository;
import com.hragent.hragentv1.repo.AgentNotificationRepository;
import com.hragent.hragentv1.repo.LeaveRequestRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.web.AppException;
import com.hragent.hragentv1.web.RequestCorrelation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgentIntegrationService {
    private final OpenApiService openApiService;
    private final UserAccountRepository userAccountRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ApiCallLogRepository apiCallLogRepository;
    private final AgentNotificationRepository notificationRepository;
    private final LeaveService leaveService;
    private final AgentNotificationService agentNotificationService;
    private final AgentCardTokenService cardTokenService;
    private final WebChatIdentityService webChatIdentityService;
    private final EmployeePersonalProfileService personalProfileService;
    private final EmploymentCertificateService employmentCertificateService;
    private final OnboardingService onboardingService;

    public AgentIntegrationService(
            OpenApiService openApiService,
            UserAccountRepository userAccountRepository,
            LeaveRequestRepository leaveRequestRepository,
            ApiCallLogRepository apiCallLogRepository,
            AgentNotificationRepository notificationRepository,
            LeaveService leaveService,
            AgentNotificationService agentNotificationService,
            AgentCardTokenService cardTokenService,
            WebChatIdentityService webChatIdentityService,
            EmployeePersonalProfileService personalProfileService,
            EmploymentCertificateService employmentCertificateService,
            OnboardingService onboardingService
    ) {
        this.openApiService = openApiService;
        this.userAccountRepository = userAccountRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.apiCallLogRepository = apiCallLogRepository;
        this.notificationRepository = notificationRepository;
        this.leaveService = leaveService;
        this.agentNotificationService = agentNotificationService;
        this.cardTokenService = cardTokenService;
        this.webChatIdentityService = webChatIdentityService;
        this.personalProfileService = personalProfileService;
        this.employmentCertificateService = employmentCertificateService;
        this.onboardingService = onboardingService;
    }

    @Transactional
    public AgentIntegrationDtos.BindingStatus bind(
            String rawApiKey,
            String dingtalkUserId,
            String dingtalkStaffId,
            AgentIntegrationDtos.BindRequest request
    ) {
        IntegrationApiKey key = authenticate(rawApiKey, "POST", "/internal/agent/v1/identity/bind");
        if (dingtalkUserId == null || dingtalkUserId.isBlank()) {
            throw AppException.badRequest("Missing DingTalk user identity");
        }
        if (webChatIdentityService.isWebIdentity(dingtalkUserId)) {
            throw AppException.badRequest("Web chat identity cannot be used for DingTalk binding");
        }
        String codeHash = AuthService.hashBindingCode(request.code());
        UserAccount employee = userAccountRepository
                .findByTenantIdAndDingtalkBindingCodeHash(key.getTenantId(), codeHash)
                .stream()
                .filter(candidate -> candidate.getDingtalkBindingCodeExpiresAt() != null)
                .filter(candidate -> candidate.getDingtalkBindingCodeExpiresAt().isAfter(LocalDateTime.now()))
                .findFirst()
                .orElseThrow(() -> AppException.badRequest("Binding code is invalid or expired"));

        userAccountRepository.findByTenantIdAndDingtalkUserId(key.getTenantId(), dingtalkUserId.trim())
                .filter(existing -> !existing.getId().equals(employee.getId()))
                .ifPresent(existing -> {
                    throw AppException.badRequest("This DingTalk account is already bound to another employee");
                });
        employee.setDingtalkUserId(dingtalkUserId.trim());
        if (dingtalkStaffId != null && !dingtalkStaffId.isBlank()) {
            employee.setDingtalkStaffId(dingtalkStaffId.trim());
        }
        employee.setDingtalkBoundAt(LocalDateTime.now());
        employee.setDingtalkBindingCodeHash(null);
        employee.setDingtalkBindingCodeExpiresAt(null);
        userAccountRepository.save(employee);
        log(key, "POST", "/internal/agent/v1/identity/bind", 200, "DingTalk identity bound");
        return bindingStatus(employee);
    }

    public AgentIntegrationDtos.BindingStatus bindingStatus(String rawApiKey, String dingtalkUserId) {
        IntegrationApiKey key = authenticate(rawApiKey, "GET", "/internal/agent/v1/identity/status");
        UserAccount employee = userAccountRepository
                .findByTenantIdAndDingtalkUserId(key.getTenantId(), dingtalkUserId)
                .orElse(null);
        log(key, "GET", "/internal/agent/v1/identity/status", 200, "DingTalk identity status");
        return employee == null
                ? new AgentIntegrationDtos.BindingStatus(false, null, null)
                : bindingStatus(employee);
    }

    public AgentIntegrationDtos.EmployeeContext context(String rawApiKey, String dingtalkUserId) {
        IntegrationKeyAndUser context = resolve(rawApiKey, dingtalkUserId, "GET", "/internal/agent/v1/me");
        UserAccount manager = context.user().getManagerId() == null
                ? null
                : userAccountRepository.findById(context.user().getManagerId())
                .filter(UserAccount::isActive)
                .orElse(null);
        log(context.key(), "GET", "/internal/agent/v1/me", 200, "Employee context query");
        return new AgentIntegrationDtos.EmployeeContext(
                context.user().getEmployeeNo(),
                context.user().getName(),
                context.user().getDepartment(),
                context.user().getTitle(),
                context.user().getRole().name(),
                manager == null ? null : manager.getEmployeeNo(),
                manager == null ? null : manager.getName(),
                manager != null && manager.getDingtalkUserId() != null
                        && !manager.getDingtalkUserId().isBlank()
        );
    }

    public AgentIntegrationDtos.BalanceResponse balances(String rawApiKey, String dingtalkUserId) {
        IntegrationKeyAndUser context = resolve(rawApiKey, dingtalkUserId, "GET", "/internal/agent/v1/balances");
        log(context.key(), "GET", "/internal/agent/v1/balances", 200, "Employee balance query");
        return new AgentIntegrationDtos.BalanceResponse(
                context.user().getEmployeeNo(),
                leaveService.agentBalances(context.user())
        );
    }

    public AgentIntegrationDtos.PersonalProfile personalProfile(
            String rawApiKey,
            String dingtalkUserId
    ) {
        String path = "/internal/agent/v1/personal-profile";
        IntegrationKeyAndUser context = resolve(rawApiKey, dingtalkUserId, "GET", path);
        EmployeePersonalProfileDtos.ProfileView profile = personalProfileService.mine(context.user());
        log(context.key(), "GET", path, 200, "Employee personal profile query");
        return new AgentIntegrationDtos.PersonalProfile(
                profile.employeeNo(),
                profile.displayName(),
                profile.legalName(),
                profile.englishName(),
                profile.role(),
                profile.department(),
                profile.title(),
                profile.email(),
                profile.phone(),
                profile.entryDate(),
                profile.employeeStatus(),
                profile.managerName(),
                profile.nationality(),
                maskIdentifier(profile.idNumber()),
                maskIdentifier(profile.passportNumber()),
                profile.passportExpiryDate(),
                profile.employmentType(),
                profile.contractStartDate(),
                profile.contractEndDate(),
                profile.workLocation(),
                profile.monthlySalary(),
                profile.currency(),
                profile.updatedAt(),
                profile.maintained()
        );
    }

    @Transactional
    public EmploymentCertificateDtos.RequestView createCertificateRequest(
            String rawApiKey,
            String dingtalkUserId,
            EmploymentCertificateDtos.CreateRequest input
    ) {
        String path = "/internal/agent/v1/certificates/requests";
        IntegrationKeyAndUser context = resolve(rawApiKey, dingtalkUserId, "POST", path);
        EmploymentCertificateDtos.RequestView result = employmentCertificateService.create(context.user(), input);
        log(context.key(), "POST", path, 200, "Employment certificate request created");
        return result;
    }

    public List<EmploymentCertificateDtos.RequestView> myCertificateRequests(
            String rawApiKey,
            String dingtalkUserId
    ) {
        String path = "/internal/agent/v1/certificates/requests";
        IntegrationKeyAndUser context = resolve(rawApiKey, dingtalkUserId, "GET", path);
        List<EmploymentCertificateDtos.RequestView> result = employmentCertificateService.mine(context.user());
        log(context.key(), "GET", path, 200, "Employee certificate requests query");
        return result;
    }

    public AgentIntegrationDtos.LeavePreview preview(
            String rawApiKey,
            String dingtalkUserId,
            AgentIntegrationDtos.LeaveInput input
    ) {
        IntegrationKeyAndUser context = resolve(rawApiKey, dingtalkUserId, "POST", "/internal/agent/v1/leave/preview");
        AgentIntegrationDtos.LeavePreview preview = leaveService.previewForAgent(
                context.user(),
                toLeaveRequest(input)
        );
        log(context.key(), "POST", "/internal/agent/v1/leave/preview", 200, "Leave preview");
        return preview;
    }

    @Transactional
    public AgentIntegrationDtos.LeaveApplication create(
            String rawApiKey,
            String dingtalkUserId,
            AgentIntegrationDtos.LeaveInput input
    ) {
        IntegrationKeyAndUser context = resolve(rawApiKey, dingtalkUserId, "POST", "/internal/agent/v1/leave/requests");
        LeaveDtos.LeaveRequestView view = leaveService.createForAgent(
                context.user(),
                toLeaveRequest(input)
        );
        LeaveRequest request = leaveRequestRepository.findById(view.id())
                .orElseThrow(() -> AppException.notFound("Created leave request cannot be read"));
        log(context.key(), "POST", "/internal/agent/v1/leave/requests", 200, "Leave request created");
        return application(request, false);
    }

    @Transactional
    public AgentIntegrationDtos.LeaveApplication review(
            String rawApiKey,
            String dingtalkUserId,
            Long requestId,
            AgentIntegrationDtos.ReviewInput input
    ) {
        IntegrationKeyAndUser context = resolve(
                rawApiKey,
                dingtalkUserId,
                "POST",
                "/internal/agent/v1/leave/requests/" + requestId + "/review"
        );
        LeaveRequest before = leaveRequestRepository.findById(requestId)
                .filter(request -> request.getTenantId().equals(context.key().getTenantId()))
                .orElseThrow(() -> AppException.notFound("Leave request not found"));
        boolean alreadyProcessed = before.getStatus() != RequestStatus.PENDING_MANAGER;
        leaveService.managerReviewAndAutoRecord(
                context.user(),
                requestId,
                new LeaveDtos.ReviewRequest(input.approved(), input.opinion())
        );
        LeaveRequest after = leaveRequestRepository.findById(requestId).orElse(before);
        log(context.key(), "POST", "/internal/agent/v1/leave/requests/" + requestId + "/review", 200,
                "Leave request reviewed");
        return application(after, alreadyProcessed);
    }

    @Transactional
    public AgentIntegrationDtos.LeaveApplication cardAction(
            String rawApiKey,
            AgentIntegrationDtos.CardActionRequest input
    ) {
        IntegrationApiKey key = authenticate(
                rawApiKey,
                "POST",
                "/internal/agent/v1/leave/card-action"
        );
        AgentCardTokenService.CardAction action = cardTokenService.parse(input.token());
        if (!key.getTenantId().equals(action.tenantId())) {
            throw AppException.forbidden("审批卡片不属于当前工作空间");
        }
        AgentNotification notification = notificationRepository.findById(action.notificationId())
                .filter(value -> value.getTenantId().equals(key.getTenantId()))
                .filter(value -> value.getBusinessId().equals(action.requestId()))
                .filter(value -> "LEAVE_SUBMITTED".equals(value.getEventType()))
                .orElseThrow(() -> AppException.badRequest("审批卡片已失效"));
        if (!notification.getRecipientUserId().equals(action.actorUserId())) {
            throw AppException.forbidden("审批卡片接收人不匹配");
        }
        UserAccount actor = userAccountRepository.findById(action.actorUserId())
                .filter(UserAccount::isActive)
                .filter(user -> user.getTenantId().equals(key.getTenantId()))
                .orElseThrow(() -> AppException.forbidden("审批人账号不存在或已停用"));
        if (actor.getRole() != com.hragent.hragentv1.domain.Role.MANAGER
                && actor.getRole() != com.hragent.hragentv1.domain.Role.HR) {
            throw AppException.forbidden("只有主管或 HR 可以处理审批卡片");
        }
        LeaveRequest before = leaveRequestRepository.findById(action.requestId())
                .filter(request -> request.getTenantId().equals(key.getTenantId()))
                .orElseThrow(() -> AppException.notFound("Leave request not found"));
        boolean alreadyProcessed = before.getStatus() != RequestStatus.PENDING_MANAGER;
        leaveService.managerReviewAndAutoRecord(
                actor,
                action.requestId(),
                new LeaveDtos.ReviewRequest(
                        action.approved(),
                        "钉钉审批卡片操作"
                )
        );
        LeaveRequest after = leaveRequestRepository.findById(action.requestId()).orElse(before);
        log(key, "POST", "/internal/agent/v1/leave/card-action", 200,
                "DingTalk leave card action processed");
        return application(after, alreadyProcessed);
    }

    public List<AgentIntegrationDtos.LeaveSummary> myRequests(String rawApiKey, String dingtalkUserId) {
        IntegrationKeyAndUser context = resolve(
                rawApiKey,
                dingtalkUserId,
                "GET",
                "/internal/agent/v1/leave/requests"
        );
        List<AgentIntegrationDtos.LeaveSummary> result = leaveRequestRepository
                .findByTenantIdAndEmployeeIdOrderBySubmittedAtDesc(
                        context.key().getTenantId(),
                        context.user().getId()
                )
                .stream()
                .limit(20)
                .map(this::summary)
                .toList();
        log(context.key(), "GET", "/internal/agent/v1/leave/requests", 200, "Employee leave requests");
        return result;
    }

    public List<AgentIntegrationDtos.LeaveSummary> pendingApprovals(
            String rawApiKey,
            String dingtalkUserId
    ) {
        IntegrationKeyAndUser context = resolve(
                rawApiKey,
                dingtalkUserId,
                "GET",
                "/internal/agent/v1/leave/pending"
        );
        List<LeaveRequest> pending = switch (context.user().getRole()) {
            case MANAGER -> leaveRequestRepository.findByTenantIdAndManagerIdAndStatusOrderBySubmittedAtDesc(
                    context.key().getTenantId(),
                    context.user().getId(),
                    RequestStatus.PENDING_MANAGER
            );
            case HR -> leaveRequestRepository.findByTenantIdAndStatusOrderBySubmittedAtDesc(
                    context.key().getTenantId(),
                    RequestStatus.PENDING_HR
            );
            default -> List.of();
        };
        log(context.key(), "GET", "/internal/agent/v1/leave/pending", 200, "Pending leave approvals");
        return pending.stream().map(this::summary).toList();
    }

    public AgentIntegrationDtos.LeaveSummary requestStatus(
            String rawApiKey,
            String dingtalkUserId,
            Long requestId
    ) {
        IntegrationKeyAndUser context = resolve(
                rawApiKey,
                dingtalkUserId,
                "GET",
                "/internal/agent/v1/leave/requests/" + requestId
        );
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .filter(value -> value.getTenantId().equals(context.key().getTenantId()))
                .orElseThrow(() -> AppException.notFound("Leave request not found"));
        boolean visible = request.getEmployeeId().equals(context.user().getId())
                || request.getManagerId().equals(context.user().getId())
                || context.user().getRole() == com.hragent.hragentv1.domain.Role.HR;
        if (!visible) {
            throw AppException.forbidden("You cannot view this leave request");
        }
        log(context.key(), "GET", "/internal/agent/v1/leave/requests/" + requestId, 200,
                "Leave request status");
        return summary(request);
    }

    public List<OnboardingDtos.RequestView> onboardingRequests(
            String rawApiKey,
            String dingtalkUserId
    ) {
        String path = "/internal/agent/v1/onboarding/requests";
        IntegrationKeyAndUser context = resolve(rawApiKey, dingtalkUserId, "GET", path);
        List<OnboardingDtos.RequestView> result = onboardingService.mine(context.user());
        log(context.key(), "GET", path, 200, "New hire onboarding requests query");
        return result;
    }

    public List<AgentIntegrationDtos.NotificationDelivery> pendingNotifications(
            String rawApiKey,
            int limit
    ) {
        IntegrationApiKey key = authenticate(
                rawApiKey,
                "GET",
                "/internal/agent/v1/notifications/pending"
        );
        List<AgentIntegrationDtos.NotificationDelivery> result =
                agentNotificationService.pending(key.getTenantId(), limit);
        log(key, "GET", "/internal/agent/v1/notifications/pending", 200, "Pending notifications");
        return result;
    }

    public void notificationDelivered(String rawApiKey, Long notificationId) {
        IntegrationApiKey key = authenticate(
                rawApiKey,
                "POST",
                "/internal/agent/v1/notifications/" + notificationId + "/delivered"
        );
        agentNotificationService.markDelivered(key.getTenantId(), notificationId);
        log(key, "POST", "/internal/agent/v1/notifications/" + notificationId + "/delivered", 200,
                "Notification delivered");
    }

    public void reportError(String rawApiKey, AgentIntegrationDtos.ErrorReport report) {
        IntegrationApiKey key = authenticate(rawApiKey, "POST", "/internal/agent/v1/errors");
        String message = "n8n workflow=" + report.workflowName()
                + ", workflowId=" + safe(report.workflowId())
                + ", executionId=" + safe(report.executionId())
                + ", node=" + safe(report.lastNode())
                + ", message=" + report.message();
        log(key, "POST", "/internal/agent/v1/errors", 500, message);
    }

    private IntegrationKeyAndUser resolve(String rawApiKey, String dingtalkUserId, String method, String path) {
        IntegrationApiKey key = authenticate(rawApiKey, method, path);
        if (dingtalkUserId == null || dingtalkUserId.isBlank()) {
            throw AppException.unauthorized("Missing DingTalk user identity");
        }
        if (webChatIdentityService.isWebIdentity(dingtalkUserId)) {
            WebChatIdentityService.Identity identity = webChatIdentityService.verify(dingtalkUserId);
            if (!key.getTenantId().equals(identity.tenantId())) {
                throw AppException.forbidden("Web chat identity does not belong to this workspace");
            }
            UserAccount employee = userAccountRepository.findById(identity.employeeId())
                    .filter(candidate -> candidate.getTenantId().equals(key.getTenantId()))
                    .filter(UserAccount::isActive)
                    .orElseThrow(() -> AppException.forbidden("Web chat employee identity is no longer active"));
            return new IntegrationKeyAndUser(key, employee);
        }
        UserAccount employee = userAccountRepository.findByTenantIdAndDingtalkUserId(
                        key.getTenantId(), dingtalkUserId.trim())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> AppException.forbidden("DingTalk account is not bound to an employee in this workspace"));
        return new IntegrationKeyAndUser(key, employee);
    }

    private IntegrationApiKey authenticate(String rawApiKey, String method, String path) {
        return openApiService.authenticate(rawApiKey, method, path);
    }

    private LeaveDtos.CreateLeaveRequest toLeaveRequest(AgentIntegrationDtos.LeaveInput input) {
        return new LeaveDtos.CreateLeaveRequest(
                input.leaveType(),
                input.startDate(),
                input.endDate(),
                java.math.BigDecimal.ONE,
                input.reason()
        );
    }

    private AgentIntegrationDtos.LeaveApplication application(LeaveRequest request, boolean alreadyProcessed) {
        Map<Long, UserAccount> users = userAccountRepository.findAllById(
                        List.of(request.getEmployeeId(), request.getManagerId()))
                .stream()
                .collect(Collectors.toMap(UserAccount::getId, value -> value));
        UserAccount employee = users.get(request.getEmployeeId());
        UserAccount manager = users.get(request.getManagerId());
        return new AgentIntegrationDtos.LeaveApplication(
                request.getId(),
                employee == null ? null : employee.getEmployeeNo(),
                employee == null ? null : employee.getName(),
                manager == null ? null : manager.getEmployeeNo(),
                manager == null ? null : manager.getName(),
                manager == null ? null : manager.getDingtalkUserId(),
                employee == null ? null : employee.getDingtalkUserId(),
                request.getLeaveType(),
                request.getLeaveType().getLabel(),
                request.getStartDate(),
                request.getEndDate(),
                request.getDays(),
                request.getReason(),
                request.getStatus(),
                request.getStatus().getLabel(),
                request.getSubmittedAt(),
                alreadyProcessed
        );
    }

    private AgentIntegrationDtos.LeaveSummary summary(LeaveRequest request) {
        UserAccount employee = userAccountRepository.findById(request.getEmployeeId()).orElse(null);
        UserAccount manager = request.getManagerId() == null
                ? null
                : userAccountRepository.findById(request.getManagerId()).orElse(null);
        return new AgentIntegrationDtos.LeaveSummary(
                request.getId(),
                employee == null ? null : employee.getEmployeeNo(),
                employee == null ? null : employee.getName(),
                manager == null ? null : manager.getEmployeeNo(),
                manager == null ? null : manager.getName(),
                request.getLeaveType(),
                request.getLeaveType().getLabel(),
                request.getStartDate(),
                request.getEndDate(),
                request.getDays(),
                request.getReason(),
                request.getStatus(),
                request.getStatus().getLabel(),
                request.getSubmittedAt(),
                request.getManagerReviewedAt(),
                request.getHrRecordedAt()
        );
    }

    private AgentIntegrationDtos.BindingStatus bindingStatus(UserAccount employee) {
        String value = employee.getDingtalkUserId();
        String masked = value == null || value.length() < 6
                ? value
                : value.substring(0, 3) + "***" + value.substring(value.length() - 3);
        return new AgentIntegrationDtos.BindingStatus(
                value != null && !value.isBlank(),
                masked,
                employee.getDingtalkBoundAt()
        );
    }

    private void log(IntegrationApiKey key, String method, String path, int statusCode, String message) {
        ApiCallLog log = new ApiCallLog();
        log.setTenantId(key.getTenantId());
        log.setApiKeyId(key.getId());
        log.setMethod(method);
        log.setPath(path);
        log.setStatusCode(statusCode);
        log.setRequestId(RequestCorrelation.currentId());
        String detail = "[" + RequestCorrelation.currentId() + "] " + message;
        log.setMessage(detail.substring(0, Math.min(600, detail.length())));
        apiCallLogRepository.save(log);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.replaceAll("[\\r\\n]", " ");
    }

    private String maskIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.trim();
        if (clean.length() <= 4) {
            return "****";
        }
        return "*".repeat(Math.min(8, clean.length() - 4))
                + clean.substring(clean.length() - 4);
    }

    private record IntegrationKeyAndUser(IntegrationApiKey key, UserAccount user) {
    }
}
