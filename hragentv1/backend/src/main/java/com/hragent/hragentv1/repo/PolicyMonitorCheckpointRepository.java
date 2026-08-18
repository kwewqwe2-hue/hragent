package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.PolicyMonitorCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyMonitorCheckpointRepository extends JpaRepository<PolicyMonitorCheckpoint, Long> {
    Optional<PolicyMonitorCheckpoint> findByTenantIdAndSourceId(Long tenantId, String sourceId);
}
