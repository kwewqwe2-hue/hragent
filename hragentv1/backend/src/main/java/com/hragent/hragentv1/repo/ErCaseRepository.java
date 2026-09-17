package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.ErCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ErCaseRepository extends JpaRepository<ErCase, Long> {
    List<ErCase> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<ErCase> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(Long tenantId, Long employeeId);
    Optional<ErCase> findByTenantIdAndReceiptHash(Long tenantId, String receiptHash);
    Optional<ErCase> findByIdAndTenantId(Long id, Long tenantId);
}
