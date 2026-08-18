package com.hragent.hragentv1.dto;

import com.hragent.hragentv1.domain.PolicyReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DemoPolicyDtos {
    private DemoPolicyDtos() {
    }

    public record PolicyView(
            String sourceId,
            String sourceName,
            String sourceType,
            String title,
            String version,
            String region,
            LocalDate publishedAt,
            LocalDate effectiveAt,
            String summary,
            String content,
            String changeSummary,
            String contentHash,
            LocalDateTime sourceUpdatedAt,
            boolean updateAvailable,
            String disclaimer
    ) {
    }

    public record CandidateInput(
            @NotBlank @Size(max = 120) String sourceId,
            @NotBlank @Size(max = 160) String sourceName,
            @NotBlank @Size(max = 320) String sourceUrl,
            @NotBlank @Size(max = 240) String title,
            @NotBlank @Size(max = 40) String version,
            @Size(max = 80) String region,
            LocalDate publishedAt,
            LocalDate effectiveAt,
            String summary,
            @NotBlank String content,
            String changeSummary,
            @NotBlank @Size(min = 64, max = 64) String contentHash,
            LocalDateTime sourceUpdatedAt
    ) {
    }

    public record CandidateView(
            Long id,
            String sourceId,
            String sourceName,
            String sourceUrl,
            String title,
            String version,
            String region,
            LocalDate publishedAt,
            LocalDate effectiveAt,
            String summary,
            String content,
            String changeSummary,
            String contentHash,
            LocalDateTime sourceUpdatedAt,
            LocalDateTime detectedAt,
            PolicyReviewStatus reviewStatus,
            LocalDateTime reviewedAt,
            String reviewOpinion,
            Long knowledgeArticleId
    ) {
    }

    public record MonitorCheckResult(
            String monitorStatus,
            String previousVersion,
            CandidateView candidate
    ) {
    }

    public record ReviewRequest(
            @NotNull PolicyReviewStatus decision,
            @Size(max = 600) String opinion
    ) {
    }
}
