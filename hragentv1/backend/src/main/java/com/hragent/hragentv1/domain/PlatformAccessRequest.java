package com.hragent.hragentv1.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "platform_access_requests", indexes = @Index(name = "idx_platform_access_employee", columnList = "tenantId,employeeId,status"))
public class PlatformAccessRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private Long managerId;
    @Column(nullable = false) private Long employeeId;
    @Column(nullable = false) private boolean platformApi;
    @Column(nullable = false) private boolean agentApi;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PlatformAccessStatus status = PlatformAccessStatus.PENDING_MANAGER;
    @Column(length = 600) private String reason;
    @Column(length = 600) private String reviewOpinion;
    private Long reviewedById;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime reviewedAt;
    public Long getId(){return id;} public Long getTenantId(){return tenantId;} public void setTenantId(Long v){tenantId=v;} public Long getManagerId(){return managerId;} public void setManagerId(Long v){managerId=v;} public Long getEmployeeId(){return employeeId;} public void setEmployeeId(Long v){employeeId=v;} public boolean isPlatformApi(){return platformApi;} public void setPlatformApi(boolean v){platformApi=v;} public boolean isAgentApi(){return agentApi;} public void setAgentApi(boolean v){agentApi=v;} public PlatformAccessStatus getStatus(){return status;} public void setStatus(PlatformAccessStatus v){status=v;} public String getReason(){return reason;} public void setReason(String v){reason=v;} public String getReviewOpinion(){return reviewOpinion;} public void setReviewOpinion(String v){reviewOpinion=v;} public Long getReviewedById(){return reviewedById;} public void setReviewedById(Long v){reviewedById=v;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getReviewedAt(){return reviewedAt;} public void setReviewedAt(LocalDateTime v){reviewedAt=v;}
}
