package com.hragent.hragentv1.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "onboarding_requests",
        indexes = {
                @Index(name = "idx_onboarding_tenant_status", columnList = "tenantId,status"),
                @Index(name = "idx_onboarding_tenant_new_hire", columnList = "tenantId,newHireId")
        }
)
public class OnboardingRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long newHireId;

    @Column(nullable = false, length = 80)
    private String legalName;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(nullable = false, length = 120)
    private String personalEmail;

    @Column(nullable = false, length = 4)
    private String idNumberLast4;

    @Column(nullable = false)
    private LocalDate plannedEntryDate;

    @Column(nullable = false, length = 80)
    private String department;

    @Column(nullable = false, length = 80)
    private String positionTitle;

    @Column(length = 80)
    private String managerName;

    @Column(nullable = false, length = 120)
    private String workLocation;

    @Column(nullable = false, length = 80)
    private String emergencyContactName;

    @Column(nullable = false, length = 40)
    private String emergencyContactPhone;

    @Column(nullable = false, length = 120)
    private String bankName;

    @Column(nullable = false, length = 4)
    private String bankCardLast4;

    @Column(nullable = false, length = 80)
    private String highestEducation;

    @Column(nullable = false)
    private boolean idDocumentPrepared;

    @Column(nullable = false)
    private boolean bankCardPrepared;

    @Column(nullable = false)
    private boolean educationCertificatePrepared;

    @Column(nullable = false)
    private boolean photoPrepared;

    @Column(nullable = false)
    private boolean officeSuppliesReceived;

    @Column(length = 600)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OnboardingRequestStatus status = OnboardingRequestStatus.PENDING_HR;

    @Column(length = 600)
    private String hrOpinion;

    private Long reviewedByEmployeeId;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    private LocalDateTime reviewedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getNewHireId() { return newHireId; }
    public void setNewHireId(Long newHireId) { this.newHireId = newHireId; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPersonalEmail() { return personalEmail; }
    public void setPersonalEmail(String personalEmail) { this.personalEmail = personalEmail; }
    public String getIdNumberLast4() { return idNumberLast4; }
    public void setIdNumberLast4(String idNumberLast4) { this.idNumberLast4 = idNumberLast4; }
    public LocalDate getPlannedEntryDate() { return plannedEntryDate; }
    public void setPlannedEntryDate(LocalDate plannedEntryDate) { this.plannedEntryDate = plannedEntryDate; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPositionTitle() { return positionTitle; }
    public void setPositionTitle(String positionTitle) { this.positionTitle = positionTitle; }
    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getBankCardLast4() { return bankCardLast4; }
    public void setBankCardLast4(String bankCardLast4) { this.bankCardLast4 = bankCardLast4; }
    public String getHighestEducation() { return highestEducation; }
    public void setHighestEducation(String highestEducation) { this.highestEducation = highestEducation; }
    public boolean isIdDocumentPrepared() { return idDocumentPrepared; }
    public void setIdDocumentPrepared(boolean idDocumentPrepared) { this.idDocumentPrepared = idDocumentPrepared; }
    public boolean isBankCardPrepared() { return bankCardPrepared; }
    public void setBankCardPrepared(boolean bankCardPrepared) { this.bankCardPrepared = bankCardPrepared; }
    public boolean isEducationCertificatePrepared() { return educationCertificatePrepared; }
    public void setEducationCertificatePrepared(boolean educationCertificatePrepared) { this.educationCertificatePrepared = educationCertificatePrepared; }
    public boolean isPhotoPrepared() { return photoPrepared; }
    public void setPhotoPrepared(boolean photoPrepared) { this.photoPrepared = photoPrepared; }
    public boolean isOfficeSuppliesReceived() { return officeSuppliesReceived; }
    public void setOfficeSuppliesReceived(boolean officeSuppliesReceived) { this.officeSuppliesReceived = officeSuppliesReceived; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public OnboardingRequestStatus getStatus() { return status; }
    public void setStatus(OnboardingRequestStatus status) { this.status = status; }
    public String getHrOpinion() { return hrOpinion; }
    public void setHrOpinion(String hrOpinion) { this.hrOpinion = hrOpinion; }
    public Long getReviewedByEmployeeId() { return reviewedByEmployeeId; }
    public void setReviewedByEmployeeId(Long reviewedByEmployeeId) { this.reviewedByEmployeeId = reviewedByEmployeeId; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
