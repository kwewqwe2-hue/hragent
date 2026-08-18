package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.PolicyMonitorCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyMonitorCandidateRepository extends JpaRepository<PolicyMonitorCandidate, Long> {
    Optional<PolicyMonitorCandidate> findByTenantIdAndSourceIdAndContentHash(
            Long tenantId,
            String sourceId,
            String contentHash
    );

    List<PolicyMonitorCandidate> findByTenantIdOrderByDetectedAtDesc(Long tenantId);
}
