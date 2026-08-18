package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.ApiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiCallLogRepository extends JpaRepository<ApiCallLog, Long> {
    List<ApiCallLog> findTop100ByTenantIdOrderByCreatedAtDesc(Long tenantId);

    long countByTenantId(Long tenantId);
}
