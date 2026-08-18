package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.MembershipStatus;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.WorkspaceMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, Long> {
    List<WorkspaceMembership> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    Optional<WorkspaceMembership> findByAccountIdAndWorkspaceId(Long accountId, Long workspaceId);

    List<WorkspaceMembership> findByWorkspaceIdOrderByCreatedAtAsc(Long workspaceId);

    List<WorkspaceMembership> findByWorkspaceIdAndStatusOrderByCreatedAtAsc(
            Long workspaceId,
            MembershipStatus status
    );

    long countByWorkspaceId(Long workspaceId);

    long countByWorkspaceIdAndStatus(Long workspaceId, MembershipStatus status);

    long countByWorkspaceIdAndRoleAndStatus(Long workspaceId, Role role, MembershipStatus status);
}
