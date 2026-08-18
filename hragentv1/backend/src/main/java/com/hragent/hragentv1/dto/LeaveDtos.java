package com.hragent.hragentv1.dto;

import com.hragent.hragentv1.domain.LeaveRequest;
import com.hragent.hragentv1.domain.LeaveType;
import com.hragent.hragentv1.domain.RequestStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class LeaveDtos {
    public record LeaveTypeOption(
            LeaveType value,
            String label
    ) {
    }

    public record BalanceView(
            LeaveType leaveType,
            String leaveTypeLabel,
            BigDecimal totalDays,
            BigDecimal usedDays,
            BigDecimal remainingDays
    ) {
    }

    public record CalendarDayView(
            LocalDate date,
            String dayType,
            String label,
            LeaveType leaveType,
            String leaveTypeLabel,
            RequestStatus requestStatus
    ) {
    }

    public record CalendarView(
            int year,
            List<CalendarDayView> days
    ) {
    }

    public record CreateLeaveRequest(
            @NotNull LeaveType leaveType,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @DecimalMin("0.5") BigDecimal days,
            @NotBlank @Size(max = 600) String reason
    ) {
    }

    public record ReviewRequest(
            boolean approved,
            @Size(max = 600) String opinion
    ) {
    }

    public record LeaveRequestView(
            Long id,
            String employeeName,
            String managerName,
            LeaveType leaveType,
            String leaveTypeLabel,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal days,
            String reason,
            RequestStatus status,
            String statusLabel,
            String aiRiskLevel,
            String aiSummary,
            String aiEvidence,
            String managerOpinion,
            String hrOpinion,
            LocalDateTime submittedAt,
            LocalDateTime managerReviewedAt,
            LocalDateTime hrRecordedAt
    ) {
        public static LeaveRequestView from(LeaveRequest request, String employeeName, String managerName) {
            return new LeaveRequestView(
                    request.getId(),
                    employeeName,
                    managerName,
                    request.getLeaveType(),
                    request.getLeaveType().getLabel(),
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getDays(),
                    request.getReason(),
                    request.getStatus(),
                    request.getStatus().getLabel(),
                    request.getAiRiskLevel(),
                    request.getAiSummary(),
                    request.getAiEvidence(),
                    request.getManagerOpinion(),
                    request.getHrOpinion(),
                    request.getSubmittedAt(),
                    request.getManagerReviewedAt(),
                    request.getHrRecordedAt()
            );
        }
    }
}
