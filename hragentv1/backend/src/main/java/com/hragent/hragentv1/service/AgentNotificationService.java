package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.AgentNotification;
import com.hragent.hragentv1.domain.LeaveRequest;
import com.hragent.hragentv1.domain.RequestStatus;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.AgentIntegrationDtos;
import com.hragent.hragentv1.repo.AgentNotificationRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.web.AppException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentNotificationService {
    private static final String LEAVE_SUBMITTED = "LEAVE_SUBMITTED";
    private static final String LEAVE_PENDING_HR = "LEAVE_PENDING_HR";
    private static final String LEAVE_APPROVED = "LEAVE_APPROVED";
    private static final String LEAVE_REJECTED = "LEAVE_REJECTED";

    private final AgentNotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;
    private final AgentCardTokenService cardTokenService;

    public AgentNotificationService(
            AgentNotificationRepository notificationRepository,
            UserAccountRepository userAccountRepository,
            AgentCardTokenService cardTokenService
    ) {
        this.notificationRepository = notificationRepository;
        this.userAccountRepository = userAccountRepository;
        this.cardTokenService = cardTokenService;
    }

    @Transactional
    public void leaveCreated(LeaveRequest request) {
        if (request.getStatus() == RequestStatus.PENDING_MANAGER) {
            UserAccount employee = requireUser(request.getEmployeeId());
            String message = "【请假待审批】%s（%s）提交了%s申请 #%d：%s 至 %s，共 %s 天，原因：%s。"
                    .formatted(
                            employee.getName(),
                            employee.getEmployeeNo(),
                            request.getLeaveType().getLabel(),
                            request.getId(),
                            request.getStartDate(),
                            request.getEndDate(),
                            request.getDays().stripTrailingZeros().toPlainString(),
                            request.getReason()
                    )
                    + " 可直接点击钉钉审批卡片，也可以回复“同意 #" + request.getId() + "”或“拒绝 #"
                    + request.getId() + " 原因”，或在 SaaS 主管待办中处理。";
            enqueue(request, request.getManagerId(), LEAVE_SUBMITTED, message);
            return;
        }
        if (request.getStatus() == RequestStatus.PENDING_HR) {
            enqueueHrPending(request);
        }
    }

    @Transactional
    public void managerReviewed(LeaveRequest request) {
        cancelPending(request, LEAVE_SUBMITTED);
        if (request.getStatus() == RequestStatus.PENDING_HR) {
            enqueueHrPending(request);
        } else {
            enqueueFinal(request);
        }
    }

    @Transactional
    public void leaveFinalized(LeaveRequest request) {
        cancelPending(request, LEAVE_SUBMITTED);
        cancelPending(request, LEAVE_PENDING_HR);
        enqueueFinal(request);
    }

    @Transactional(readOnly = true)
    public List<AgentIntegrationDtos.NotificationDelivery> pending(Long tenantId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        return notificationRepository
                .findByTenantIdAndDeliveredAtIsNullOrderByCreatedAtAsc(
                        tenantId,
                        PageRequest.of(0, Math.max(limit * 5, 50))
                )
                .stream()
                .map(notification -> delivery(notification, tenantId))
                .filter(java.util.Objects::nonNull)
                .limit(limit)
                .toList();
    }

    @Transactional
    public void markDelivered(Long tenantId, Long notificationId) {
        AgentNotification notification = notificationRepository.findById(notificationId)
                .filter(value -> value.getTenantId().equals(tenantId))
                .orElseThrow(() -> AppException.notFound("Notification not found"));
        if (notification.getDeliveredAt() == null) {
            notification.setDeliveredAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    private void enqueueHrPending(LeaveRequest request) {
        UserAccount employee = requireUser(request.getEmployeeId());
        String message = "【请假待备案】%s（%s）的%s申请 #%d 已由主管通过。日期：%s 至 %s，共 %s 天。请在 SaaS HR 待办中完成备案。"
                .formatted(
                        employee.getName(),
                        employee.getEmployeeNo(),
                        request.getLeaveType().getLabel(),
                        request.getId(),
                        request.getStartDate(),
                        request.getEndDate(),
                        request.getDays().stripTrailingZeros().toPlainString()
                );
        userAccountRepository.findByTenantIdAndRole(request.getTenantId(), Role.HR)
                .stream()
                .filter(UserAccount::isActive)
                .forEach(hr -> enqueue(request, hr.getId(), LEAVE_PENDING_HR, message));
    }

    private void enqueueFinal(LeaveRequest request) {
        if (request.getStatus() != RequestStatus.APPROVED && request.getStatus() != RequestStatus.REJECTED) {
            return;
        }
        String eventType = request.getStatus() == RequestStatus.APPROVED ? LEAVE_APPROVED : LEAVE_REJECTED;
        String result = request.getStatus() == RequestStatus.APPROVED
                ? "已审批通过并完成 HR 备案"
                : "已被拒绝";
        String message = "【请假结果】您的%s申请 #%d（%s 至 %s，共 %s 天）%s。"
                .formatted(
                        request.getLeaveType().getLabel(),
                        request.getId(),
                        request.getStartDate(),
                        request.getEndDate(),
                        request.getDays().stripTrailingZeros().toPlainString(),
                        result
                );
        enqueue(request, request.getEmployeeId(), eventType, message);
    }

    private void enqueue(LeaveRequest request, Long recipientUserId, String eventType, String message) {
        if (recipientUserId == null) {
            return;
        }
        String dedupKey = "%d:%s:%d:%d".formatted(
                request.getTenantId(),
                eventType,
                request.getId(),
                recipientUserId
        );
        if (notificationRepository.findByDedupKey(dedupKey).isPresent()) {
            return;
        }
        AgentNotification notification = new AgentNotification();
        notification.setTenantId(request.getTenantId());
        notification.setRecipientUserId(recipientUserId);
        notification.setEventType(eventType);
        notification.setBusinessId(request.getId());
        notification.setDedupKey(dedupKey);
        notification.setMessage(message);
        notificationRepository.save(notification);
    }

    private void cancelPending(LeaveRequest request, String eventType) {
        List<AgentNotification> pending = notificationRepository
                .findByTenantIdAndBusinessIdAndEventTypeAndDeliveredAtIsNull(
                        request.getTenantId(),
                        request.getId(),
                        eventType
                );
        LocalDateTime now = LocalDateTime.now();
        pending.forEach(notification -> notification.setDeliveredAt(now));
        notificationRepository.saveAll(pending);
    }

    private AgentIntegrationDtos.NotificationDelivery delivery(AgentNotification notification, Long tenantId) {
        UserAccount recipient = userAccountRepository.findById(notification.getRecipientUserId())
                .filter(UserAccount::isActive)
                .filter(user -> user.getTenantId().equals(tenantId))
                .orElse(null);
        if (recipient == null
                || recipient.getDingtalkStaffId() == null
                || recipient.getDingtalkStaffId().isBlank()) {
            return null;
        }
        String approveToken = null;
        String rejectToken = null;
        if (LEAVE_SUBMITTED.equals(notification.getEventType())) {
            approveToken = cardTokenService.create(
                    notification.getTenantId(),
                    notification.getId(),
                    notification.getBusinessId(),
                    notification.getRecipientUserId(),
                    true
            );
            rejectToken = cardTokenService.create(
                    notification.getTenantId(),
                    notification.getId(),
                    notification.getBusinessId(),
                    notification.getRecipientUserId(),
                    false
            );
        }
        return new AgentIntegrationDtos.NotificationDelivery(
                notification.getId(),
                notification.getEventType(),
                notification.getBusinessId(),
                recipient.getDingtalkStaffId(),
                notification.getMessage(),
                notification.getCreatedAt(),
                approveToken,
                rejectToken
        );
    }

    private UserAccount requireUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("Notification recipient not found"));
    }
}
