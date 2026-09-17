package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.EmployeeServiceProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface EmployeeServiceProfileRepository extends JpaRepository<EmployeeServiceProfile, Long> {
    Optional<EmployeeServiceProfile> findByTenantIdAndEmployeeId(Long tenantId, Long employeeId);
}
