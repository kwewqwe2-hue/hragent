package com.hragent.hragentv1.domain;
import jakarta.persistence.*;
import java.time.*;
@Entity
@Table(name="er_cases")
public class ErCase {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false)
    private Long tenantId;
    
    private Long employeeId;
    
    private boolean anonymous;
    @Column(length=64, unique=true)
    private String receiptHash;
    @Column(nullable=false,length=40)
    private String kind;
    @Column(nullable=false,length=40)
    private String status;
    @Column(columnDefinition="LONGTEXT")
    private String encryptedBody;
    @Column(columnDefinition="LONGTEXT")
    private String encryptedReply;
    
    private Long assignedTo;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    @Version private Long version;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId=value; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long value) { employeeId=value; }
    public boolean getAnonymous() { return anonymous; }
    public void setAnonymous(boolean value) { anonymous=value; }
    public String getReceiptHash() { return receiptHash; }
    public void setReceiptHash(String value) { receiptHash=value; }
    public String getKind() { return kind; }
    public void setKind(String value) { kind=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    public String getEncryptedBody() { return encryptedBody; }
    public void setEncryptedBody(String value) { encryptedBody=value; }
    public String getEncryptedReply() { return encryptedReply; }
    public void setEncryptedReply(String value) { encryptedReply=value; }
    public Long getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Long value) { assignedTo=value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt=value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt=value; }
}
