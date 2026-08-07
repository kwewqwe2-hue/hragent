package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.IntegrationApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationApiKeyRepository extends JpaRepository<IntegrationApiKey, Long> {
    List<IntegrationApiKey> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<IntegrationApiKey> findByKeyPrefixAndActiveTrue(String keyPrefix);
}
