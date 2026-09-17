package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.ErKnowledgeGap;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ErKnowledgeGapRepository extends JpaRepository<ErKnowledgeGap, Long> {
    List<ErKnowledgeGap> findByTenantIdOrderByUpdatedAtDesc(Long tenantId);
    Optional<ErKnowledgeGap> findByTenantIdAndTopic(Long tenantId, String topic);
    Optional<ErKnowledgeGap> findByIdAndTenantId(Long id, Long tenantId);
}
