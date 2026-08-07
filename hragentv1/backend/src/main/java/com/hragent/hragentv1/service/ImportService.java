package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.AdminDtos;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.web.AppException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ImportService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;
    private final JobTitleRepository jobTitleRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final ImportBatchRepository importBatchRepository;
    private final PlatformAccountRepository platformAccountRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    public ImportService(
            UserAccountRepository userAccountRepository,
            DepartmentRepository departmentRepository,
            JobTitleRepository jobTitleRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            ImportBatchRepository importBatchRepository,
            PlatformAccountRepository platformAccountRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.importBatchRepository = importBatchRepository;
        this.platformAccountRepository = platformAccountRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AdminDtos.ImportResult importEmployees(UserAccount actor, MultipartFile file, boolean commit) {
        List<RowData> rows = readRows(file);
        Set<String> incomingEmployeeNos = new HashSet<>();
        Set<String> duplicateEmployeeNos = new HashSet<>();
        for (RowData row : rows) {
            String employeeNo = value(row, "employeeNo", "employee", "employeeId");
            if (!employeeNo.isBlank()) {
                if (!incomingEmployeeNos.add(employeeNo)) {
                    duplicateEmployeeNos.add(employeeNo);
                }
            }
        }

        List<AdminDtos.ImportRowView> previews = new ArrayList<>();
        List<EmployeeImportRow> validRows = new ArrayList<>();
        for (RowData row : rows) {
            EmployeeImportRow parsed = parseEmployeeRow(
                    actor.getTenantId(), row, incomingEmployeeNos, duplicateEmployeeNos);
            previews.add(new AdminDtos.ImportRowView(row.number(), parsed.valid(), parsed.action(), parsed.message(), row.values()));
            if (parsed.valid()) {
                validRows.add(parsed);
            }
        }

        if (commit && previews.stream().allMatch(AdminDtos.ImportRowView::valid)) {
            Map<String, UserAccount> savedByNo = new HashMap<>();
            for (EmployeeImportRow row : validRows) {
                UserAccount user = userAccountRepository.findByTenantIdAndEmployeeNo(actor.getTenantId(), row.employeeNo())
                        .orElseGet(() -> {
                            UserAccount created = new UserAccount();
                            created.setTenantId(actor.getTenantId());
                            created.setUsername(row.employeeNo() + "." + actor.getTenantId());
                            created.setPasswordHash(passwordEncoder.encode("123456"));
                            created.setRole(row.role());
                            created.setEmployeeNo(row.employeeNo());
                            return created;
                        });
                if (!row.publicId().isBlank()) {
                    PlatformAccount account = platformAccountRepository.findByPublicId(row.publicId())
                            .orElseThrow(() -> AppException.badRequest("Account not found: " + row.publicId()));
                    user.setAccountId(account.getId());
                }
                user.setName(row.name());
                user.setPhone(row.phone());
                user.setEmail(row.email());
                user.setDepartment(row.department());
                user.setTitle(row.title());
                user.setEntryDate(row.entryDate());
                user.setEmployeeStatus(row.status());
                user.setActive(row.status() == EmployeeStatus.ACTIVE);
                user.setRole(row.role());
                UserAccount savedUser = userAccountRepository.save(user);
                savedByNo.put(row.employeeNo(), savedUser);
                ensureDefaultBalances(savedUser);
            }

            for (EmployeeImportRow row : validRows) {
                UserAccount user = savedByNo.get(row.employeeNo());
                if (row.managerEmployeeNo().isBlank()) {
                    user.setManagerId(null);
                } else {
                    UserAccount manager = userAccountRepository.findByTenantIdAndEmployeeNo(actor.getTenantId(), row.managerEmployeeNo())
                            .orElse(savedByNo.get(row.managerEmployeeNo()));
                    user.setManagerId(manager == null ? null : manager.getId());
                }
                userAccountRepository.save(user);

                if (!row.publicId().isBlank()) {
                    PlatformAccount account = platformAccountRepository.findByPublicId(row.publicId()).orElseThrow();
                    WorkspaceMembership membership = workspaceMembershipRepository
                            .findByAccountIdAndWorkspaceId(account.getId(), actor.getTenantId())
                            .orElseThrow(() -> AppException.badRequest("Account has not joined this workspace: " + row.publicId()));
                    membership.setEmployeeProfileId(user.getId());
                    membership.setRole(row.role());
                    membership.setStatus(MembershipStatus.ACTIVE);
                    workspaceMembershipRepository.save(membership);
                } else if (user.getAccountId() != null) {
                    workspaceMembershipRepository
                            .findByAccountIdAndWorkspaceId(user.getAccountId(), actor.getTenantId())
                            .ifPresent(membership -> {
                                membership.setEmployeeProfileId(user.getId());
                                membership.setRole(user.getRole());
                                membership.setStatus(MembershipStatus.ACTIVE);
                                workspaceMembershipRepository.save(membership);
                            });
                }
            }
            saveBatch(actor, "EMPLOYEE", file, rows.size(), validRows.size(), 0, "COMMITTED", "Employee import committed");
            auditService.log(actor, "IMPORT_EMPLOYEES", "import_batch", null, "rows=" + validRows.size());
        } else if (commit) {
            saveBatch(actor, "EMPLOYEE", file, rows.size(), 0, previews.size() - validRows.size(), "FAILED", "Validation failed");
        }

        return new AdminDtos.ImportResult("EMPLOYEE", commit && previews.stream().allMatch(AdminDtos.ImportRowView::valid),
                rows.size(), validRows.size(), rows.size() - validRows.size(), previews);
    }

    @Transactional
    public AdminDtos.ImportResult importBalances(UserAccount actor, MultipartFile file, boolean commit) {
        List<RowData> rows = readRows(file);
        Set<String> seenEmployeeNos = new HashSet<>();
        Set<String> duplicateEmployeeNos = new HashSet<>();
        for (RowData row : rows) {
            String employeeNo = value(row, "employeeNo", "employee", "employeeId");
            if (!employeeNo.isBlank() && !seenEmployeeNos.add(employeeNo)) {
                duplicateEmployeeNos.add(employeeNo);
            }
        }
        List<AdminDtos.ImportRowView> previews = new ArrayList<>();
        List<BalanceImportRow> validRows = new ArrayList<>();
        for (RowData row : rows) {
            BalanceImportRow parsed = parseBalanceRow(actor.getTenantId(), row, duplicateEmployeeNos);
            previews.add(new AdminDtos.ImportRowView(row.number(), parsed.valid(), parsed.action(), parsed.message(), row.values()));
            if (parsed.valid()) {
                validRows.add(parsed);
            }
        }

        if (commit && previews.stream().allMatch(AdminDtos.ImportRowView::valid)) {
            for (BalanceImportRow row : validRows) {
                UserAccount employee = userAccountRepository.findByTenantIdAndEmployeeNo(actor.getTenantId(), row.employeeNo())
                        .orElseThrow(() -> AppException.badRequest("Employee not found: " + row.employeeNo()));
                for (Map.Entry<LeaveType, BalanceValue> entry : row.balances().entrySet()) {
                    LeaveBalance balance = leaveBalanceRepository
                            .findByTenantIdAndEmployeeIdAndLeaveType(actor.getTenantId(), employee.getId(), entry.getKey())
                            .orElseGet(() -> {
                                LeaveBalance created = new LeaveBalance();
                                created.setTenantId(actor.getTenantId());
                                created.setEmployeeId(employee.getId());
                                created.setLeaveType(entry.getKey());
                                return created;
                            });
                    balance.setTotalDays(entry.getValue().total());
                    balance.setUsedDays(entry.getValue().used());
                    leaveBalanceRepository.save(balance);
                }
            }
            saveBatch(actor, "BALANCE", file, rows.size(), validRows.size(), 0, "COMMITTED", "Balance import committed");
            auditService.log(actor, "IMPORT_BALANCES", "import_batch", null, "rows=" + validRows.size());
        } else if (commit) {
            saveBatch(actor, "BALANCE", file, rows.size(), 0, previews.size() - validRows.size(), "FAILED", "Validation failed");
        }

        return new AdminDtos.ImportResult("BALANCE", commit && previews.stream().allMatch(AdminDtos.ImportRowView::valid),
                rows.size(), validRows.size(), rows.size() - validRows.size(), previews);
    }

    private EmployeeImportRow parseEmployeeRow(
            Long tenantId,
            RowData row,
            Set<String> incomingEmployeeNos,
            Set<String> duplicateEmployeeNos
    ) {
        String publicId = value(row, "userId", "publicId", "accountId").toUpperCase();
        String employeeNo = value(row, "employeeNo", "employee", "employeeId");
        String name = value(row, "name", "employeeName");
        String phone = value(row, "phone", "mobile");
        String email = value(row, "email");
        String department = value(row, "department", "dept");
        String title = value(row, "title", "jobTitle", "position");
        String managerNo = value(row, "managerEmployeeNo", "managerNo", "manager");
        String entryDateText = value(row, "entryDate", "hireDate", "joinDate");
        EmployeeStatus status = parseStatus(value(row, "status", "employeeStatus"));
        Role role = parseRole(value(row, "role"));
        List<String> errors = new ArrayList<>();

        if (employeeNo.isBlank()) errors.add("employeeNo is required");
        if (duplicateEmployeeNos.contains(employeeNo)) errors.add("duplicate employeeNo in import file: " + employeeNo);
        if (name.isBlank()) errors.add("name is required");
        if (department.isBlank()) errors.add("department is required");
        if (title.isBlank()) errors.add("title is required");
        if (!department.isBlank()
                && departmentRepository.findByTenantIdAndName(tenantId, department).filter(Department::isActive).isEmpty()) {
            errors.add("department not found: " + department);
        }
        if (!title.isBlank()
                && jobTitleRepository.findByTenantIdAndName(tenantId, title).filter(JobTitle::isActive).isEmpty()) {
            errors.add("job title not found: " + title);
        }
        if (!managerNo.isBlank() && !incomingEmployeeNos.contains(managerNo)
                && userAccountRepository.findByTenantIdAndEmployeeNo(tenantId, managerNo).isEmpty()) {
            errors.add("manager employeeNo not found: " + managerNo);
        }
        if (!publicId.isBlank()) {
            Optional<PlatformAccount> account = platformAccountRepository.findByPublicId(publicId);
            if (account.isEmpty()) {
                errors.add("userId not found: " + publicId);
            } else {
                Optional<WorkspaceMembership> membership = workspaceMembershipRepository
                        .findByAccountIdAndWorkspaceId(account.get().getId(), tenantId);
                if (membership.isEmpty()
                        || (membership.get().getStatus() != MembershipStatus.PENDING_PROFILE
                        && membership.get().getStatus() != MembershipStatus.ACTIVE)) {
                    errors.add("userId has not been approved for this workspace: " + publicId);
                }
                userAccountRepository.findByTenantIdAndAccountId(tenantId, account.get().getId())
                        .filter(existing -> !existing.getEmployeeNo().equals(employeeNo))
                        .ifPresent(existing -> errors.add("userId is already bound to employeeNo: " + existing.getEmployeeNo()));
            }
        }

        LocalDate entryDate = null;
        if (!entryDateText.isBlank()) {
            try {
                entryDate = LocalDate.parse(entryDateText, DATE_FORMAT);
            } catch (Exception exception) {
                errors.add("entryDate must be yyyy-MM-dd");
            }
        }

        boolean exists = !employeeNo.isBlank() && userAccountRepository.findByTenantIdAndEmployeeNo(tenantId, employeeNo).isPresent();
        return new EmployeeImportRow(errors.isEmpty(), exists ? "UPDATE" : "CREATE",
                String.join("; ", errors), publicId, employeeNo, name, phone, email, department, title, managerNo, entryDate, status, role);
    }

    private BalanceImportRow parseBalanceRow(Long tenantId, RowData row, Set<String> duplicateEmployeeNos) {
        String employeeNo = value(row, "employeeNo", "employee", "employeeId");
        List<String> errors = new ArrayList<>();
        if (employeeNo.isBlank()) {
            errors.add("employeeNo is required");
        } else if (userAccountRepository.findByTenantIdAndEmployeeNo(tenantId, employeeNo).isEmpty()) {
            errors.add("employee not found: " + employeeNo);
        }
        if (duplicateEmployeeNos.contains(employeeNo)) {
            errors.add("duplicate employeeNo in import file: " + employeeNo);
        }

        Map<LeaveType, BalanceValue> values = new EnumMap<>(LeaveType.class);
        addBalance(values, errors, LeaveType.ANNUAL, row, "annual");
        addBalance(values, errors, LeaveType.SICK, row, "sick");
        addBalance(values, errors, LeaveType.PERSONAL, row, "personal");
        addBalance(values, errors, LeaveType.MARRIAGE, row, "marriage");
        if (values.isEmpty()) {
            errors.add("at least one balance column is required");
        }

        return new BalanceImportRow(errors.isEmpty(), "UPSERT", String.join("; ", errors), employeeNo, values);
    }

    private void addBalance(Map<LeaveType, BalanceValue> values, List<String> errors, LeaveType type, RowData row, String key) {
        String balanceText = value(row, key + "Balance", key);
        String totalText = value(row, key + "Total");
        String usedText = value(row, key + "Used");
        if (balanceText.isBlank() && totalText.isBlank() && usedText.isBlank()) {
            return;
        }
        try {
            BigDecimal total = !totalText.isBlank() ? new BigDecimal(totalText) : new BigDecimal(balanceText);
            BigDecimal used = !usedText.isBlank() ? new BigDecimal(usedText) : BigDecimal.ZERO;
            if (total.signum() < 0 || used.signum() < 0) {
                errors.add(key + " balance cannot be negative");
            } else if (used.compareTo(total) > 0) {
                errors.add(key + " used days cannot exceed total days");
            } else {
                values.put(type, new BalanceValue(total, used));
            }
        } catch (Exception exception) {
            errors.add(key + " balance must be numeric");
        }
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

    private List<RowData> readRows(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            if (name.endsWith(".csv")) {
                return readCsv(file.getInputStream());
            }
            if (name.endsWith(".xlsx")) {
                return readXlsx(file.getInputStream());
            }
            throw AppException.badRequest("Only .xlsx and .csv files are supported");
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest("Unable to read import file: " + exception.getMessage());
        }
    }

    private List<RowData> readCsv(InputStream stream) throws Exception {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();
        List<RowData> rows = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            for (CSVRecord record : format.parse(reader)) {
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, String> entry : record.toMap().entrySet()) {
                    values.put(normalize(entry.getKey()), entry.getValue() == null ? "" : entry.getValue().trim());
                }
                rows.add(new RowData((int) record.getRecordNumber() + 1, values, new ArrayList<>(record.toMap().values())));
            }
        }
        return rows;
    }

    private List<RowData> readXlsx(InputStream stream) throws Exception {
        List<RowData> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(stream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() < 2) {
                return rows;
            }
            Row header = sheet.getRow(sheet.getFirstRowNum());
            List<String> headers = new ArrayList<>();
            for (Cell cell : header) {
                headers.add(normalize(cellString(cell)));
            }
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> values = new HashMap<>();
                List<String> rawValues = new ArrayList<>();
                boolean blank = true;
                for (int c = 0; c < headers.size(); c++) {
                    String value = cellString(row.getCell(c));
                    rawValues.add(value);
                    if (!value.isBlank()) blank = false;
                    values.put(headers.get(c), value);
                }
                if (!blank) {
                    rows.add(new RowData(i + 1, values, rawValues));
                }
            }
        }
        return rows;
    }

    private String cellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMAT)
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private String value(RowData row, String... keys) {
        for (String key : keys) {
            String value = row.columns().get(normalize(key));
            if (value != null) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private EmployeeStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return EmployeeStatus.ACTIVE;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.equals("LEFT") || normalized.equals("RESIGNED") || normalized.equals("OFFBOARD")) {
            return EmployeeStatus.LEFT;
        }
        if (normalized.equals("INACTIVE") || normalized.equals("DISABLED")) {
            return EmployeeStatus.INACTIVE;
        }
        return EmployeeStatus.ACTIVE;
    }

    private Role parseRole(String value) {
        if (value == null || value.isBlank()) {
            return Role.EMPLOYEE;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.equals("MANAGER") || normalized.equals("SUPERVISOR")) {
            return Role.MANAGER;
        }
        if (normalized.equals("HR") || normalized.equals("ADMIN") || normalized.equals("SPACE_ADMIN")) {
            return Role.HR;
        }
        return Role.EMPLOYEE;
    }

    private void saveBatch(UserAccount actor, String type, MultipartFile file, int total, int success, int failed, String status, String message) {
        ImportBatch batch = new ImportBatch();
        batch.setTenantId(actor.getTenantId());
        batch.setCreatedBy(actor.getId());
        batch.setImportType(type);
        batch.setFileName(file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename());
        batch.setTotalRows(total);
        batch.setSuccessRows(success);
        batch.setFailedRows(failed);
        batch.setStatus(status);
        batch.setMessage(message);
        importBatchRepository.save(batch);
    }

    private record RowData(int number, Map<String, String> columns, List<String> values) {
    }

    private record EmployeeImportRow(
            boolean valid,
            String action,
            String message,
            String publicId,
            String employeeNo,
            String name,
            String phone,
            String email,
            String department,
            String title,
            String managerEmployeeNo,
            LocalDate entryDate,
            EmployeeStatus status,
            Role role
    ) {
    }

    private record BalanceImportRow(
            boolean valid,
            String action,
            String message,
            String employeeNo,
            Map<LeaveType, BalanceValue> balances
    ) {
    }

    private record BalanceValue(BigDecimal total, BigDecimal used) {
    }
}
