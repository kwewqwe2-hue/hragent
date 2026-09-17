package com.hragent.hragentv1.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name="leave_medical_records")
public class LeaveMedicalRecord {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Version public Long version;
 @Column(nullable=false) public Long tenantId;
 @Column(nullable=false) public Long employeeId;
 public Long leaveRequestId;
 public LocalDate startDate;
 public LocalDate endDate;
 public String mediaType;
 public String extension;
 @Column(columnDefinition="LONGTEXT") public String encryptedFile;
 @Column(columnDefinition="LONGTEXT") public String encryptedText;
 public LocalDateTime createdAt=LocalDateTime.now();
}
