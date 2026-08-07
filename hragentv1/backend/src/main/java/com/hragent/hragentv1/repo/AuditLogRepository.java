package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop100ByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
