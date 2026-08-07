package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.LeaveBalance;
import com.hragent.hragentv1.domain.LeaveRequest;
import com.hragent.hragentv1.domain.RequestStatus;
import com.hragent.hragentv1.repo.LeaveBalanceRepository;
import com.hragent.hragentv1.repo.LeaveRequestRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LeaveDataRepairService {
    private static final String REPAIR_NOTE = "系统校正：该申请与更早的已批准申请存在重复工作日，已退回重复扣减。";

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final WorkdayService workdayService;

    public LeaveDataRepairService(
            LeaveRequestRepository leaveRequestRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            WorkdayService workdayService
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.workdayService = workdayService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void repairApprovedLeaveCharges() {
        List<LeaveRequest> approvedRequests = leaveRequestRepository.findAll().stream()
                .filter(request -> request.getStatus() == RequestStatus.APPROVED)
                .sorted(Comparator
                        .comparing(this::approvalTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LeaveRequest::getId))
                .toList();
        Map<String, Set<LocalDate>> claimedDates = new HashMap<>();

        for (LeaveRequest request : approvedRequests) {
            String employeeKey = request.getTenantId() + ":" + request.getEmployeeId();
            Set<LocalDate> claimed = claimedDates.computeIfAbsent(employeeKey, ignored -> new HashSet<>());
            List<LocalDate> workingDates = workdayService.workingDates(request.getStartDate(), request.getEndDate());
            boolean overlaps = workingDates.stream().anyMatch(claimed::contains);
            BigDecimal originalDays = request.getDays() == null ? BigDecimal.ZERO : request.getDays();
            BigDecimal actualWorkingDays = BigDecimal.valueOf(workingDates.size());
            BigDecimal correctedDays = overlaps
                    ? BigDecimal.ZERO
                    : originalDays.min(actualWorkingDays);

            if (overlaps) {
                request.setStatus(RequestStatus.REJECTED);
                request.setHrOpinion(repairOpinion(request.getHrOpinion()));
            } else {
                claimed.addAll(workingDates);
            }
            request.setDays(correctedDays);
            adjustBalance(request, correctedDays.subtract(originalDays));
            leaveRequestRepository.save(request);
        }
    }

    private void adjustBalance(LeaveRequest request, BigDecimal difference) {
        if (difference.signum() == 0) {
            return;
        }
        LeaveBalance balance = leaveBalanceRepository
                .findByTenantIdAndEmployeeIdAndLeaveType(
                        request.getTenantId(), request.getEmployeeId(), request.getLeaveType())
                .orElse(null);
        if (balance == null) {
            return;
        }
        BigDecimal adjusted = balance.getUsedDays().add(difference);
        balance.setUsedDays(adjusted.signum() < 0 ? BigDecimal.ZERO : adjusted);
        leaveBalanceRepository.save(balance);
    }

    private LocalDateTime approvalTime(LeaveRequest request) {
        return request.getHrRecordedAt() == null ? request.getSubmittedAt() : request.getHrRecordedAt();
    }

    private String repairOpinion(String existing) {
        String value = existing == null || existing.isBlank() ? REPAIR_NOTE : existing + " " + REPAIR_NOTE;
        return value.length() <= 600 ? value : value.substring(0, 600);
    }
}
