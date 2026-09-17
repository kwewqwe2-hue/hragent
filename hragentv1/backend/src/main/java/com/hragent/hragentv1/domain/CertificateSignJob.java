package com.hragent.hragentv1.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="certificate_sign_jobs")
public class CertificateSignJob {
 @Id public Long certificateId;
 @Version public Long version;
 public Long tenantId;
 @Column(length=30) public String status="READY";
 public String resourceId;
 public String taskId;
 public String flowId;
 public int failures;
 @Column(length=300) public String error;
 @Lob @Column(columnDefinition="TEXT") public String encryptedConfig;
 public LocalDateTime updatedAt=LocalDateTime.now();
}
