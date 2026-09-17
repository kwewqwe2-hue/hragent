package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.LeaveRequest;
import com.hragent.hragentv1.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id and r.tenantId = :tenantId")
    Optional<LeaveRequest> lockForReview(@Param("id") Long id, @Param("tenantId") Long tenantId);

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
