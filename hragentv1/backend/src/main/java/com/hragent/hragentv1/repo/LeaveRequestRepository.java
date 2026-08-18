package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.LeaveRequest;
import com.hragent.hragentv1.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByTenantIdAndEmployeeIdOrderBySubmittedAtDesc(Long tenantId, Long employeeId);

    List<LeaveRequest> findByTenantIdAndManagerIdAndStatusOrderBySubmittedAtDesc(
            Long tenantId,
            Long managerId,
            RequestStatus status
    );

    List<LeaveRequest> findByTenantIdAndStatusOrderBySubmittedAtDesc(Long tenantId, RequestStatus status);

    List<LeaveRequest> findByTenantIdOrderBySubmittedAtDesc(Long tenantId);

    long countByTenantIdAndStatus(Long tenantId, RequestStatus status);

    long countByTenantIdAndEmployeeIdAndStatus(Long tenantId, Long employeeId, RequestStatus status);

    long countByTenantIdAndManagerIdAndStatus(Long tenantId, Long managerId, RequestStatus status);
}
