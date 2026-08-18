package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.LeaveBalance;
import com.hragent.hragentv1.domain.LeaveRequest;
import com.hragent.hragentv1.domain.LeaveType;
import com.hragent.hragentv1.domain.RequestStatus;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.LeaveDtos;
import com.hragent.hragentv1.dto.AgentIntegrationDtos;
import com.hragent.hragentv1.repo.LeaveBalanceRepository;
import com.hragent.hragentv1.repo.LeaveRequestRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LeaveService {
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final UserAccountRepository userAccountRepository;
    private final AssistantService assistantService;
    private final AuditService auditService;
    private final WorkdayService workdayService;
    private final AgentNotificationService agentNotificationService;

    public LeaveService(
            LeaveBalanceRepository leaveBalanceRepository,
            LeaveRequestRepository leaveRequestRepository,
            UserAccountRepository userAccountRepository,
            AssistantService assistantService,
            AuditService auditService,
            WorkdayService workdayService,
            AgentNotificationService agentNotificationService
    ) {
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.userAccountRepository = userAccountRepository;
        this.assistantService = assistantService;
        this.auditService = auditService;
        this.workdayService = workdayService;
        this.agentNotificationService = agentNotificationService;
    }

    public List<LeaveDtos.LeaveTypeOption> leaveTypes() {
        return Arrays.stream(LeaveType.values())
                .map(type -> new LeaveDtos.LeaveTypeOption(type, type.getLabel()))
                .toList();
    }

    public List<LeaveDtos.BalanceView> balances(UserAccount user) {
        requireLeaveEligible(user);
        return leaveBalanceRepository.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(user.getTenantId(), user.getId())
                .stream()
                .map(balance -> new LeaveDtos.BalanceView(
                        balance.getLeaveType(),
                        balance.getLeaveType().getLabel(),
                        balance.getTotalDays(),
                        balance.getUsedDays(),
                        balance.remainingDays()
                ))
                .toList();
    }

    public List<AgentIntegrationDtos.BalanceLine> agentBalances(UserAccount user) {
        requireLeaveEligible(user);
        return leaveBalanceRepository.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(user.getTenantId(), user.getId())
                .stream()
                .map(balance -> {
                    BigDecimal reserved = reservedPendingDays(user, balance.getLeaveType());
                    return new AgentIntegrationDtos.BalanceLine(
                            balance.getLeaveType().name(),
                            balance.getLeaveType().getLabel(),
                            balance.getTotalDays(),
                            balance.getUsedDays(),
                            reserved,
                            balance.remainingDays().subtract(reserved)
                    );
                })
                .toList();
    }

    public AgentIntegrationDtos.LeavePreview previewForAgent(
            UserAccount employee,
            LeaveDtos.CreateLeaveRequest request
    ) {
        requireLeaveEligible(employee);
        UserAccount manager = activeManager(employee);
        if (request.endDate().isBefore(request.startDate())) {
            throw AppException.badRequest("结束日期不能早于开始日期");
        }
        List<LocalDate> workingDates = workdayService.workingDates(request.startDate(), request.endDate());
        if (workingDates.isEmpty()) {
            throw AppException.badRequest("所选日期范围没有工作日");
        }
        BigDecimal workingDays = BigDecimal.valueOf(workingDates.size());
        ensureNoOverlappingRequest(
                employee,
                request.startDate(),
                request.endDate(),
                null,
                Set.of(RequestStatus.PENDING_MANAGER, RequestStatus.PENDING_HR, RequestStatus.APPROVED)
        );
        LeaveBalance balance = leaveBalanceRepository
                .findByTenantIdAndEmployeeIdAndLeaveType(employee.getTenantId(), employee.getId(), request.leaveType())
                .orElseThrow(() -> AppException.badRequest("没有配置该假别余额"));
        BigDecimal reserved = reservedPendingDays(employee, request.leaveType());
        BigDecimal availableBefore = balance.remainingDays().subtract(reserved);
        BigDecimal availableAfter = availableBefore.subtract(workingDays);
        if (availableAfter.signum() < 0) {
            throw AppException.badRequest("假期余额不足，可用余额为 %s 天".formatted(availableBefore));
        }
        return new AgentIntegrationDtos.LeavePreview(
                true,
                "校验通过，等待员工确认提交",
                request.leaveType(),
                request.leaveType().getLabel(),
                request.startDate(),
                request.endDate(),
                workingDays,
                availableBefore,
                availableAfter,
                manager.getEmployeeNo(),
                manager.getName()
        );
    }

    public LeaveDtos.LeaveRequestView createForAgent(
            UserAccount employee,
            LeaveDtos.CreateLeaveRequest request
    ) {
        requireLeaveEligible(employee);
        activeManager(employee);
        return create(employee, request);
    }

    public UserAccount activeManager(UserAccount employee) {
        if (employee.getManagerId() == null) {
            throw AppException.badRequest("当前员工没有配置直属主管");
        }
        return userAccountRepository.findById(employee.getManagerId())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> AppException.badRequest("当前员工的直属主管不存在或已停用"));
    }

    @Transactional
    public LeaveDtos.LeaveRequestView create(UserAccount employee, LeaveDtos.CreateLeaveRequest request) {
        requireLeaveEligible(employee);
        if (request.endDate().isBefore(request.startDate())) {
            throw AppException.badRequest("结束日期不能早于开始日期");
        }
        List<LocalDate> workingDates = workdayService.workingDates(request.startDate(), request.endDate());
        if (workingDates.isEmpty()) {
            throw AppException.badRequest("所选日期范围没有工作日");
        }
        BigDecimal chargeDays = BigDecimal.valueOf(workingDates.size());
        ensureNoOverlappingRequest(
                employee,
                request.startDate(),
                request.endDate(),
                null,
                Set.of(RequestStatus.PENDING_MANAGER, RequestStatus.PENDING_HR, RequestStatus.APPROVED)
        );
        Long reviewerId = employee.getManagerId();
        RequestStatus initialStatus = RequestStatus.PENDING_MANAGER;
        if (reviewerId == null || userAccountRepository.findById(reviewerId).filter(UserAccount::isActive).isEmpty()) {
            reviewerId = userAccountRepository.findByTenantIdAndRole(employee.getTenantId(), Role.HR).stream()
                    .filter(UserAccount::isActive)
                    .map(UserAccount::getId)
                    .findFirst()
                    .orElseThrow(() -> AppException.badRequest("当前空间没有可处理申请的管理员"));
            initialStatus = RequestStatus.PENDING_HR;
        }

        LeaveBalance balance = leaveBalanceRepository
                .findByTenantIdAndEmployeeIdAndLeaveType(employee.getTenantId(), employee.getId(), request.leaveType())
                .orElseThrow(() -> AppException.badRequest("没有配置该假别余额"));
        BigDecimal reservedDays = reservedPendingDays(employee, request.leaveType());
        BigDecimal availableDays = balance.remainingDays().subtract(reservedDays);
        if (availableDays.compareTo(chargeDays) < 0) {
            throw AppException.badRequest("%s可申请余额不足，剩余 %s 天，审批中已占用 %s 天".formatted(
                    request.leaveType().getLabel(),
                    balance.remainingDays(),
                    reservedDays
            ));
        }

        AssistantService.LeaveAiResult ai = assistantService.reviewLeaveDraft(
                employee,
                request.leaveType(),
                chargeDays,
                request.reason()
        );

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setTenantId(employee.getTenantId());
        leaveRequest.setEmployeeId(employee.getId());
        leaveRequest.setManagerId(reviewerId);
        leaveRequest.setLeaveType(request.leaveType());
        leaveRequest.setStartDate(request.startDate());
        leaveRequest.setEndDate(request.endDate());
        leaveRequest.setDays(chargeDays);
        leaveRequest.setReason(request.reason());
        leaveRequest.setStatus(initialStatus);
        leaveRequest.setAiRiskLevel(ai.riskLevel());
        leaveRequest.setAiSummary(ai.summary());
        leaveRequest.setAiEvidence(ai.evidence());
        leaveRequest.setSubmittedAt(LocalDateTime.now());
        leaveRequestRepository.save(leaveRequest);
        agentNotificationService.leaveCreated(leaveRequest);

        auditService.log(employee, "提交请假申请", "leave_request", leaveRequest.getId(),
                "%s %s 天".formatted(request.leaveType().getLabel(), chargeDays));
        return toView(leaveRequest);
    }

    public List<LeaveDtos.LeaveRequestView> mine(UserAccount user) {
        requireLeaveEligible(user);
        return leaveRequestRepository
                .findByTenantIdAndEmployeeIdOrderBySubmittedAtDesc(user.getTenantId(), user.getId())
                .stream()
                .map(this::toView)
                .toList();
    }

    public LeaveDtos.CalendarView calendar(UserAccount user, int year) {
        requireLeaveEligible(user);
        if (year < 2020 || year > 2100) {
            throw AppException.badRequest("日历年份必须在 2020 到 2100 之间");
        }
        LocalDate firstDay = LocalDate.of(year, 1, 1);
        LocalDate nextYear = firstDay.plusYears(1);
        Map<LocalDate, LeaveRequest> requestByDate = new HashMap<>();
        for (LeaveRequest request : leaveRequestRepository
                .findByTenantIdAndEmployeeIdOrderBySubmittedAtDesc(user.getTenantId(), user.getId())) {
            if (request.getStatus() == RequestStatus.REJECTED
                    || request.getEndDate().isBefore(firstDay)
                    || !request.getStartDate().isBefore(nextYear)) {
                continue;
            }
            LocalDate date = request.getStartDate().isBefore(firstDay) ? firstDay : request.getStartDate();
            LocalDate end = request.getEndDate().isBefore(nextYear) ? request.getEndDate() : nextYear.minusDays(1);
            while (!date.isAfter(end)) {
                if (workdayService.isWorkingDay(date)) {
                    LeaveRequest current = requestByDate.get(date);
                    if (current == null || calendarPriority(request) > calendarPriority(current)) {
                        requestByDate.put(date, request);
                    }
                }
                date = date.plusDays(1);
            }
        }

        List<LeaveDtos.CalendarDayView> days = firstDay.datesUntil(nextYear)
                .map(date -> calendarDay(date, requestByDate.get(date)))
                .toList();
        return new LeaveDtos.CalendarView(year, days);
    }

    public List<LeaveDtos.LeaveRequestView> managerPending(UserAccount manager) {
        return leaveRequestRepository
                .findByTenantIdAndManagerIdAndStatusOrderBySubmittedAtDesc(
                        manager.getTenantId(),
                        manager.getId(),
                        RequestStatus.PENDING_MANAGER
                )
                .stream()
                .map(this::toView)
                .toList();
    }

    public List<LeaveDtos.LeaveRequestView> hrPending(UserAccount hr) {
        return leaveRequestRepository
                .findByTenantIdAndStatusOrderBySubmittedAtDesc(hr.getTenantId(), RequestStatus.PENDING_HR)
                .stream()
                .map(this::toView)
                .toList();
    }

    public List<LeaveDtos.LeaveRequestView> all(Long tenantId) {
        return leaveRequestRepository.findByTenantIdOrderBySubmittedAtDesc(tenantId)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public LeaveDtos.LeaveRequestView managerReview(UserAccount manager, Long id, LeaveDtos.ReviewRequest request) {
        LeaveRequest leaveRequest = findByIdAndTenant(id, manager.getTenantId());
        if (!leaveRequest.getManagerId().equals(manager.getId())) {
            throw AppException.forbidden("你只能审批自己下属提交的申请");
        }
        if (leaveRequest.getStatus() != RequestStatus.PENDING_MANAGER) {
            throw AppException.badRequest("当前申请不在主管审批阶段");
        }
        leaveRequest.setManagerOpinion(request.opinion());
        leaveRequest.setManagerReviewedAt(LocalDateTime.now());
        leaveRequest.setStatus(request.approved() ? RequestStatus.PENDING_HR : RequestStatus.REJECTED);
        leaveRequestRepository.save(leaveRequest);
        agentNotificationService.managerReviewed(leaveRequest);

        auditService.log(manager, request.approved() ? "主管审批通过" : "主管驳回申请",
                "leave_request", leaveRequest.getId(), request.opinion());
        return toView(leaveRequest);
    }

    @Transactional
    public LeaveDtos.LeaveRequestView managerReviewAndAutoRecord(
            UserAccount manager,
            Long id,
            LeaveDtos.ReviewRequest request
    ) {
        LeaveRequest leaveRequest = findByIdAndTenant(id, manager.getTenantId());
        if (!leaveRequest.getManagerId().equals(manager.getId())) {
            throw AppException.forbidden("只能处理自己直属下属的请假申请");
        }
        if (leaveRequest.getStatus() != RequestStatus.PENDING_MANAGER) {
            // DingTalk may deliver the same card click more than once. Treat it as idempotent.
            return toView(leaveRequest);
        }

        leaveRequest.setManagerOpinion(request.opinion());
        leaveRequest.setManagerReviewedAt(LocalDateTime.now());
        leaveRequest.setHrOpinion("系统自动备案");
        leaveRequest.setHrRecordedAt(LocalDateTime.now());
        if (request.approved()) {
            recordApproved(leaveRequest);
        } else {
            leaveRequest.setStatus(RequestStatus.REJECTED);
        }
        leaveRequestRepository.save(leaveRequest);
        agentNotificationService.leaveFinalized(leaveRequest);
        auditService.log(manager, request.approved() ? "主管审批通过并自动备案" : "主管驳回请假申请",
                "leave_request", leaveRequest.getId(), request.opinion());
        return toView(leaveRequest);
    }

    private void recordApproved(LeaveRequest leaveRequest) {
        UserAccount employee = userAccountRepository.findById(leaveRequest.getEmployeeId())
                .orElseThrow(() -> AppException.notFound("员工档案不存在"));
        ensureNoOverlappingRequest(
                employee,
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                leaveRequest.getId(),
                Set.of(RequestStatus.APPROVED)
        );
        BigDecimal chargeDays = BigDecimal.valueOf(workdayService
                .workingDates(leaveRequest.getStartDate(), leaveRequest.getEndDate()).size());
        if (chargeDays.signum() == 0) {
            throw AppException.badRequest("申请日期范围内没有工作日");
        }
        LeaveBalance balance = leaveBalanceRepository
                .findForUpdateByTenantIdAndEmployeeIdAndLeaveType(
                        leaveRequest.getTenantId(),
                        leaveRequest.getEmployeeId(),
                        leaveRequest.getLeaveType()
                )
                .orElseThrow(() -> AppException.badRequest("没有配置该假别余额"));
        if (balance.remainingDays().compareTo(chargeDays) < 0) {
            throw AppException.badRequest("余额不足，无法完成自动备案");
        }
        balance.setUsedDays(balance.getUsedDays().add(chargeDays));
        leaveBalanceRepository.save(balance);
        leaveRequest.setDays(chargeDays);
        leaveRequest.setStatus(RequestStatus.APPROVED);
    }

    @Transactional
    public LeaveDtos.LeaveRequestView hrRecord(UserAccount hr, Long id, LeaveDtos.ReviewRequest request) {
        LeaveRequest leaveRequest = findByIdAndTenant(id, hr.getTenantId());
        if (leaveRequest.getStatus() != RequestStatus.PENDING_HR) {
            throw AppException.badRequest("当前申请不在 HR 备案阶段");
        }

        leaveRequest.setHrOpinion(request.opinion());
        leaveRequest.setHrRecordedAt(LocalDateTime.now());

        if (request.approved()) {
            UserAccount employee = userAccountRepository.findById(leaveRequest.getEmployeeId())
                    .orElseThrow(() -> AppException.notFound("员工档案不存在"));
            ensureNoOverlappingRequest(
                    employee,
                    leaveRequest.getStartDate(),
                    leaveRequest.getEndDate(),
                    leaveRequest.getId(),
                    Set.of(RequestStatus.APPROVED)
            );
            BigDecimal chargeDays = BigDecimal.valueOf(workdayService
                    .workingDates(leaveRequest.getStartDate(), leaveRequest.getEndDate()).size());
            if (chargeDays.signum() == 0) {
                throw AppException.badRequest("该申请日期范围内没有工作日，不能备案通过");
            }
            leaveRequest.setDays(chargeDays);
            LeaveBalance balance = leaveBalanceRepository
                    .findByTenantIdAndEmployeeIdAndLeaveType(
                            leaveRequest.getTenantId(),
                            leaveRequest.getEmployeeId(),
                            leaveRequest.getLeaveType()
                    )
                    .orElseThrow(() -> AppException.badRequest("没有配置该假别余额"));
            if (balance.remainingDays().compareTo(chargeDays) < 0) {
                throw AppException.badRequest("%s余额不足，无法完成备案".formatted(leaveRequest.getLeaveType().getLabel()));
            }
            balance.setUsedDays(balance.getUsedDays().add(chargeDays));
            leaveBalanceRepository.save(balance);
            leaveRequest.setStatus(RequestStatus.APPROVED);
        } else {
            leaveRequest.setStatus(RequestStatus.REJECTED);
        }

        leaveRequestRepository.save(leaveRequest);
        agentNotificationService.leaveFinalized(leaveRequest);
        auditService.log(hr, request.approved() ? "HR备案通过并扣减余额" : "HR备案驳回",
                "leave_request", leaveRequest.getId(), request.opinion());
        return toView(leaveRequest);
    }

    public Map<String, Long> stats(UserAccount user) {
        if (user.getRole() == Role.NEW_HIRE) {
            return Map.of("pendingManager", 0L, "pendingHr", 0L, "approved", 0L, "rejected", 0L);
        }
        Long tenantId = user.getTenantId();
        if (user.getRole() == Role.EMPLOYEE) {
            return Map.of(
                    "pendingManager", leaveRequestRepository.countByTenantIdAndEmployeeIdAndStatus(
                            tenantId, user.getId(), RequestStatus.PENDING_MANAGER),
                    "pendingHr", leaveRequestRepository.countByTenantIdAndEmployeeIdAndStatus(
                            tenantId, user.getId(), RequestStatus.PENDING_HR),
                    "approved", leaveRequestRepository.countByTenantIdAndEmployeeIdAndStatus(
                            tenantId, user.getId(), RequestStatus.APPROVED),
                    "rejected", leaveRequestRepository.countByTenantIdAndEmployeeIdAndStatus(
                            tenantId, user.getId(), RequestStatus.REJECTED)
            );
        }
        if (user.getRole() == Role.MANAGER) {
            return Map.of(
                    "pendingManager", leaveRequestRepository.countByTenantIdAndManagerIdAndStatus(
                            tenantId, user.getId(), RequestStatus.PENDING_MANAGER),
                    "pendingHr", leaveRequestRepository.countByTenantIdAndManagerIdAndStatus(
                            tenantId, user.getId(), RequestStatus.PENDING_HR),
                    "approved", leaveRequestRepository.countByTenantIdAndManagerIdAndStatus(
                            tenantId, user.getId(), RequestStatus.APPROVED),
                    "rejected", leaveRequestRepository.countByTenantIdAndManagerIdAndStatus(
                            tenantId, user.getId(), RequestStatus.REJECTED)
            );
        }
        if (user.getRole() == Role.HR) {
            return Map.of(
                "pendingManager", leaveRequestRepository.countByTenantIdAndStatus(tenantId, RequestStatus.PENDING_MANAGER),
                "pendingHr", leaveRequestRepository.countByTenantIdAndStatus(tenantId, RequestStatus.PENDING_HR),
                "approved", leaveRequestRepository.countByTenantIdAndStatus(tenantId, RequestStatus.APPROVED),
                "rejected", leaveRequestRepository.countByTenantIdAndStatus(tenantId, RequestStatus.REJECTED)
            );
        }
        return Map.of("pendingManager", 0L, "pendingHr", 0L, "approved", 0L, "rejected", 0L);
    }

    private void requireLeaveEligible(UserAccount user) {
        if (user.getRole() == Role.NEW_HIRE) {
            throw AppException.forbidden("新入职员工尚未转为正式员工，不能使用请假功能");
        }
    }

    private LeaveRequest findByIdAndTenant(Long id, Long tenantId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("请假申请不存在"));
        if (!leaveRequest.getTenantId().equals(tenantId)) {
            throw AppException.notFound("请假申请不存在");
        }
        return leaveRequest;
    }

    private LeaveDtos.LeaveRequestView toView(LeaveRequest request) {
        Map<Long, UserAccount> users = userAccountRepository.findAllById(List.of(request.getEmployeeId(), request.getManagerId()))
                .stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        String employeeName = users.containsKey(request.getEmployeeId()) ? users.get(request.getEmployeeId()).getName() : "未知员工";
        String managerName = users.containsKey(request.getManagerId()) ? users.get(request.getManagerId()).getName() : "未知主管";
        return LeaveDtos.LeaveRequestView.from(request, employeeName, managerName);
    }

    private LeaveDtos.CalendarDayView calendarDay(LocalDate date, LeaveRequest request) {
        if (request != null && request.getStatus() == RequestStatus.APPROVED) {
            return new LeaveDtos.CalendarDayView(
                    date,
                    "LEAVE",
                    request.getLeaveType().getLabel(),
                    request.getLeaveType(),
                    request.getLeaveType().getLabel(),
                    request.getStatus()
            );
        }
        if (request != null) {
            return new LeaveDtos.CalendarDayView(
                    date,
                    "PENDING",
                    request.getLeaveType().getLabel() + "审批中",
                    request.getLeaveType(),
                    request.getLeaveType().getLabel(),
                    request.getStatus()
            );
        }
        boolean weekend = !workdayService.isWorkingDay(date);
        return new LeaveDtos.CalendarDayView(
                date,
                weekend ? "REST" : "WORK",
                weekend ? "休息" : "上班",
                null,
                null,
                null
        );
    }

    private int calendarPriority(LeaveRequest request) {
        return request.getStatus() == RequestStatus.APPROVED ? 2 : 1;
    }

    private void ensureNoOverlappingRequest(
            UserAccount employee,
            LocalDate startDate,
            LocalDate endDate,
            Long excludedRequestId,
            Set<RequestStatus> blockedStatuses
    ) {
        LeaveRequest conflict = leaveRequestRepository
                .findByTenantIdAndEmployeeIdOrderBySubmittedAtDesc(employee.getTenantId(), employee.getId())
                .stream()
                .filter(existing -> excludedRequestId == null || !existing.getId().equals(excludedRequestId))
                .filter(existing -> blockedStatuses.contains(existing.getStatus()))
                .filter(existing -> workdayService.overlapsOnWorkingDay(
                        startDate, endDate, existing.getStartDate(), existing.getEndDate()))
                .findFirst()
                .orElse(null);
        if (conflict != null) {
            throw AppException.badRequest(
                    "所选日期与申请 #%s（%s 至 %s，%s）存在重复工作日"
                            .formatted(
                                    conflict.getId(),
                                    conflict.getStartDate(),
                                    conflict.getEndDate(),
                                    conflict.getStatus().getLabel()
                            )
            );
        }
    }

    private BigDecimal reservedPendingDays(UserAccount employee, LeaveType leaveType) {
        long days = leaveRequestRepository
                .findByTenantIdAndEmployeeIdOrderBySubmittedAtDesc(employee.getTenantId(), employee.getId())
                .stream()
                .filter(request -> request.getLeaveType() == leaveType)
                .filter(request -> request.getStatus() == RequestStatus.PENDING_MANAGER
                        || request.getStatus() == RequestStatus.PENDING_HR)
                .mapToLong(request -> workdayService
                        .workingDates(request.getStartDate(), request.getEndDate()).size())
                .sum();
        return BigDecimal.valueOf(days);
    }
}
