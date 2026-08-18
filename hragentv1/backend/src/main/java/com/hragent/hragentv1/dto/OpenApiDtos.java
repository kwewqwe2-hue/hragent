package com.hragent.hragentv1.dto;

import java.time.LocalDate;
import java.util.List;

public class OpenApiDtos {
    public record EmployeePayload(
            String employeeNo,
            String name,
            String phone,
            String email,
            String department,
            String title,
            String managerEmployeeNo,
            LocalDate entryDate,
            String employeeStatus
    ) {
    }

    public record EmployeeResponse(
            String employeeNo,
            String name,
            String phone,
            String email,
            String department,
            String title,
            String managerEmployeeNo,
            LocalDate entryDate,
            String employeeStatus,
            boolean active
    ) {
    }

    public record BalanceResponse(
            String employeeNo,
            List<BalanceLine> balances
    ) {
    }

    public record BalanceLine(
            String leaveType,
            String leaveTypeLabel,
            String totalDays,
            String usedDays,
            String remainingDays
    ) {
    }
}
