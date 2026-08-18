package com.hragent.hragentv1.dto;

import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.LeaveType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AdminDtos {
    public record EmployeeView(
            Long id,
            String employeeNo,
            String username,
            String accountPublicId,
            String name,
            Role role,
            String department,
            String title,
            String email,
            String phone,
            LocalDate entryDate,
            String employeeStatus,
            Long managerId,
            String managerName,
            boolean active
    ) {
    }

    public record EmployeeUpsertRequest(
            @NotBlank @Size(max = 80) String employeeNo,
            @Size(max = 24) String accountPublicId,
            @NotBlank @Size(max = 80) String name,
            Role role,
            @Size(max = 40) String phone,
            @Size(max = 120) String email,
            @NotBlank @Size(max = 80) String department,
            @NotBlank @Size(max = 80) String title,
            @Size(max = 80) String managerEmployeeNo,
            LocalDate entryDate,
            @Size(max = 30) String employeeStatus,
            Boolean active
    ) {
    }

    public record BalanceUpdateItem(
            @NotNull LeaveType leaveType,
            @NotNull @DecimalMin("0") BigDecimal totalDays,
            @NotNull @DecimalMin("0") BigDecimal usedDays
    ) {
    }

    public record EmployeeBalanceUpdateRequest(
            @NotNull @Size(min = 1) List<@Valid BalanceUpdateItem> balances
    ) {
    }

    public record BasicConfigRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 80) String code,
            @Size(max = 240) String description,
            Boolean active
    ) {
    }

    public record DepartmentView(
            Long id,
            String name,
            String code,
            String description,
            boolean active,
            LocalDateTime createdAt
    ) {
    }

    public record JobTitleView(
            Long id,
            String name,
            String code,
            String description,
            boolean active,
            LocalDateTime createdAt
    ) {
    }

    public record ImportRowView(
            int rowNumber,
            boolean valid,
            String action,
            String message,
            List<String> values
    ) {
    }

    public record ImportResult(
            String importType,
            boolean committed,
            int totalRows,
            int validRows,
            int failedRows,
            List<ImportRowView> rows
    ) {
    }

    public record ImportBatchView(
            Long id,
            String importType,
            String fileName,
            int totalRows,
            int successRows,
            int failedRows,
            String status,
            String message,
            LocalDateTime createdAt
    ) {
    }

    public record ApiKeyCreateRequest(
            @NotBlank @Size(max = 120) String name
    ) {
    }

    public record ApiKeyCreateResponse(
            Long id,
            String name,
            String apiKey,
            String keyPrefix
    ) {
    }

    public record ApiKeyView(
            Long id,
            String name,
            String keyPrefix,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime lastUsedAt
    ) {
    }

    public record ApiCallLogView(
            Long id,
            Long apiKeyId,
            String method,
            String path,
            int statusCode,
            String message,
            LocalDateTime createdAt
    ) {
    }

    public record AiConfigView(
            String provider,
            String baseUrl,
            String model,
            boolean enabled,
            boolean apiKeyConfigured,
            String maskedApiKey,
            String credentialSource,
            LocalDateTime updatedAt
    ) {
    }

    public record AiConfigUpdateRequest(
            @NotBlank @Size(max = 240) String baseUrl,
            @NotBlank @Size(max = 120) String model,
            @Size(max = 500) String apiKey,
            Boolean enabled
    ) {
    }

    public record AiConfigTestResult(
            boolean success,
            String message,
            String provider,
            String model,
            long latencyMs
    ) {
    }

    public record KnowledgeUpsertRequest(
            @NotBlank @Size(max = 80) String category,
            @NotBlank @Size(max = 160) String title,
            @NotBlank String content,
            @Size(max = 240) String source,
            @Size(max = 80) String region,
            LocalDate publishedAt,
            LocalDate updatedAt,
            @Size(max = 40) String reviewStatus
    ) {
    }
}
