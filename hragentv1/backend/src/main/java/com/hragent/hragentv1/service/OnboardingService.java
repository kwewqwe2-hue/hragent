package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.OnboardingRequest;
import com.hragent.hragentv1.domain.OnboardingRequestStatus;
import com.hragent.hragentv1.domain.EmployeeStatus;
import com.hragent.hragentv1.domain.LeaveBalance;
import com.hragent.hragentv1.domain.LeaveType;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.OnboardingDtos;
import com.hragent.hragentv1.domain.MembershipStatus;
import com.hragent.hragentv1.repo.OnboardingRequestRepository;
import com.hragent.hragentv1.repo.LeaveBalanceRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.repo.WorkspaceMembershipRepository;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Service
public class OnboardingService {
    private final OnboardingRequestRepository requestRepository;
    private final UserAccountRepository userAccountRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final AuditService auditService;

    public OnboardingService(
            OnboardingRequestRepository requestRepository,
            UserAccountRepository userAccountRepository,
            WorkspaceMembershipRepository membershipRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            AuditService auditService
    ) {
        this.requestRepository = requestRepository;
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.auditService = auditService;
    }

    @Transactional
    public OnboardingDtos.RequestView create(UserAccount actor, OnboardingDtos.CreateRequest input) {
        requireNewHire(actor);
        requestRepository.findFirstByTenantIdAndNewHireIdAndStatusOrderBySubmittedAtDesc(
                actor.getTenantId(), actor.getId(), OnboardingRequestStatus.PENDING_HR
        ).ifPresent(existing -> {
            throw AppException.badRequest("已有待 HR 审核的入职登记，请勿重复提交");
        });

        OnboardingRequest request = new OnboardingRequest();
        request.setTenantId(actor.getTenantId());
        request.setNewHireId(actor.getId());
        request.setLegalName(clean(input.legalName()));
        request.setPhone(clean(input.phone()));
        request.setPersonalEmail(clean(input.personalEmail()));
        request.setIdNumberLast4(input.idNumberLast4().trim().toUpperCase());
        request.setPlannedEntryDate(input.plannedEntryDate());
        request.setDepartment(clean(input.department()));
        request.setPositionTitle(clean(input.positionTitle()));
        request.setManagerName(clean(input.managerName()));
        request.setWorkLocation(clean(input.workLocation()));
        request.setEmergencyContactName(clean(input.emergencyContactName()));
        request.setEmergencyContactPhone(clean(input.emergencyContactPhone()));
        request.setBankName(clean(input.bankName()));
        request.setBankCardLast4(input.bankCardLast4().trim());
        request.setHighestEducation(clean(input.highestEducation()));
        request.setIdDocumentPrepared(input.idDocumentPrepared());
        request.setBankCardPrepared(input.bankCardPrepared());
        request.setEducationCertificatePrepared(input.educationCertificatePrepared());
        request.setPhotoPrepared(input.photoPrepared());
        request.setRemarks(clean(input.remarks()));
        request.setStatus(OnboardingRequestStatus.PENDING_HR);
        request.setSubmittedAt(LocalDateTime.now());
        OnboardingRequest saved = requestRepository.save(request);
        auditService.log(actor, "SUBMIT_ONBOARDING", "ONBOARDING_REQUEST", saved.getId(), "提交入职登记表");
        return view(saved);
    }

    @Transactional
    public List<OnboardingDtos.RequestView> mine(UserAccount actor) {
        if (actor.getRole() != Role.NEW_HIRE && actor.getRole() != Role.EMPLOYEE) {
            throw AppException.forbidden("只有新入职员工或普通员工可以查看入职记录");
        }
        List<OnboardingRequest> requests = requestRepository
                .findByTenantIdAndNewHireIdOrderBySubmittedAtDesc(actor.getTenantId(), actor.getId());
        if (actor.getRole() == Role.NEW_HIRE) {
            requests.stream()
                    .filter(request -> request.getStatus() == OnboardingRequestStatus.APPROVED)
                    .findFirst()
                    .ifPresent(this::activateEmployeeProfile);
        }
        return requests.stream().map(this::view).toList();
    }

    @Transactional
    public OnboardingDtos.RequestView completeOfficeSupplies(UserAccount actor) {
        if (actor.getRole() != Role.NEW_HIRE && actor.getRole() != Role.EMPLOYEE) {
            throw AppException.forbidden("只有新员工可以更新入职进度");
        }
        OnboardingRequest request = requestRepository
                .findFirstByTenantIdAndNewHireIdAndStatusOrderBySubmittedAtDesc(
                        actor.getTenantId(), actor.getId(), OnboardingRequestStatus.APPROVED
                )
                .orElseThrow(() -> AppException.badRequest("入职审核通过后才能确认办公用品领取"));
        if (!request.isOfficeSuppliesReceived()) {
            request.setOfficeSuppliesReceived(true);
            request = requestRepository.save(request);
            auditService.log(actor, "COMPLETE_ONBOARDING_OFFICE_SUPPLIES", "ONBOARDING_REQUEST", request.getId(), "确认领取办公用品");
        }
        return view(request);
    }

