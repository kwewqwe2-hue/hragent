package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.OnboardingRequest;
import com.hragent.hragentv1.domain.OnboardingRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OnboardingRequestRepository extends JpaRepository<OnboardingRequest, Long> {
    List<OnboardingRequest> findByTenantIdAndNewHireIdOrderBySubmittedAtDesc(Long tenantId, Long newHireId);

    List<OnboardingRequest> findByTenantIdOrderBySubmittedAtDesc(Long tenantId);

    List<OnboardingRequest> findByTenantIdAndStatusOrderBySubmittedAtDesc(
            Long tenantId,
            OnboardingRequestStatus status
    );

    Optional<OnboardingRequest> findFirstByTenantIdAndNewHireIdAndStatusOrderBySubmittedAtDesc(
            Long tenantId,
            Long newHireId,
            OnboardingRequestStatus status
    );

    Optional<OnboardingRequest> findByIdAndTenantId(Long id, Long tenantId);
}
