package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByCode(String code);

    Optional<Tenant> findByCodeIgnoreCase(String code);
}
