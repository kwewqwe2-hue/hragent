package com.hragent.hragentv1.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="lifecycle_requests", indexes={@Index(columnList="tenantId,employeeId"),@Index(columnList="tenantId,status")})
public class LifecycleRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Version public Long version;
 @Column(nullable=false) public Long tenantId;
 @Column(nullable=false) public Long employeeId;
 @Column(nullable=false,length=80) public String conversationId;
 @Column(nullable=false,length=40) public String kind;
 @Column(nullable=false,length=30) public String status="DRAFT";
 @Column(columnDefinition="LONGTEXT") public String encryptedFields;
 @Column(columnDefinition="TEXT") public String encryptedDateProposal;
 @Column(columnDefinition="TEXT") public String encryptedOpinion;
 @Column(columnDefinition="LONGTEXT") public String encryptedFile;
 public String fileName;
 @Column(columnDefinition="LONGTEXT") public String encryptedMaterials;
 public String materialsName;
 public Long reviewerId;
 public Long leaveRequestId;
 public Long medicalRecordId;
 public Long certificateRequestId;
 public Long certificateTemplateId;
 @Column(columnDefinition="boolean default false") public boolean certificateTemplateConfirmed;
 @Lob @Column(columnDefinition="TEXT") public String encryptedCertificateValues;
 public java.time.LocalDate dueDate;
 public LocalDateTime createdAt=LocalDateTime.now();
 public LocalDateTime updatedAt=LocalDateTime.now();
}
