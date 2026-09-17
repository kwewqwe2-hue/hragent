package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.ErSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ErSettingsRepository extends JpaRepository<ErSettings, Long> {
    Optional<ErSettings> findByTenantId(Long tenantId);
}
