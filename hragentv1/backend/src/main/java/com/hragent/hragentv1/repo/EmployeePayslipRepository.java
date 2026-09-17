package com.hragent.hragentv1.repo;
import com.hragent.hragentv1.domain.EmployeePayslip;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EmployeePayslipRepository extends JpaRepository<EmployeePayslip,Long> {
 Optional<EmployeePayslip> findByTenantIdAndEmployeeIdAndPayMonth(Long tenantId,Long employeeId,String payMonth);
 List<EmployeePayslip> findByTenantIdAndEmployeeIdOrderByPayMonthDesc(Long tenantId,Long employeeId);
}
