package com.hragent.hragentv1.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "leave_balances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenantId", "employeeId", "leaveType"}))
public class LeaveBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeaveType leaveType;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal totalDays = BigDecimal.ZERO;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal usedDays = BigDecimal.ZERO;

    public BigDecimal remainingDays() {
        return totalDays.subtract(usedDays);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public BigDecimal getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(BigDecimal totalDays) {
        this.totalDays = totalDays;
    }

    public BigDecimal getUsedDays() {
        return usedDays;
    }

    public void setUsedDays(BigDecimal usedDays) {
        this.usedDays = usedDays;
    }
}
