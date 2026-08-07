package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByTenantIdOrderByNameAsc(Long tenantId);

    List<Department> findByTenantIdAndActiveTrueOrderByNameAsc(Long tenantId);

    Optional<Department> findByTenantIdAndName(Long tenantId, String name);
}
