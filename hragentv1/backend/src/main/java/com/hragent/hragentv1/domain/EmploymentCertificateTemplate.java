package com.hragent.hragentv1.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "employment_certificate_templates",
        indexes = {
                @Index(name = "idx_certificate_template_tenant_active", columnList = "tenantId,active"),
                @Index(name = "idx_certificate_template_match", columnList = "tenantId,destinationCountry,language")
        }
)
public class EmploymentCertificateTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 100)
    private String destinationCountry;

    @Column(nullable = false, length = 160)
    private String consulateName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CertificateLanguage language;

    @Column(nullable = false, length = 240)
    private String sourceFileName;

    @Column(nullable = false, length = 500)
    private String storageKey;

    @Column(nullable = false, length = 160)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Long uploadedByEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CertificateTemplateReviewStatus reviewStatus;

    @Column(length = 600)
    private String reviewOpinion;

    private Long reviewedByEmployeeId;

    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public CertificateLanguage getLanguage() {
        return language;
    }

    public void setLanguage(CertificateLanguage language) {
        this.language = language;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getUploadedByEmployeeId() {
        return uploadedByEmployeeId;
    }

    public void setUploadedByEmployeeId(Long uploadedByEmployeeId) {
        this.uploadedByEmployeeId = uploadedByEmployeeId;
    }

    public CertificateTemplateReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(CertificateTemplateReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewOpinion() {
        return reviewOpinion;
    }

    public void setReviewOpinion(String reviewOpinion) {
        this.reviewOpinion = reviewOpinion;
    }

    public Long getReviewedByEmployeeId() {
        return reviewedByEmployeeId;
    }

    public void setReviewedByEmployeeId(Long reviewedByEmployeeId) {
        this.reviewedByEmployeeId = reviewedByEmployeeId;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
