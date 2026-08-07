package com.hragent.hragentv1.dto;

import java.util.List;

public class DirectoryDtos {
    public record DepartmentGroup(
            Long id,
            String name,
            String code,
            long memberCount
    ) {
    }

    public record DirectoryOverview(
            List<DepartmentGroup> departments,
            List<AdminDtos.EmployeeView> employees
    ) {
    }

    public record EmployeeDetail(
            AdminDtos.EmployeeView employee,
            boolean leaveDataVisible,
            List<LeaveDtos.BalanceView> balances,
            List<LeaveDtos.LeaveRequestView> requests
    ) {
    }
}
