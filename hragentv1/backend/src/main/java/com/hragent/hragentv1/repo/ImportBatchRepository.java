package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    List<ImportBatch> findTop50ByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
