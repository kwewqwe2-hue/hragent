package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.OpenApiDtos;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.web.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class OpenApiService {
    private final IntegrationApiKeyRepository apiKeyRepository;
    private final ApiCallLogRepository apiCallLogRepository;
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;
    private final JobTitleRepository jobTitleRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final PasswordEncoder passwordEncoder;

    public OpenApiService(
            IntegrationApiKeyRepository apiKeyRepository,
            ApiCallLogRepository apiCallLogRepository,
            UserAccountRepository userAccountRepository,
            DepartmentRepository departmentRepository,
            JobTitleRepository jobTitleRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiCallLogRepository = apiCallLogRepository;
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public IntegrationApiKey authenticate(String rawKey, String method, String path) {
        if (rawKey == null || rawKey.length() < 12) {
            throw AppException.unauthorized("Missing or invalid X-API-Key");
        }
        String prefix = rawKey.substring(0, 12);
        IntegrationApiKey key = apiKeyRepository.findByKeyPrefixAndActiveTrue(prefix)
                .filter(candidate -> candidate.getKeyHash().equals(hash(rawKey)))
                .orElseThrow(() -> AppException.unauthorized("Invalid X-API-Key"));
        key.setLastUsedAt(LocalDateTime.now());
        apiKeyRepository.save(key);
        return key;
    }

    public OpenApiDtos.EmployeeResponse employee(IntegrationApiKey key, String employeeNo) {
        UserAccount employee = userAccountRepository.findByTenantIdAndEmployeeNo(key.getTenantId(), employeeNo)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Employee not found"));
        log(key, "GET", "/openapi/v1/employees/" + employeeNo, 200, "Employee query");
        return employeeResponse(employee);
    }

    public OpenApiDtos.BalanceResponse balances(IntegrationApiKey key, String employeeNo) {
        UserAccount employee = userAccountRepository.findByTenantIdAndEmployeeNo(key.getTenantId(), employeeNo)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Employee not found"));
        List<OpenApiDtos.BalanceLine> lines = leaveBalanceRepository
                .findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(key.getTenantId(), employee.getId())
                .stream()
                .map(balance -> new OpenApiDtos.BalanceLine(
                        balance.getLeaveType().name(),
                        balance.getLeaveType().getLabel(),
                        balance.getTotalDays().toPlainString(),
                        balance.getUsedDays().toPlainString(),
                        balance.remainingDays().toPlainString()
                ))
                .toList();
        log(key, "GET", "/openapi/v1/balances/" + employeeNo, 200, "Balance query");
        return new OpenApiDtos.BalanceResponse(employeeNo, lines);
    }

    @Transactional
    public OpenApiDtos.EmployeeResponse syncEmployee(IntegrationApiKey key, OpenApiDtos.EmployeePayload payload) {
        if (payload.employeeNo() == null || payload.employeeNo().isBlank()) {
            throw AppException.badRequest("employeeNo is required");
        }
        validateOrg(key.getTenantId(), payload.department(), payload.title());
        UserAccount employee = userAccountRepository.findByTenantIdAndEmployeeNo(key.getTenantId(), payload.employeeNo())
                .orElseGet(() -> {
                    UserAccount created = new UserAccount();
                    created.setTenantId(key.getTenantId());
                    created.setUsername(payload.employeeNo() + "." + key.getTenantId());
                    created.setPasswordHash(passwordEncoder.encode("123456"));
                    created.setEmployeeNo(payload.employeeNo());
                    created.setRole(Role.EMPLOYEE);
                    return created;
                });
        employee.setName(payload.name());
        employee.setPhone(payload.phone());
        employee.setEmail(payload.email());
        employee.setDepartment(payload.department());
        employee.setTitle(payload.title());
        employee.setEntryDate(payload.entryDate());
        employee.setEmployeeStatus(parseStatus(payload.employeeStatus()));
        employee.setActive(employee.getEmployeeStatus() == EmployeeStatus.ACTIVE);
        if (payload.managerEmployeeNo() == null || payload.managerEmployeeNo().isBlank()) {
            employee.setManagerId(null);
        } else {
            UserAccount manager = userAccountRepository.findByTenantIdAndEmployeeNo(key.getTenantId(), payload.managerEmployeeNo())
                    .orElseThrow(() -> AppException.badRequest("Manager employee number not found"));
            if (manager.getRole() != Role.MANAGER && manager.getRole() != Role.HR) {
                throw AppException.badRequest("Manager employee number must belong to a manager or workspace administrator");
            }
            employee.setManagerId(manager.getId());
        }
        UserAccount saved = userAccountRepository.save(employee);
        ensureDefaultBalances(saved);
        log(key, "POST", "/openapi/v1/employees/sync", 200, "Employee synced: " + saved.getEmployeeNo());
        return employeeResponse(saved);
    }

    private OpenApiDtos.EmployeeResponse employeeResponse(UserAccount employee) {
        String managerNo = null;
        if (employee.getManagerId() != null) {
            managerNo = userAccountRepository.findById(employee.getManagerId()).map(UserAccount::getEmployeeNo).orElse(null);
        }
        return new OpenApiDtos.EmployeeResponse(
                employee.getEmployeeNo(),
                employee.getName(),
                employee.getPhone(),
                employee.getEmail(),
                employee.getDepartment(),
                employee.getTitle(),
                managerNo,
                employee.getEntryDate(),
                (employee.getEmployeeStatus() == null ? EmployeeStatus.ACTIVE : employee.getEmployeeStatus()).name(),
                employee.isActive()
        );
    }

    private void validateOrg(Long tenantId, String department, String title) {
        departmentRepository.findByTenantIdAndName(tenantId, department)
                .filter(Department::isActive)
                .orElseThrow(() -> AppException.badRequest("Department not found or inactive"));
        jobTitleRepository.findByTenantIdAndName(tenantId, title)
                .filter(JobTitle::isActive)
                .orElseThrow(() -> AppException.badRequest("Job title not found or inactive"));
    }

    private EmployeeStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return EmployeeStatus.ACTIVE;
        String normalized = value.trim().toUpperCase();
        if (normalized.equals("LEFT") || normalized.equals("RESIGNED")) return EmployeeStatus.LEFT;
        if (normalized.equals("INACTIVE") || normalized.equals("DISABLED")) return EmployeeStatus.INACTIVE;
        return EmployeeStatus.ACTIVE;
    }

    private void log(IntegrationApiKey key, String method, String path, int statusCode, String message) {
        ApiCallLog log = new ApiCallLog();
        log.setTenantId(key.getTenantId());
        log.setApiKeyId(key.getId());
        log.setMethod(method);
        log.setPath(path);
        log.setStatusCode(statusCode);
        log.setMessage(message);
        apiCallLogRepository.save(log);
    }

    private void ensureDefaultBalances(UserAccount employee) {
        for (LeaveType leaveType : LeaveType.values()) {
            leaveBalanceRepository.findByTenantIdAndEmployeeIdAndLeaveType(
                            employee.getTenantId(), employee.getId(), leaveType)
                    .orElseGet(() -> {
                        LeaveBalance balance = new LeaveBalance();
                        balance.setTenantId(employee.getTenantId());
                        balance.setEmployeeId(employee.getId());
                        balance.setLeaveType(leaveType);
                        return leaveBalanceRepository.save(balance);
                    });
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash API key", exception);
        }
    }
}
