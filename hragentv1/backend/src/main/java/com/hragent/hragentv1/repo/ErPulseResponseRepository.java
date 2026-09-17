package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.ErPulseResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ErPulseResponseRepository extends JpaRepository<ErPulseResponse,Long> {
    boolean existsByTenantIdAndPeriodAndParticipant(Long tenantId,String period,String participant);
    List<ErPulseResponse> findByTenantIdAndPeriod(Long tenantId,String period);
}
