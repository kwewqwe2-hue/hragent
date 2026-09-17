package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.ErJourneyTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ErJourneyTaskRepository extends JpaRepository<ErJourneyTask, Long> {
    List<ErJourneyTask> findByTenantIdAndEmployeeId(Long tenantId, Long employeeId);
    Optional<ErJourneyTask> findByTenantIdAndEmployeeIdAndTaskKey(Long tenantId, Long employeeId, String taskKey);
}
