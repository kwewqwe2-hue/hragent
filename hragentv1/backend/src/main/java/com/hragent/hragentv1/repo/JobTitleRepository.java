package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.JobTitle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobTitleRepository extends JpaRepository<JobTitle, Long> {
    List<JobTitle> findByTenantIdOrderByNameAsc(Long tenantId);

    List<JobTitle> findByTenantIdAndActiveTrueOrderByNameAsc(Long tenantId);

    Optional<JobTitle> findByTenantIdAndName(Long tenantId, String name);
}
