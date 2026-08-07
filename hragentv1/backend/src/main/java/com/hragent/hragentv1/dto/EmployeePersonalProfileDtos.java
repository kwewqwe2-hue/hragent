package com.hragent.hragentv1.dto;

import com.hragent.hragentv1.domain.Role;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class EmployeePersonalProfileDtos {
    public record ProfileSummary(
            Long employeeId,
            String employeeNo,
            String displayName,
            String legalName,
            Role role,
            String department,
            String title,
            String employeeStatus,
            String employmentType,
            String workLocation,
            LocalDateTime updatedAt,
            boolean maintained
    ) {
    }

    public record ProfileView(
            Long employeeId,
            String employeeNo,
            String displayName,
            String legalName,
            String englishName,
            Role role,
            String department,
            String title,
            String email,
            String phone,
            LocalDate entryDate,
            String employeeStatus,
            String managerName,
            String gender,
            LocalDate birthDate,
            String nationality,
            String idType,
            String idNumber,
            String passportNumber,
            LocalDate passportExpiryDate,
            String employmentType,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            String workLocation,
            BigDecimal monthlySalary,
            String currency,
            String homeAddress,
            String emergencyContactName,
            String emergencyContactPhone,
            LocalDateTime updatedAt,
            boolean maintained
    ) {
    }

    public record ProfileUpdateRequest(
            @Size(max = 80) String legalName,
            @Size(max = 120) String englishName,
            @Size(max = 20) String gender,
            LocalDate birthDate,
            @Size(max = 80) String nationality,
            @Size(max = 40) String idType,
            @Size(max = 80) String idNumber,
            @Size(max = 80) String passportNumber,
            LocalDate passportExpiryDate,
            @Size(max = 40) String employmentType,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            @Size(max = 160) String workLocation,
            @DecimalMin("0") BigDecimal monthlySalary,
            @Size(max = 10) String currency,
            @Size(max = 240) String homeAddress,
            @Size(max = 80) String emergencyContactName,
            @Size(max = 40) String emergencyContactPhone
    ) {
    }
}
