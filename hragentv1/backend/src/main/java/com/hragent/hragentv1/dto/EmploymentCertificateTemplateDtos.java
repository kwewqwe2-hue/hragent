package com.hragent.hragentv1.dto;

import com.hragent.hragentv1.domain.CertificateLanguage;
import com.hragent.hragentv1.domain.CertificateTemplateReviewStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class EmploymentCertificateTemplateDtos {
    public record ActiveRequest(@NotNull Boolean active) {
    }

    public record TemplateView(
            Long id,
            String name,
            String destinationCountry,
            String consulateName,
            CertificateLanguage language,
            String languageLabel,
            String sourceFileName,
            long fileSize,
            boolean active,
            Long uploadedByEmployeeId,
            CertificateTemplateReviewStatus reviewStatus,
            String reviewStatusLabel,
            String reviewOpinion,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record TemplatePreview(
            String fileName,
            long fileSize,
            boolean readable,
            boolean hasPlaceholders,
            boolean canUpload,
            List<String> placeholders,
            List<String> unsupportedPlaceholders,
            List<String> warnings
    ) {
    }
}
