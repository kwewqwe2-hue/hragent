package com.hragent.hragentv1.dto;

import com.hragent.hragentv1.domain.*;

public record UserProfile(
        Long id,
        String publicId,
        String username,
        String name,
        String email,
        String avatarUrl,
        boolean platformAdmin,
        Long tenantId,
        String workspaceName,
        String workspaceCode,
        MembershipStatus membershipStatus,
        Long employeeProfileId,
        String employeeNo,
        Role role,
        String department,
        String title,
        Long managerId
) {
    public static UserProfile from(
            PlatformAccount account,
            WorkspaceMembership membership,
            UserAccount employee,
            Tenant workspace
    ) {
        return new UserProfile(
                account.getId(),
                account.getPublicId(),
                account.getUsername(),
                account.getName(),
                account.getEmail(),
                account.getAvatarUrl(),
                account.isPlatformAdmin(),
                workspace == null ? null : workspace.getId(),
                workspace == null ? null : workspace.getName(),
                workspace == null ? null : workspace.getCode(),
                membership == null ? null : membership.getStatus(),
                membership == null ? null : membership.getEmployeeProfileId(),
                employee == null ? null : employee.getEmployeeNo(),
                membership == null ? null : membership.getRole(),
                employee == null ? null : employee.getDepartment(),
                employee == null ? null : employee.getTitle(),
                employee == null ? null : employee.getManagerId()
        );
    }
}
