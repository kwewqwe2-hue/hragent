package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.EmployeePersonalProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeePersonalProfileRepository extends JpaRepository<EmployeePersonalProfile, Long> {
    Optional<EmployeePersonalProfile> findByTenantIdAndEmployeeId(Long tenantId, Long employeeId);

    List<EmployeePersonalProfile> findByTenantIdOrderByEmployeeIdAsc(Long tenantId);
}
