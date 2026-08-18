package com.hragent.hragentv1.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "policy_monitor_checkpoints",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_policy_checkpoint_tenant_source",
                columnNames = {"tenant_id", "source_id"}
        )
)
public class PolicyMonitorCheckpoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "source_id", nullable = false, length = 120)
    private String sourceId;

    @Column(nullable = false, length = 40)
    private String lastVersion;

    @Column(nullable = false, length = 64)
    private String lastContentHash;

    @Column(nullable = false)
    private LocalDateTime lastCheckedAt = LocalDateTime.now();

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

    public String getLastVersion() {
        return lastVersion;
    }

    public void setLastVersion(String lastVersion) {
        this.lastVersion = lastVersion;
    }

    public String getLastContentHash() {
        return lastContentHash;
    }

    public void setLastContentHash(String lastContentHash) {
        this.lastContentHash = lastContentHash;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }
}
