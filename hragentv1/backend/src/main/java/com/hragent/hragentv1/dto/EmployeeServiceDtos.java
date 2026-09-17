package com.hragent.hragentv1.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class EmployeeServiceDtos {
    private EmployeeServiceDtos() { }
    public record ProfileRequest(
            @Size(max=80) String location, @Size(max=80) String jobGrade,
            @Size(max=80) String workType, @Size(max=160) String legalEntity,
            LocalDate probationEndDate, LocalDate medicalCheckDeadline, LocalDate annualLeaveExpiresAt,
            @Size(max=1000) String medicalBookingUrl) { }
    public record ProfileView(Long employeeId, String employeeNo, String name, String location,
            String jobGrade, String workType, String legalEntity, LocalDate probationEndDate,
            LocalDate contractEndDate, LocalDate medicalCheckDeadline, LocalDate annualLeaveExpiresAt,
            String medicalBookingUrl, List<String> missingAttributes) { }
    public record Question(@NotBlank @Size(max=1000) String message) { }
    public record Citation(Long id, String title, String excerpt, String source, String sourceUrl,
            LocalDate publishedAt, LocalDate effectiveFrom, LocalDate effectiveTo,
            String region, String jobGrades, String workTypes, String legalEntities) { }
    public record PolicyAnswer(String answer, String status, ProfileView profile,
            List<Citation> citations, List<String> gaps) { }
    public record TaskAction(@NotBlank @Pattern(regexp="DONE|OPEN|SNOOZE") String action,
            LocalDate until) { }
    public record ChecklistRequest(@NotBlank @Pattern(regexp="SOCIAL_SECURITY|HOUSING_FUND|SETTLEMENT") String kind,
            @Size(max=80) String origin, @NotBlank @Size(max=80) String destination) { }
    public record Checklist(String title, String status, String origin, String destination,
            List<String> materials, List<String> steps, List<Citation> citations, String markdown) { }
}
