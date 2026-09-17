package com.hragent.hragentv1.dto;
import com.hragent.hragentv1.domain.PlatformAccessStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public final class PlatformAccessDtos {
 private PlatformAccessDtos(){}
 public record CreateRequest(Long employeeId, boolean platformApi, boolean agentApi, @Size(max=600) String reason){}
 public record ReviewRequest(boolean approved, @Size(max=600) String opinion){}
 public record View(Long id, Long employeeId, String employeeName, Long managerId, String managerName, boolean platformApi, boolean agentApi, PlatformAccessStatus status, String reason, String reviewOpinion, LocalDateTime createdAt, LocalDateTime reviewedAt){}
}