    @Transactional(readOnly = true)
    public List<OnboardingDtos.RequestView> hrAll(UserAccount actor) {
        requireHr(actor);
        return requestRepository.findByTenantIdOrderBySubmittedAtDesc(actor.getTenantId())
                .stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<OnboardingDtos.RequestView> hrPending(UserAccount actor) {
        requireHr(actor);
        return requestRepository.findByTenantIdAndStatusOrderBySubmittedAtDesc(
                actor.getTenantId(), OnboardingRequestStatus.PENDING_HR
        ).stream().map(this::view).toList();
    }

    @Transactional
    public OnboardingDtos.RequestView review(
            UserAccount actor,
            Long requestId,
            OnboardingDtos.ReviewRequest input
    ) {
        requireHr(actor);
        OnboardingRequest request = requestRepository.findByIdAndTenantId(requestId, actor.getTenantId())
                .orElseThrow(() -> AppException.notFound("入职申请不存在"));
        if (request.getStatus() != OnboardingRequestStatus.PENDING_HR) {
            throw AppException.badRequest("该入职申请已经处理，请刷新列表");
        }
        if (!Boolean.TRUE.equals(input.approved()) && isBlank(input.opinion())) {
            throw AppException.badRequest("驳回入职申请时必须填写原因");
        }
        request.setStatus(Boolean.TRUE.equals(input.approved())
                ? OnboardingRequestStatus.APPROVED
                : OnboardingRequestStatus.REJECTED);
        request.setHrOpinion(clean(input.opinion()));
        request.setReviewedByEmployeeId(actor.getId());
        request.setReviewedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(input.approved())) {
            activateEmployeeProfile(request);
        }
        OnboardingRequest saved = requestRepository.save(request);
        auditService.log(
                actor,
                Boolean.TRUE.equals(input.approved()) ? "APPROVE_ONBOARDING" : "REJECT_ONBOARDING",
                "ONBOARDING_REQUEST",
                saved.getId(),
                clean(input.opinion())
        );
        return view(saved);
    }

    private void activateEmployeeProfile(OnboardingRequest request) {
        UserAccount newHire = userAccountRepository.findById(request.getNewHireId())
                .filter(user -> user.getTenantId().equals(request.getTenantId()))
                .orElseThrow(() -> AppException.notFound("新入职员工账号不存在"));

        newHire.setRole(Role.EMPLOYEE);
        newHire.setEmployeeStatus(EmployeeStatus.ACTIVE);
        newHire.setActive(true);
        newHire.setName(request.getLegalName());
        newHire.setPhone(request.getPhone());
        newHire.setEmail(request.getPersonalEmail());
        newHire.setDepartment(request.getDepartment());
        newHire.setTitle(request.getPositionTitle());
        newHire.setEntryDate(request.getPlannedEntryDate());
        UserAccount savedEmployee = userAccountRepository.save(newHire);

        if (savedEmployee.getAccountId() == null) {
            throw AppException.badRequest("该入职账号尚未绑定企业成员，无法完成转正");
        }
        var membership = membershipRepository.findByAccountIdAndWorkspaceId(
                        savedEmployee.getAccountId(), request.getTenantId())
                .orElseThrow(() -> AppException.badRequest("该入职账号尚未绑定企业成员，无法完成转正"));
        membership.setEmployeeProfileId(savedEmployee.getId());
        membership.setRole(Role.EMPLOYEE);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setReviewedAt(LocalDateTime.now());
        membershipRepository.save(membership);
        ensureDefaultBalances(savedEmployee);
    }

    private void ensureDefaultBalances(UserAccount employee) {
        for (LeaveType leaveType : LeaveType.values()) {
            leaveBalanceRepository.findByTenantIdAndEmployeeIdAndLeaveType(
                            employee.getTenantId(), employee.getId(), leaveType)
                    .orElseGet(() -> {
                        LeaveBalance balance = new LeaveBalance();
                        balance.setTenantId(employee.getTenantId());
                        balance.setEmployeeId(employee.getId());
                        balance.setLeaveType(leaveType);
                        balance.setTotalDays(defaultTotal(leaveType));
                        balance.setUsedDays(BigDecimal.ZERO);
                        return leaveBalanceRepository.save(balance);
                    });
        }
    }

    private BigDecimal defaultTotal(LeaveType leaveType) {
        return switch (leaveType) {
            case ANNUAL, SICK, MARRIAGE -> new BigDecimal("10");
            case PERSONAL -> new BigDecimal("5");
        };
    }

    private OnboardingDtos.RequestView view(OnboardingRequest request) {
        UserAccount newHire = userAccountRepository.findById(request.getNewHireId())
                .filter(user -> user.getTenantId().equals(request.getTenantId()))
                .orElseThrow(() -> AppException.notFound("新入职员工账号不存在"));
        return new OnboardingDtos.RequestView(
                request.getId(), request.getNewHireId(), newHire.getEmployeeNo(), newHire.getName(),
                request.getLegalName(), request.getPhone(), request.getPersonalEmail(), request.getIdNumberLast4(),
                request.getPlannedEntryDate(), request.getDepartment(), request.getPositionTitle(),
                request.getManagerName(), request.getWorkLocation(), request.getEmergencyContactName(),
                request.getEmergencyContactPhone(), request.getBankName(), request.getBankCardLast4(),
                request.getHighestEducation(), request.isIdDocumentPrepared(), request.isBankCardPrepared(),
                request.isEducationCertificatePrepared(), request.isPhotoPrepared(), request.isOfficeSuppliesReceived(), request.getRemarks(),
                request.getStatus(), request.getStatus().getLabel(), request.getHrOpinion(),
                request.getSubmittedAt(), request.getReviewedAt()
        );
    }

    private void requireNewHire(UserAccount actor) {
        if (actor.getRole() != Role.NEW_HIRE) {
            throw AppException.forbidden("只有新入职员工可以提交入职登记");
        }
    }

    private void requireHr(UserAccount actor) {
        if (actor.getRole() != Role.HR) {
            throw AppException.forbidden("只有空间管理员可以审核入职申请");
        }
    }

    private String clean(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
