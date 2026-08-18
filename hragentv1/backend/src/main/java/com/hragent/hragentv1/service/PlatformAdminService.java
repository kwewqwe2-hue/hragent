package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.PlatformAccount;
import com.hragent.hragentv1.domain.MembershipStatus;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.Tenant;
import com.hragent.hragentv1.dto.PlatformDtos;
import com.hragent.hragentv1.repo.AiCallRecordRepository;
import com.hragent.hragentv1.repo.ApiCallLogRepository;
import com.hragent.hragentv1.repo.AuditLogRepository;
import com.hragent.hragentv1.repo.PlatformAccountRepository;
import com.hragent.hragentv1.repo.TenantRepository;
import com.hragent.hragentv1.repo.WorkspaceMembershipRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class PlatformAdminService {
    private final AuthService authService;
    private final TenantRepository tenantRepository;
    private final PlatformAccountRepository accountRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final AiCallRecordRepository aiCallRecordRepository;
    private final ApiCallLogRepository apiCallLogRepository;
    private final AuditLogRepository auditLogRepository;

    public PlatformAdminService(
            AuthService authService,
            TenantRepository tenantRepository,
            PlatformAccountRepository accountRepository,
            WorkspaceMembershipRepository membershipRepository,
            AiCallRecordRepository aiCallRecordRepository,
            ApiCallLogRepository apiCallLogRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.authService = authService;
        this.tenantRepository = tenantRepository;
        this.accountRepository = accountRepository;
        this.membershipRepository = membershipRepository;
        this.aiCallRecordRepository = aiCallRecordRepository;
        this.apiCallLogRepository = apiCallLogRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<PlatformDtos.WorkspaceOverview> workspaces(HttpServletRequest request) {
        authService.requirePlatformAdmin(request);
        return tenantRepository.findAll().stream()
                .sorted(Comparator.comparing(Tenant::getCreatedAt).reversed())
                .map(this::workspaceOverview)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlatformDtos.WorkspaceDetail workspace(HttpServletRequest request, Long workspaceId) {
        authService.requirePlatformAdmin(request);
        Tenant workspace = tenantRepository.findById(workspaceId)
                .orElseThrow(() -> com.hragent.hragentv1.web.AppException.notFound("企业空间不存在"));
        long pendingCount = membershipRepository.countByWorkspaceIdAndStatus(workspaceId, MembershipStatus.PENDING)
                + membershipRepository.countByWorkspaceIdAndStatus(workspaceId, MembershipStatus.PENDING_PROFILE);
        return new PlatformDtos.WorkspaceDetail(
                workspaceOverview(workspace),
                membershipRepository.countByWorkspaceIdAndStatus(workspaceId, MembershipStatus.ACTIVE),
                pendingCount,
                membershipRepository.countByWorkspaceIdAndStatus(workspaceId, MembershipStatus.LEFT),
                membershipRepository.countByWorkspaceIdAndRoleAndStatus(
                        workspaceId, Role.EMPLOYEE, MembershipStatus.ACTIVE),
                membershipRepository.countByWorkspaceIdAndRoleAndStatus(
                        workspaceId, Role.MANAGER, MembershipStatus.ACTIVE),
                membershipRepository.countByWorkspaceIdAndRoleAndStatus(
                        workspaceId, Role.HR, MembershipStatus.ACTIVE),
                apiCallLogRepository.findTop100ByTenantIdOrderByCreatedAtDesc(workspaceId).stream()
                        .map(log -> new PlatformDtos.ApiUsageEntry(
                                log.getId(), log.getMethod(), log.getPath(), log.getStatusCode(), log.getCreatedAt()))
                        .toList(),
                auditLogRepository.findTop100ByTenantIdOrderByCreatedAtDesc(workspaceId).stream()
                        .map(log -> new PlatformDtos.OperationEntry(
                                log.getId(), log.getAction(), log.getTargetType(), log.getCreatedAt()))
                        .toList(),
                aiCallRecordRepository.findTop100ByTenantIdOrderByCreatedAtDesc(workspaceId).stream()
                        .map(call -> new PlatformDtos.AiUsageEntry(
                                call.getId(), call.getScenario(), call.getProvider(), call.isSuccess(), call.getCreatedAt()))
                        .toList()
        );
    }

    private PlatformDtos.WorkspaceOverview workspaceOverview(Tenant workspace) {
        PlatformAccount creator = workspace.getCreatedByAccountId() == null
                ? null
                : accountRepository.findById(workspace.getCreatedByAccountId()).orElse(null);
        return new PlatformDtos.WorkspaceOverview(
                workspace.getId(),
                workspace.getName(),
                workspace.getCode(),
                workspace.isActive(),
                creator == null ? null : creator.getPublicId(),
                creator == null ? null : creator.getName(),
                membershipRepository.countByWorkspaceIdAndStatus(workspace.getId(), MembershipStatus.ACTIVE),
                aiCallRecordRepository.countByTenantId(workspace.getId()),
                apiCallLogRepository.countByTenantId(workspace.getId()),
                workspace.getCreatedAt()
        );
    }
}
