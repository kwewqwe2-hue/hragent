package com.hragent.hragentv1.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "policy_monitor_candidates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_policy_candidate_tenant_source_hash",
                columnNames = {"tenant_id", "source_id", "content_hash"}
        )
)
public class PolicyMonitorCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "source_id", nullable = false, length = 120)
    private String sourceId;

    @Column(nullable = false, length = 160)
    private String sourceName;

    @Column(nullable = false, length = 320)
    private String sourceUrl;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false, length = 40)
    private String version;

    @Column(length = 80)
    private String region;

    private LocalDate publishedAt;
    private LocalDate effectiveAt;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    private LocalDateTime sourceUpdatedAt;

    @Column(nullable = false)
    private LocalDateTime detectedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PolicyReviewStatus reviewStatus = PolicyReviewStatus.PENDING_REVIEW;

    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;

    @Column(length = 600)
    private String reviewOpinion;

    private Long knowledgeArticleId;

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

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public LocalDate getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDate publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDate getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(LocalDate effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public LocalDateTime getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public void setSourceUpdatedAt(LocalDateTime sourceUpdatedAt) {
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }

    public PolicyReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(PolicyReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Long getReviewedByUserId() {
        return reviewedByUserId;
    }

    public void setReviewedByUserId(Long reviewedByUserId) {
        this.reviewedByUserId = reviewedByUserId;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewOpinion() {
        return reviewOpinion;
    }

    public void setReviewOpinion(String reviewOpinion) {
        this.reviewOpinion = reviewOpinion;
    }

    public Long getKnowledgeArticleId() {
        return knowledgeArticleId;
    }

    public void setKnowledgeArticleId(Long knowledgeArticleId) {
        this.knowledgeArticleId = knowledgeArticleId;
    }
}
