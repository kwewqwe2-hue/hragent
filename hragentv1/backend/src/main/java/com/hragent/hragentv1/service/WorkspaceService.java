package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.WorkspaceDtos;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.web.AppException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {
    private final AuthService authService;
    private final TenantRepository tenantRepository;
    private final PlatformAccountRepository accountRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;
    private final JobTitleRepository jobTitleRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final AuditService auditService;

    public WorkspaceService(
            AuthService authService,
            TenantRepository tenantRepository,
            PlatformAccountRepository accountRepository,
            WorkspaceMembershipRepository membershipRepository,
            UserAccountRepository userAccountRepository,
            DepartmentRepository departmentRepository,
            JobTitleRepository jobTitleRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            AuditService auditService
    ) {
        this.authService = authService;
        this.tenantRepository = tenantRepository;
        this.accountRepository = accountRepository;
        this.membershipRepository = membershipRepository;
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.auditService = auditService;
    }

    public List<WorkspaceDtos.WorkspaceSummary> mine(HttpServletRequest request) {
        return authService.workspaces(request);
    }

    @Transactional
    public WorkspaceDtos.WorkspaceSummary create(
            HttpServletRequest request,
            WorkspaceDtos.CreateWorkspaceRequest payload
    ) {
        PlatformAccount account = authService.requireAccount(request);
        Tenant workspace = new Tenant();
        workspace.setCode(generateWorkspaceCode());
        workspace.setName(payload.name().trim());
        workspace.setCreatedByAccountId(account.getId());
        Tenant savedWorkspace = tenantRepository.save(workspace);

        seedAdminOrg(savedWorkspace.getId());
        UserAccount employeeProfile = createAdminProfile(savedWorkspace, account);
        seedDefaultBalances(savedWorkspace.getId(), employeeProfile.getId());

        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspaceId(savedWorkspace.getId());
        membership.setAccountId(account.getId());
        membership.setEmployeeProfileId(employeeProfile.getId());
        membership.setRole(Role.HR);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setReviewedAt(LocalDateTime.now());
        membership.setReviewedByAccountId(account.getId());
        membershipRepository.save(membership);

        return new WorkspaceDtos.WorkspaceSummary(
                savedWorkspace.getId(),
                savedWorkspace.getName(),
                savedWorkspace.getCode(),
                Role.HR,
                MembershipStatus.ACTIVE,
                employeeProfile.getId(),
                1
        );
    }

    @Transactional
    public WorkspaceDtos.WorkspaceSummary join(
            HttpServletRequest request,
            WorkspaceDtos.JoinWorkspaceRequest payload
    ) {
        PlatformAccount account = authService.requireAccount(request);
        Tenant workspace = tenantRepository.findByCodeIgnoreCase(payload.workspaceCode().trim())
                .filter(Tenant::isActive)
                .orElseThrow(() -> AppException.notFound("空间码不存在或空间已停用"));

        var existingMembership = membershipRepository.findByAccountIdAndWorkspaceId(account.getId(), workspace.getId());
        if (existingMembership.isPresent()) {
            WorkspaceMembership existing = existingMembership.get();
            if (existing.getStatus() == MembershipStatus.ACTIVE
                    || existing.getStatus() == MembershipStatus.DISABLED) {
                return summary(workspace, existing);
            }
            if (existing.getStatus() == MembershipStatus.PENDING
                    || existing.getStatus() == MembershipStatus.PENDING_PROFILE) {
                applyProfileDraft(existing, payload);
                return summary(workspace, membershipRepository.save(existing));
            }
        }
        WorkspaceMembership membership = existingMembership.orElseGet(WorkspaceMembership::new);
        membership.setWorkspaceId(workspace.getId());
        membership.setAccountId(account.getId());
        membership.setStatus(MembershipStatus.PENDING);
        membership.setRole(Role.EMPLOYEE);
        membership.setEmployeeProfileId(null);
        membership.setReviewedAt(null);
        membership.setReviewedByAccountId(null);
        applyProfileDraft(membership, payload);
        WorkspaceMembership saved = membershipRepository.save(membership);
        return summary(workspace, saved);
    }

    @Transactional
    public WorkspaceDtos.WorkspaceSummary leave(HttpServletRequest request, Long workspaceId) {
        PlatformAccount account = authService.requireAccount(request);
        Tenant workspace = tenantRepository.findById(workspaceId)
                .orElseThrow(() -> AppException.notFound("企业空间不存在"));
        WorkspaceMembership membership = membershipRepository
                .findByAccountIdAndWorkspaceId(account.getId(), workspaceId)
                .filter(item -> item.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> AppException.badRequest("当前账号不是该空间的有效成员"));

        if (membership.getRole() == Role.HR
                && membershipRepository.countByWorkspaceIdAndRoleAndStatus(
                workspaceId, Role.HR, MembershipStatus.ACTIVE) <= 1) {
            throw AppException.badRequest("最后一名空间管理员不能退出，请先将其他成员设为空间管理员");
        }

        UserAccount profile = membership.getEmployeeProfileId() == null
                ? null
                : userAccountRepository.findById(membership.getEmployeeProfileId()).orElse(null);
        if (profile != null && membership.getRole() == Role.MANAGER
                && userAccountRepository.countByTenantIdAndManagerIdAndActiveTrue(workspaceId, profile.getId()) > 0) {
            throw AppException.badRequest("该主管仍有直属在职员工，请先转移直属主管关系");
        }

        membership.setStatus(MembershipStatus.LEFT);
        membership.setReviewedAt(LocalDateTime.now());
        membershipRepository.save(membership);
        if (profile != null) {
            profile.setActive(false);
            profile.setEmployeeStatus(EmployeeStatus.LEFT);
            userAccountRepository.save(profile);
            auditService.log(profile, "EXIT_WORKSPACE", "workspace_membership", membership.getId(), workspace.getCode());
        }
        return summary(workspace, membership);
    }

    public List<WorkspaceDtos.MemberView> members(HttpServletRequest request, Long workspaceId) {
        authService.requireWorkspaceRole(request, workspaceId, Role.HR);
        return membershipRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .map(this::memberView)
                .toList();
    }

    public List<WorkspaceDtos.MemberView> joinRequests(HttpServletRequest request, Long workspaceId) {
        authService.requireWorkspaceRole(request, workspaceId, Role.HR);
        return membershipRepository.findByWorkspaceIdAndStatusOrderByCreatedAtAsc(
                        workspaceId,
                        MembershipStatus.PENDING
                ).stream()
                .map(this::memberView)
                .toList();
    }

    @Transactional
    public WorkspaceDtos.MemberView review(
            HttpServletRequest request,
            Long workspaceId,
            Long membershipId,
            WorkspaceDtos.ReviewRequest payload
    ) {
        PlatformAccount reviewer = authService.requireAccount(request);
        authService.requireWorkspaceRole(request, workspaceId, Role.HR);
        WorkspaceMembership membership = membershipRepository.findById(membershipId)
                .filter(item -> item.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> AppException.notFound("加入申请不存在"));
        if (membership.getStatus() != MembershipStatus.PENDING) {
            throw AppException.badRequest("该申请已经处理");
        }
        membership.setStatus(Boolean.TRUE.equals(payload.approved())
                ? MembershipStatus.PENDING_PROFILE
                : MembershipStatus.REJECTED);
        membership.setReviewedAt(LocalDateTime.now());
        membership.setReviewedByAccountId(reviewer.getId());
        return memberView(membershipRepository.save(membership));
    }

    @Transactional
    public WorkspaceDtos.MemberView updateRole(
            HttpServletRequest request,
            Long workspaceId,
            Long membershipId,
            WorkspaceDtos.RoleUpdateRequest payload
    ) {
        WorkspaceMembership actor = authService.requireWorkspaceRole(request, workspaceId, Role.HR);
        WorkspaceMembership membership = membershipRepository.findById(membershipId)
                .filter(item -> item.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> AppException.notFound("空间成员不存在"));
        if (membership.getAccountId().equals(actor.getAccountId()) && payload.role() != Role.HR) {
            throw AppException.badRequest("不能移除自己的空间管理员权限");
        }
        membership.setRole(payload.role());
        if (membership.getEmployeeProfileId() != null) {
            UserAccount profile = userAccountRepository.findById(membership.getEmployeeProfileId())
                    .orElseThrow(() -> AppException.notFound("员工档案不存在"));
            profile.setRole(payload.role());
            userAccountRepository.save(profile);
        }
        return memberView(membershipRepository.save(membership));
    }

    @Transactional
    public WorkspaceDtos.MemberView removeMember(
            HttpServletRequest request,
            Long workspaceId,
            Long membershipId
    ) {
        WorkspaceMembership actor = authService.requireWorkspaceRole(request, workspaceId, Role.HR);
        WorkspaceMembership membership = membershipRepository.findById(membershipId)
                .filter(item -> item.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> AppException.notFound("空间成员不存在"));
        if (membership.getAccountId().equals(actor.getAccountId())) {
            throw AppException.badRequest("不能移除自己的空间成员关系");
        }
        if (membership.getRole() == Role.HR
                && membership.getStatus() == MembershipStatus.ACTIVE
                && membershipRepository.countByWorkspaceIdAndRoleAndStatus(
                workspaceId, Role.HR, MembershipStatus.ACTIVE) <= 1) {
            throw AppException.badRequest("最后一名空间管理员不能被移除");
        }

        UserAccount profile = membership.getEmployeeProfileId() == null
                ? null
                : userAccountRepository.findById(membership.getEmployeeProfileId()).orElse(null);
        if (profile != null && membership.getRole() == Role.MANAGER
                && userAccountRepository.countByTenantIdAndManagerIdAndActiveTrue(workspaceId, profile.getId()) > 0) {
            throw AppException.badRequest("该主管仍有直属员工，请先调整直属关系");
        }
        membership.setStatus(MembershipStatus.DISABLED);
        membership.setReviewedAt(LocalDateTime.now());
        if (profile != null) {
            profile.setActive(false);
            profile.setEmployeeStatus(EmployeeStatus.LEFT);
            profile.setDingtalkUserId(null);
            profile.setDingtalkStaffId(null);
            profile.setDingtalkBindingCodeHash(null);
            profile.setDingtalkBindingCodeExpiresAt(null);
            userAccountRepository.save(profile);
        }
        WorkspaceMembership saved = membershipRepository.save(membership);
        UserAccount auditActor = actor.getEmployeeProfileId() == null
                ? profile
                : userAccountRepository.findById(actor.getEmployeeProfileId()).orElse(profile);
        if (auditActor != null) {
            auditService.log(auditActor, "REMOVE_WORKSPACE_MEMBER", "workspace_membership", membershipId, workspaceId.toString());
        }
        return memberView(saved);
    }

    private UserAccount createAdminProfile(Tenant workspace, PlatformAccount account) {
        UserAccount profile = new UserAccount();
        profile.setTenantId(workspace.getId());
        profile.setAccountId(account.getId());
        String baseUsername = account.getUsername().length() > 50
                ? account.getUsername().substring(0, 50)
                : account.getUsername();
        profile.setUsername(baseUsername + "." + workspace.getCode().toLowerCase());
        profile.setPasswordHash(account.getPasswordHash());
        profile.setEmployeeNo("ADMIN-" + account.getPublicId().substring(4));
        profile.setName(account.getName());
        profile.setRole(Role.HR);
        profile.setDepartment("Administration");
        profile.setTitle("Space Administrator");
        profile.setEmail(account.getEmail());
        profile.setEntryDate(LocalDate.now());
        profile.setEmployeeStatus(EmployeeStatus.ACTIVE);
        return userAccountRepository.save(profile);
    }

    private void seedAdminOrg(Long workspaceId) {
        if (departmentRepository.findByTenantIdAndName(workspaceId, "Administration").isEmpty()) {
            Department department = new Department();
            department.setTenantId(workspaceId);
            department.setName("Administration");
            department.setCode("ADMIN");
            department.setDescription("Workspace administration");
            departmentRepository.save(department);
        }
        if (jobTitleRepository.findByTenantIdAndName(workspaceId, "Space Administrator").isEmpty()) {
            JobTitle title = new JobTitle();
            title.setTenantId(workspaceId);
            title.setName("Space Administrator");
            title.setCode("SPACE-ADMIN");
            title.setDescription("Workspace owner and administrator");
            jobTitleRepository.save(title);
        }
    }

    private void seedDefaultBalances(Long workspaceId, Long employeeId) {
        for (LeaveType leaveType : LeaveType.values()) {
            LeaveBalance balance = new LeaveBalance();
            balance.setTenantId(workspaceId);
            balance.setEmployeeId(employeeId);
            balance.setLeaveType(leaveType);
            balance.setTotalDays(switch (leaveType) {
                case ANNUAL, SICK, MARRIAGE -> new BigDecimal("10");
                case PERSONAL -> new BigDecimal("5");
            });
            balance.setUsedDays(BigDecimal.ZERO);
            leaveBalanceRepository.save(balance);
        }
    }

    private WorkspaceDtos.MemberView memberView(WorkspaceMembership membership) {
        PlatformAccount account = accountRepository.findById(membership.getAccountId())
                .orElseThrow(() -> AppException.notFound("成员账号不存在"));
        UserAccount profile = membership.getEmployeeProfileId() == null
                ? null
                : userAccountRepository.findById(membership.getEmployeeProfileId()).orElse(null);
        return new WorkspaceDtos.MemberView(
                membership.getId(),
                account.getId(),
                account.getPublicId(),
                account.getUsername(),
                account.getName(),
                account.getEmail(),
                account.getAvatarUrl(),
                membership.getRole(),
                membership.getStatus(),
                membership.getEmployeeProfileId(),
                profile == null ? null : profile.getEmployeeNo(),
                profile == null ? null : profile.getDepartment(),
                profile == null ? null : profile.getTitle(),
                membership.getDraftEmployeeNo(),
                membership.getDraftPhone(),
                membership.getDraftDepartment(),
                membership.getDraftTitle(),
                membership.getDraftManagerEmployeeNo(),
                membership.getDraftEntryDate(),
                membership.getCreatedAt()
        );
    }

    private void applyProfileDraft(
            WorkspaceMembership membership,
            WorkspaceDtos.JoinWorkspaceRequest payload
    ) {
        membership.setDraftEmployeeNo(trimToNull(payload.employeeNo()));
        membership.setDraftPhone(trimToNull(payload.phone()));
        membership.setDraftDepartment(trimToNull(payload.department()));
        membership.setDraftTitle(trimToNull(payload.title()));
        membership.setDraftManagerEmployeeNo(trimToNull(payload.managerEmployeeNo()));
        membership.setDraftEntryDate(payload.entryDate());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private WorkspaceDtos.WorkspaceSummary summary(Tenant workspace, WorkspaceMembership membership) {
        return new WorkspaceDtos.WorkspaceSummary(
                workspace.getId(),
                workspace.getName(),
                workspace.getCode(),
                membership.getRole(),
                membership.getStatus(),
                membership.getEmployeeProfileId(),
                membershipRepository.countByWorkspaceIdAndStatus(workspace.getId(), MembershipStatus.ACTIVE)
        );
    }

    private String generateWorkspaceCode() {
        String code;
        do {
            code = "SPC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        } while (tenantRepository.findByCode(code).isPresent());
        return code;
    }
}
