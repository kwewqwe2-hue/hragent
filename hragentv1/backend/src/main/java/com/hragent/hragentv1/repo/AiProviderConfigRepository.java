package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.AiProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, Long> {
    Optional<AiProviderConfig> findByTenantId(Long tenantId);
}
