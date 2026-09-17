package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.EmployeeServiceTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface EmployeeServiceTaskRepository extends JpaRepository<EmployeeServiceTask, Long> {
    List<EmployeeServiceTask> findByTenantIdAndEmployeeIdOrderByDueDateAsc(Long tenantId, Long employeeId);
    Optional<EmployeeServiceTask> findByIdAndTenantIdAndEmployeeId(Long id, Long tenantId, Long employeeId);
}
