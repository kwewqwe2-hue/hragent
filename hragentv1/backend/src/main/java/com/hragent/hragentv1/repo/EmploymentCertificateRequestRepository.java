package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.CertificateRequestStatus;
import com.hragent.hragentv1.domain.EmploymentCertificateRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmploymentCertificateRequestRepository extends JpaRepository<EmploymentCertificateRequest, Long> {
    List<EmploymentCertificateRequest> findByTenantIdAndEmployeeIdOrderBySubmittedAtDesc(Long tenantId, Long employeeId);

    List<EmploymentCertificateRequest> findByTenantIdOrderBySubmittedAtDesc(Long tenantId);

    List<EmploymentCertificateRequest> findByTenantIdAndStatusOrderBySubmittedAtDesc(
            Long tenantId,
            CertificateRequestStatus status
    );

    Optional<EmploymentCertificateRequest> findByIdAndTenantId(Long id, Long tenantId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from EmploymentCertificateRequest r where r.id=:id and r.tenantId=:tenantId")
    Optional<EmploymentCertificateRequest> lockForUpdate(@org.springframework.data.repository.query.Param("id") Long id,@org.springframework.data.repository.query.Param("tenantId") Long tenantId);
}
