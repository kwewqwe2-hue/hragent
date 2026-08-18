package com.hragent.hragentv1.dto;

import com.hragent.hragentv1.domain.OnboardingRequestStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class OnboardingDtos {
    private OnboardingDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 80) String legalName,
            @NotBlank @Size(max = 40) String phone,
            @NotBlank @Email @Size(max = 120) String personalEmail,
            @NotBlank @Pattern(regexp = "^[0-9A-Za-z]{4}$", message = "证件号码后四位必须是 4 位数字或字母")
            String idNumberLast4,
            @NotNull LocalDate plannedEntryDate,
            @NotBlank @Size(max = 80) String department,
            @NotBlank @Size(max = 80) String positionTitle,
            @Size(max = 80) String managerName,
            @NotBlank @Size(max = 120) String workLocation,
            @NotBlank @Size(max = 80) String emergencyContactName,
            @NotBlank @Size(max = 40) String emergencyContactPhone,
            @NotBlank @Size(max = 120) String bankName,
            @NotBlank @Pattern(regexp = "^\\d{4}$", message = "银行卡后四位必须是 4 位数字")
            String bankCardLast4,
            @NotBlank @Size(max = 80) String highestEducation,
            boolean idDocumentPrepared,
            boolean bankCardPrepared,
            boolean educationCertificatePrepared,
            boolean photoPrepared,
            @Size(max = 600) String remarks
    ) {
    }

    public record ReviewRequest(
            @NotNull Boolean approved,
            @Size(max = 600) String opinion
    ) {
    }

    public record RequestView(
            Long id,
            Long newHireId,
            String employeeNo,
            String accountName,
            String legalName,
            String phone,
            String personalEmail,
            String idNumberLast4,
            LocalDate plannedEntryDate,
            String department,
            String positionTitle,
            String managerName,
            String workLocation,
            String emergencyContactName,
            String emergencyContactPhone,
            String bankName,
            String bankCardLast4,
            String highestEducation,
            boolean idDocumentPrepared,
            boolean bankCardPrepared,
            boolean educationCertificatePrepared,
            boolean photoPrepared,
            boolean officeSuppliesReceived,
            String remarks,
            OnboardingRequestStatus status,
            String statusLabel,
            String hrOpinion,
            LocalDateTime submittedAt,
            LocalDateTime reviewedAt
    ) {
    }
}
