package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.PlatformAccessRequest;
import com.hragent.hragentv1.domain.PlatformAccessStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PlatformAccessRequestRepository extends JpaRepository<PlatformAccessRequest, Long> {
 List<PlatformAccessRequest> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(Long tenantId, Long employeeId);
 List<PlatformAccessRequest> findByTenantIdAndManagerIdOrderByCreatedAtDesc(Long tenantId, Long managerId);
 List<PlatformAccessRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(Long tenantId, PlatformAccessStatus status);
 List<PlatformAccessRequest> findByTenantIdAndStatusInOrderByCreatedAtDesc(Long tenantId, Collection<PlatformAccessStatus> statuses);
 Optional<PlatformAccessRequest> findByIdAndTenantId(Long id, Long tenantId);
}
