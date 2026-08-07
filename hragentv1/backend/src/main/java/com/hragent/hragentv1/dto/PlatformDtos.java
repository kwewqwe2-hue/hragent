package com.hragent.hragentv1.dto;

import java.time.LocalDateTime;
import java.util.List;

public class PlatformDtos {
    public record WorkspaceOverview(
            Long workspaceId,
            String name,
            String code,
            boolean active,
            String creatorPublicId,
            String creatorName,
            long memberCount,
            long aiCallCount,
            long apiCallCount,
            LocalDateTime createdAt
    ) {
    }

    public record ApiUsageEntry(
            Long id,
            String method,
            String path,
            int statusCode,
            LocalDateTime createdAt
    ) {
    }

    public record OperationEntry(
            Long id,
            String action,
            String targetType,
            LocalDateTime createdAt
    ) {
    }

    public record AiUsageEntry(
            Long id,
            String scenario,
            String provider,
            boolean success,
            LocalDateTime createdAt
    ) {
    }

    public record WorkspaceDetail(
            WorkspaceOverview workspace,
            long activeMemberCount,
            long pendingMemberCount,
            long leftMemberCount,
            long employeeCount,
            long managerCount,
            long adminCount,
            List<ApiUsageEntry> apiCalls,
            List<OperationEntry> operations,
            List<AiUsageEntry> aiCalls
    ) {
    }
}
