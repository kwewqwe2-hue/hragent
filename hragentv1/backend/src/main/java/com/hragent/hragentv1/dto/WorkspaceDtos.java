package com.hragent.hragentv1.dto;

import com.hragent.hragentv1.domain.MembershipStatus;
import com.hragent.hragentv1.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class WorkspaceDtos {
    public record WorkspaceSummary(
            Long workspaceId,
            String name,
            String code,
            Role role,
            MembershipStatus status,
            Long employeeProfileId,
            long memberCount
    ) {
    }

    public record CreateWorkspaceRequest(
            @NotBlank @Size(max = 120) String name
    ) {
    }

    public record JoinWorkspaceRequest(
            @NotBlank @Size(max = 80) String workspaceCode,
            @Size(max = 80) String employeeNo,
            @Size(max = 40) String phone,
            @Size(max = 80) String department,
            @Size(max = 80) String title,
            @Size(max = 80) String managerEmployeeNo,
            LocalDate entryDate
    ) {
    }

    public record MemberView(
            Long membershipId,
            Long accountId,
            String publicId,
            String username,
            String name,
            String email,
            String avatarUrl,
            Role role,
            MembershipStatus status,
            Long employeeProfileId,
            String employeeNo,
            String department,
            String title,
            String draftEmployeeNo,
            String draftPhone,
            String draftDepartment,
            String draftTitle,
            String draftManagerEmployeeNo,
            LocalDate draftEntryDate,
            LocalDateTime createdAt
    ) {
    }

    public record ReviewRequest(
            @NotNull Boolean approved
    ) {
    }

    public record RoleUpdateRequest(
            @NotNull Role role
    ) {
    }
}
