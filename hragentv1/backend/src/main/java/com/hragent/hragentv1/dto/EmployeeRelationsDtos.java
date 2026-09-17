package com.hragent.hragentv1.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class EmployeeRelationsDtos {
    public record SettingsInput(@Size(max=160) String eapName, @Size(max=80) String eapPhone,
            @Size(max=1000) String eapUrl, @Size(max=500) String erContact,
            @NotNull @Size(max=30) List<Long> investigatorIds) { }
    public record Resources(String eapName, String eapPhone, String eapUrl, String erContact,
            boolean routingReady, boolean investigator, boolean analyticsEnabled) { }
    public record CaseInput(@NotBlank @Pattern(regexp="COMPLAINT|CONSULT|EXIT_INTERVIEW") String kind,
            boolean anonymous, @NotBlank @Size(max=4000) String facts, @Size(max=160) String occurredAt,
            @Size(max=240) String location, @Size(max=2000) String evidence,
            @Size(max=1000) String expectation, @AssertTrue(message="请确认已阅读隐私说明并同意提交") boolean consent) { }
    public record CaseView(Long id, String kind, String status, boolean anonymous, String employee,
            String facts, String reply, Long assignedTo, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record Receipt(CaseView caseInfo, String receipt, String message) { }
    public record Lookup(@NotBlank @Size(min=32,max=100) String receipt) { }
    public record CaseAction(@Pattern(regexp="IN_PROGRESS|RESOLVED") @NotBlank String status,
            @NotBlank @Size(max=3000) String reply) { }
    public record Followup(@NotBlank @Size(max=2000) String message, @Size(max=100) String receipt) { }
    public record Toggle(boolean enabled) { }
    public record JourneyTask(String key, String phase, String title, String detail, boolean done, LocalDate dueDate) { }
    public record Feedback(@NotBlank @Size(max=1000) String question) { }
    public record GapAction(@NotBlank @Pattern(regexp="OPEN|RESOLVED") String status) { }
    public record TopicMetric(String department, String topic, long participants, long previousParticipants, String trend) { }
    public record Insights(List<TopicMetric> topics, int minimumGroupSize, LocalDate since, String privacyNote) { }
}
