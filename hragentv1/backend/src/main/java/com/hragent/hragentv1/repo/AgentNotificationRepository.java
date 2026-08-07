package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.AgentNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentNotificationRepository extends JpaRepository<AgentNotification, Long> {
    Optional<AgentNotification> findByDedupKey(String dedupKey);

    List<AgentNotification> findByTenantIdAndDeliveredAtIsNullOrderByCreatedAtAsc(
            Long tenantId,
            Pageable pageable
    );

    List<AgentNotification> findByTenantIdAndBusinessIdAndEventTypeAndDeliveredAtIsNull(
            Long tenantId,
            Long businessId,
            String eventType
    );
}
