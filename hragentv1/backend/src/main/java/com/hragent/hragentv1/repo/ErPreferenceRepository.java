package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.ErPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ErPreferenceRepository extends JpaRepository<ErPreference, Long> {
    Optional<ErPreference> findByTenantIdAndEmployeeId(Long tenantId, Long employeeId);
}
