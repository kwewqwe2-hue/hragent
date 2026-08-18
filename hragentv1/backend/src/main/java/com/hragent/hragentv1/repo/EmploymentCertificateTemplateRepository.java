package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.CertificateLanguage;
import com.hragent.hragentv1.domain.EmploymentCertificateTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmploymentCertificateTemplateRepository
        extends JpaRepository<EmploymentCertificateTemplate, Long> {
    List<EmploymentCertificateTemplate> findByTenantIdOrderByUpdatedAtDesc(Long tenantId);

    Optional<EmploymentCertificateTemplate> findByIdAndTenantId(Long id, Long tenantId);

    Optional<EmploymentCertificateTemplate>
    findFirstByTenantIdAndDestinationCountryIgnoreCaseAndConsulateNameIgnoreCaseAndLanguageAndActiveTrueOrderByUpdatedAtDesc(
            Long tenantId,
            String destinationCountry,
            String consulateName,
            CertificateLanguage language
    );
}
