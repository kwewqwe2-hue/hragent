package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.AiCallRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiCallRecordRepository extends JpaRepository<AiCallRecord, Long> {
    List<AiCallRecord> findTop100ByTenantIdOrderByCreatedAtDesc(Long tenantId);

    long countByTenantId(Long tenantId);
}
