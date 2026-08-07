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
}
