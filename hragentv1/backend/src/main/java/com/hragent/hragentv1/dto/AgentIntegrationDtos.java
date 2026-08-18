package com.hragent.hragentv1.dto;

import com.hragent.hragentv1.domain.LeaveType;
import com.hragent.hragentv1.domain.RequestStatus;
import com.hragent.hragentv1.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AgentIntegrationDtos {
    private AgentIntegrationDtos() {
    }

    public record BindingCodeResponse(
            String code,
            LocalDateTime expiresAt
    ) {
    }

    public record BindingStatus(
            boolean bound,
            String maskedDingtalkUserId,
            LocalDateTime boundAt
    ) {
    }

    public record BindRequest(
            @NotBlank @Size(max = 32) String code
    ) {
    }

    public record EmployeeContext(
            String employeeNo,
            String name,
            String department,
            String title,
            String role,
            String managerEmployeeNo,
            String managerName,
            boolean managerBound
    ) {
    }

    public record BalanceResponse(
            String employeeNo,
            List<BalanceLine> balances
    ) {
    }

    public record PersonalProfile(
            String employeeNo,
            String displayName,
            String legalName,
            String englishName,
            Role role,
            String department,
            String title,
            String email,
            String phone,
            LocalDate entryDate,
            String employeeStatus,
            String managerName,
            String nationality,
            String idNumberMasked,
            String passportNumberMasked,
            LocalDate passportExpiryDate,
            String employmentType,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            String workLocation,
            BigDecimal monthlySalary,
            String currency,
            LocalDateTime updatedAt,
            boolean maintained
    ) {
    }

    public record BalanceLine(
            String leaveType,
            String leaveTypeLabel,
            BigDecimal totalDays,
            BigDecimal usedDays,
            BigDecimal reservedDays,
            BigDecimal availableDays
    ) {
    }

    public record LeaveInput(
            @NotNull LeaveType leaveType,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank @Size(max = 600) String reason
    ) {
    }

    public record LeavePreview(
            boolean canSubmit,
            String message,
            LeaveType leaveType,
            String leaveTypeLabel,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal workingDays,
            BigDecimal availableDaysBefore,
            BigDecimal availableDaysAfter,
            String managerEmployeeNo,
            String managerName
    ) {
    }

    public record LeaveApplication(
            Long id,
            String employeeNo,
            String employeeName,
            String managerEmployeeNo,
            String managerName,
            String managerDingtalkUserId,
            String employeeDingtalkUserId,
            LeaveType leaveType,
            String leaveTypeLabel,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal days,
            String reason,
            RequestStatus status,
            String statusLabel,
            LocalDateTime submittedAt,
            boolean alreadyProcessed
    ) {
    }

    public record ReviewInput(
            boolean approved,
            @Size(max = 600) String opinion
    ) {
    }

    public record LeaveSummary(
            Long id,
            String employeeNo,
            String employeeName,
            String managerEmployeeNo,
            String managerName,
            LeaveType leaveType,
            String leaveTypeLabel,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal days,
            String reason,
            RequestStatus status,
            String statusLabel,
            LocalDateTime submittedAt,
            LocalDateTime managerReviewedAt,
            LocalDateTime hrRecordedAt
    ) {
    }

    public record NotificationDelivery(
            Long id,
            String eventType,
            Long businessId,
            String dingtalkStaffId,
            String message,
            LocalDateTime createdAt,
            String approveToken,
            String rejectToken
    ) {
    }

    public record CardActionRequest(
            @NotBlank String token
    ) {
    }

    public record ErrorReport(
            @NotBlank @Size(max = 160) String workflowName,
            @Size(max = 160) String workflowId,
            @Size(max = 160) String executionId,
            @Size(max = 160) String lastNode,
            @NotBlank @Size(max = 1000) String message,
            @Size(max = 80) String occurredAt
    ) {
    }
}
