package com.hragent.hragentv1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class AuthDtos {
    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record LoginResponse(
            String token,
            UserProfile user,
            List<WorkspaceDtos.WorkspaceSummary> workspaces
    ) {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 80) String username,
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 6, max = 72) String password
    ) {
    }

    public record ProfileUpdateRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Email @Size(max = 160) String email,
            @Size(max = 500) String avatarUrl
    ) {
    }

    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 6, max = 72) String newPassword
    ) {
    }
}
