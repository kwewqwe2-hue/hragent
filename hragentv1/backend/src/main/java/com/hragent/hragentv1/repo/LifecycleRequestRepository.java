package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.LifecycleRequest;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface LifecycleRequestRepository extends JpaRepository<LifecycleRequest,Long> {
 List<LifecycleRequest> findByTenantIdAndEmployeeIdOrderByUpdatedAtDesc(Long tenantId,Long employeeId);
 List<LifecycleRequest> findByTenantIdAndStatusNotOrderByUpdatedAtDesc(Long tenantId,String status);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 Optional<LifecycleRequest> findFirstByTenantIdAndEmployeeIdAndConversationIdOrderByIdDesc(Long tenantId,Long employeeId,String conversationId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 Optional<LifecycleRequest> findByIdAndTenantId(Long id,Long tenantId);
}
