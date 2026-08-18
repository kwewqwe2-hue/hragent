package com.hragent.hragentv1.dto;

import com.hragent.hragentv1.domain.CertificateLanguage;
import com.hragent.hragentv1.domain.CertificateRequestStatus;
import com.hragent.hragentv1.domain.EmploymentCertificateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class EmploymentCertificateDtos {
    public record Option<T>(T value, String label) {
    }

    public record FormOptions(
            List<Option<EmploymentCertificateType>> certificateTypes,
            List<Option<CertificateLanguage>> languages
    ) {
    }

    public record CreateRequest(
            @NotNull EmploymentCertificateType certificateType,
            @NotNull CertificateLanguage language,
            @NotBlank @Size(max = 200) String purpose,
            @Size(max = 100) String destinationCountry,
            @Size(max = 160) String consulateName,
            boolean includeSalary,
            @Size(max = 600) String remarks,
            Long requestedTemplateId
    ) {
        public CreateRequest(
                EmploymentCertificateType certificateType,
                CertificateLanguage language,
                String purpose,
                String destinationCountry,
                String consulateName,
                boolean includeSalary,
                String remarks
        ) {
            this(
                    certificateType,
                    language,
                    purpose,
                    destinationCountry,
                    consulateName,
                    includeSalary,
                    remarks,
                    null
            );
        }
    }

    public record ReviewRequest(
            boolean approved,
            @Size(max = 600) String opinion
    ) {
    }

    public record RequestView(
            Long id,
            Long employeeId,
            String employeeNo,
            String employeeName,
            String department,
            String title,
            EmploymentCertificateType certificateType,
            String certificateTypeLabel,
            CertificateLanguage language,
            String languageLabel,
            String purpose,
            String destinationCountry,
            String consulateName,
            boolean includeSalary,
            String remarks,
            CertificateRequestStatus status,
            String statusLabel,
            String hrOpinion,
            LocalDateTime submittedAt,
            LocalDateTime reviewedAt,
            boolean profileReady,
            List<String> missingProfileFields,
            Long requestedTemplateId,
            String requestedTemplateFileName,
            String sourceTemplateFileName,
            String generatedFileName,
            String generationError,
            LocalDateTime generatedAt,
            boolean canCancel,
            boolean documentReady
    ) {
    }
}
