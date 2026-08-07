package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.EmployeeStatus;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.AdminDtos;
import com.hragent.hragentv1.dto.DirectoryDtos;
import com.hragent.hragentv1.repo.DepartmentRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DirectoryService {
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;
    private final AdminService adminService;
    private final LeaveService leaveService;

    public DirectoryService(
            UserAccountRepository userAccountRepository,
            DepartmentRepository departmentRepository,
            AdminService adminService,
            LeaveService leaveService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
        this.adminService = adminService;
        this.leaveService = leaveService;
    }

    public DirectoryDtos.DirectoryOverview overview(UserAccount actor) {
        List<UserAccount> employees = userAccountRepository.findByTenantIdOrderByIdAsc(actor.getTenantId()).stream()
                .filter(UserAccount::isActive)
                .filter(employee -> employee.getEmployeeStatus() == null
                        || employee.getEmployeeStatus() == EmployeeStatus.ACTIVE)
                .toList();
        List<AdminDtos.EmployeeView> employeeViews = employees.stream()
                .map(adminService::employeeView)
                .toList();
        List<DirectoryDtos.DepartmentGroup> departments = departmentRepository
                .findByTenantIdAndActiveTrueOrderByNameAsc(actor.getTenantId()).stream()
                .map(department -> new DirectoryDtos.DepartmentGroup(
                        department.getId(),
                        department.getName(),
                        department.getCode(),
                        employees.stream().filter(employee -> department.getName().equals(employee.getDepartment())).count()
                ))
                .toList();
        return new DirectoryDtos.DirectoryOverview(departments, employeeViews);
    }

    public DirectoryDtos.EmployeeDetail employeeDetail(UserAccount actor, Long employeeId) {
        UserAccount employee = userAccountRepository.findById(employeeId)
                .filter(item -> item.getTenantId().equals(actor.getTenantId()))
                .orElseThrow(() -> AppException.notFound("员工档案不存在"));
        boolean leaveDataVisible = actor.getRole() == Role.HR
                || actor.getId().equals(employee.getId())
                || (actor.getRole() == Role.MANAGER && actor.getId().equals(employee.getManagerId()));
        return new DirectoryDtos.EmployeeDetail(
                adminService.employeeView(employee),
                leaveDataVisible,
                leaveDataVisible ? leaveService.balances(employee) : List.of(),
                leaveDataVisible ? leaveService.mine(employee) : List.of()
        );
    }
}
