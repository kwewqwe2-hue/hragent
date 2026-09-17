package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.ErSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ErSignalRepository extends JpaRepository<ErSignal, Long> {
    List<ErSignal> findByTenantIdAndWeekGreaterThanEqual(Long tenantId, java.time.LocalDate week);
    Optional<ErSignal> findByTenantIdAndParticipantAndTopicAndWeek(Long tenantId, String participant, String topic, java.time.LocalDate week);
    void deleteByTenantIdAndParticipant(Long tenantId, String participant);
    void deleteByWeekBefore(java.time.LocalDate week);
}
