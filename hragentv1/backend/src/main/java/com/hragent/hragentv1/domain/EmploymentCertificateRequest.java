package com.hragent.hragentv1.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "employment_certificate_requests",
        indexes = {
                @Index(name = "idx_certificate_tenant_employee", columnList = "tenantId,employeeId"),
                @Index(name = "idx_certificate_tenant_status", columnList = "tenantId,status")
        }
)
public class EmploymentCertificateRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EmploymentCertificateType certificateType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CertificateLanguage language;

    @Column(nullable = false, length = 200)
    private String purpose;

    @Column(length = 100)
    private String destinationCountry;

    @Column(length = 160)
    private String consulateName;

    @Column(nullable = false)
    private boolean includeSalary;

    @Column(length = 600)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CertificateRequestStatus status = CertificateRequestStatus.PENDING_HR;

    @Column(length = 600)
    private String hrOpinion;

    private Long reviewedByEmployeeId;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    private LocalDateTime reviewedAt;

    private Long requestedTemplateId;

    @Column(length = 240)
    private String requestedTemplateFileName;

    @Column(length = 240)
    private String sourceTemplateFileName;

    @Column(length = 500)
    private String sourceTemplateStorageKey;

    @Column(length = 240)
    private String generatedFileName;

    @Column(length = 500)
    private String generatedFileStorageKey;

    @Column(length = 1000)
    private String generationError;

    private LocalDateTime generatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public EmploymentCertificateType getCertificateType() {
        return certificateType;
    }

    public void setCertificateType(EmploymentCertificateType certificateType) {
        this.certificateType = certificateType;
    }

    public CertificateLanguage getLanguage() {
        return language;
    }

    public void setLanguage(CertificateLanguage language) {
        this.language = language;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getDestinationCountry() {
        return destinationCountry;
    }

    public void setDestinationCountry(String destinationCountry) {
        this.destinationCountry = destinationCountry;
    }

    public String getConsulateName() {
        return consulateName;
    }

    public void setConsulateName(String consulateName) {
        this.consulateName = consulateName;
    }

    public boolean isIncludeSalary() {
        return includeSalary;
    }

    public void setIncludeSalary(boolean includeSalary) {
        this.includeSalary = includeSalary;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public CertificateRequestStatus getStatus() {
        return status;
    }

    public void setStatus(CertificateRequestStatus status) {
        this.status = status;
    }

    public String getHrOpinion() {
        return hrOpinion;
    }

    public void setHrOpinion(String hrOpinion) {
        this.hrOpinion = hrOpinion;
    }

    public Long getReviewedByEmployeeId() {
        return reviewedByEmployeeId;
    }

    public void setReviewedByEmployeeId(Long reviewedByEmployeeId) {
        this.reviewedByEmployeeId = reviewedByEmployeeId;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Long getRequestedTemplateId() {
        return requestedTemplateId;
    }

    public void setRequestedTemplateId(Long requestedTemplateId) {
        this.requestedTemplateId = requestedTemplateId;
    }

    public String getRequestedTemplateFileName() {
        return requestedTemplateFileName;
    }

    public void setRequestedTemplateFileName(String requestedTemplateFileName) {
        this.requestedTemplateFileName = requestedTemplateFileName;
    }

    public String getSourceTemplateFileName() {
        return sourceTemplateFileName;
    }

    public void setSourceTemplateFileName(String sourceTemplateFileName) {
        this.sourceTemplateFileName = sourceTemplateFileName;
    }

    public String getSourceTemplateStorageKey() {
        return sourceTemplateStorageKey;
    }

    public void setSourceTemplateStorageKey(String sourceTemplateStorageKey) {
        this.sourceTemplateStorageKey = sourceTemplateStorageKey;
    }

    public String getGeneratedFileName() {
        return generatedFileName;
    }

    public void setGeneratedFileName(String generatedFileName) {
        this.generatedFileName = generatedFileName;
    }

    public String getGeneratedFileStorageKey() {
        return generatedFileStorageKey;
    }

    public void setGeneratedFileStorageKey(String generatedFileStorageKey) {
        this.generatedFileStorageKey = generatedFileStorageKey;
    }

    public String getGenerationError() {
        return generationError;
    }

    public void setGenerationError(String generationError) {
        this.generationError = generationError;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
