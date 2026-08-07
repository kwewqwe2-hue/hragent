package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.LeaveBalance;
import com.hragent.hragentv1.domain.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    List<LeaveBalance> findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(Long tenantId, Long employeeId);

    Optional<LeaveBalance> findByTenantIdAndEmployeeIdAndLeaveType(Long tenantId, Long employeeId, LeaveType leaveType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LeaveBalance> findForUpdateByTenantIdAndEmployeeIdAndLeaveType(
            Long tenantId,
            Long employeeId,
            LeaveType leaveType
    );
}
