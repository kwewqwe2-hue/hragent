package com.hragent.hragentv1.config;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.service.SecretCryptoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner seedData(
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            KnowledgeArticleRepository knowledgeArticleRepository,
            DepartmentRepository departmentRepository,
            JobTitleRepository jobTitleRepository,
            PlatformAccountRepository platformAccountRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            EmployeePersonalProfileRepository employeePersonalProfileRepository,
            SecretCryptoService secretCryptoService,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            Tenant tenant = tenantRepository.findByCode("demo")
                    .orElseGet(() -> {
                        Tenant created = new Tenant();
                        created.setCode("demo");
                        created.setName("Demo Technology Co., Ltd.");
                        return tenantRepository.save(created);
                    });

            seedDepartment(departmentRepository, tenant.getId(), "R&D Center", "RD");
            seedDepartment(departmentRepository, tenant.getId(), "Human Resources", "HR");
            seedDepartment(departmentRepository, tenant.getId(), "Operations", "OPS");

            seedJobTitle(jobTitleRepository, tenant.getId(), "Java Engineer", "JAVA");
            seedJobTitle(jobTitleRepository, tenant.getId(), "Engineering Manager", "ENG-MGR");
            seedJobTitle(jobTitleRepository, tenant.getId(), "HR Manager", "HR-MGR");

            UserAccount manager = userAccountRepository.findByUsername("lisi")
                    .orElseGet(() -> userAccountRepository.save(user(
                            "lisi",
                            "M001",
                            "Li Si",
                            Role.MANAGER,
                            "R&D Center",
                            "Engineering Manager",
                            "lisi@example.com",
                            "13800000002",
                            LocalDate.of(2024, 1, 15),
                            tenant.getId(),
                            null,
                            passwordEncoder
                    )));

            UserAccount hr = userAccountRepository.findByUsername("wanghr")
                    .orElseGet(() -> userAccountRepository.save(user(
                            "wanghr",
                            "H001",
                            "Wang HR",
                            Role.HR,
                            "Human Resources",
                            "HR Manager",
                            "wanghr@example.com",
                            "13800000003",
                            LocalDate.of(2023, 8, 1),
                            tenant.getId(),
                            null,
                            passwordEncoder
                    )));

            if (manager.getManagerId() == null) {
                manager.setManagerId(hr.getId());
                userAccountRepository.save(manager);
            }

            UserAccount employee = userAccountRepository.findByUsername("zhangsan")
                    .orElseGet(() -> userAccountRepository.save(user(
                            "zhangsan",
                            "E001",
                            "Zhang San",
                            Role.EMPLOYEE,
                            "R&D Center",
                            "Java Engineer",
                            "zhangsan@example.com",
                            "13800000001",
                            LocalDate.of(2025, 3, 10),
                            tenant.getId(),
                            manager.getId(),
                            passwordEncoder
                    )));

            if (employee.getManagerId() == null) {
                employee.setManagerId(manager.getId());
                userAccountRepository.save(employee);
            }

            seedPersonalProfile(
                    employeePersonalProfileRepository,
                    secretCryptoService,
                    employee,
                    "张三",
                    "ZHANG SAN",
                    "男",
                    LocalDate.of(1996, 3, 12),
                    "中国",
                    "居民身份证（演示）",
                    "110101199603120000",
                    "DEMOE001",
                    LocalDate.of(2031, 6, 30),
                    "全日制",
                    LocalDate.of(2025, 3, 10),
                    LocalDate.of(2028, 3, 9),
                    "北京市海淀区",
                    "15000",
                    "北京市海淀区演示地址 1 号",
                    "张先生",
                    "13900000001"
            );
            seedPersonalProfile(
                    employeePersonalProfileRepository,
                    secretCryptoService,
                    manager,
                    "李四",
                    "LI SI",
                    "男",
                    LocalDate.of(1989, 6, 6),
                    "中国",
                    "居民身份证（演示）",
                    "110101198906060000",
                    "DEMOM001",
                    LocalDate.of(2030, 12, 31),
                    "全日制",
                    LocalDate.of(2024, 1, 15),
                    LocalDate.of(2027, 1, 14),
                    "北京市海淀区",
                    "28000",
                    "北京市朝阳区演示地址 2 号",
                    "李女士",
                    "13900000002"
            );
            seedPersonalProfile(
                    employeePersonalProfileRepository,
                    secretCryptoService,
                    hr,
                    "王 HR",
                    "WANG HR",
                    "女",
                    LocalDate.of(1992, 8, 8),
                    "中国",
                    "居民身份证（演示）",
                    "110101199208080000",
                    "DEMOH001",
                    LocalDate.of(2032, 8, 31),
                    "全日制",
                    LocalDate.of(2023, 8, 1),
                    LocalDate.of(2029, 7, 31),
                    "北京市朝阳区",
                    "26000",
                    "北京市朝阳区演示地址 3 号",
                    "王先生",
                    "13900000003"
            );

            linkDemoAccount(
                    manager,
                    "USR-LISI-DEMO",
                    platformAccountRepository,
                    workspaceMembershipRepository,
                    userAccountRepository
            );
            PlatformAccount hrAccount = linkDemoAccount(
                    hr,
                    "USR-WANGHR-DEMO",
                    platformAccountRepository,
                    workspaceMembershipRepository,
                    userAccountRepository
            );
            linkDemoAccount(
                    employee,
                    "USR-ZHANGSAN-DEMO",
                    platformAccountRepository,
                    workspaceMembershipRepository,
                    userAccountRepository
            );
            if (tenant.getCreatedByAccountId() == null) {
                tenant.setCreatedByAccountId(hrAccount.getId());
                tenantRepository.save(tenant);
            }
            seedPlatformAdmin(platformAccountRepository, passwordEncoder);

            seedBalance(leaveBalanceRepository, tenant.getId(), employee.getId(), LeaveType.ANNUAL, "5", "0");
            seedBalance(leaveBalanceRepository, tenant.getId(), employee.getId(), LeaveType.SICK, "10", "0");
            seedBalance(leaveBalanceRepository, tenant.getId(), employee.getId(), LeaveType.PERSONAL, "5", "0");
            seedBalance(leaveBalanceRepository, tenant.getId(), employee.getId(), LeaveType.MARRIAGE, "10", "0");
            seedBalance(leaveBalanceRepository, tenant.getId(), manager.getId(), LeaveType.ANNUAL, "10", "0");
            seedBalance(leaveBalanceRepository, tenant.getId(), manager.getId(), LeaveType.SICK, "10", "0");
            seedBalance(leaveBalanceRepository, tenant.getId(), manager.getId(), LeaveType.PERSONAL, "5", "0");
            seedBalance(leaveBalanceRepository, tenant.getId(), manager.getId(), LeaveType.MARRIAGE, "10", "0");
            seedBalance(leaveBalanceRepository, tenant.getId(), hr.getId(), LeaveType.ANNUAL, "10", "0");
            seedBalance(leaveBalanceRepository, tenant.getId(), hr.getId(), LeaveType.SICK, "10", "0");
            seedBalance(leaveBalanceRepository, tenant.getId(), hr.getId(), LeaveType.PERSONAL, "5", "0");
            seedBalance(leaveBalanceRepository, tenant.getId(), hr.getId(), LeaveType.MARRIAGE, "10", "0");

            if (knowledgeArticleRepository.findByTenantIdOrderByUpdatedAtDesc(tenant.getId()).isEmpty()) {
                knowledgeArticleRepository.saveAll(List.of(
                        article(tenant.getId(), "Company Policy", "Leave Management Overview",
                                "Employees should submit leave requests with type, date range, day count, and reason. Emergency leave may be reported first and completed in the system later.",
                                "Employee Handbook V1.0", "Default", "APPROVED"),
                        article(tenant.getId(), "Leave Rule", "Annual Leave Rule",
                                "Annual leave requests require sufficient balance. Balance is deducted only after HR record approval.",
                                "Company Leave Policy", "Default", "APPROVED"),
                        article(tenant.getId(), "Leave Rule", "Sick Leave Rule",
                                "Sick leave longer than one day should include medical proof or other auditable material.",
                                "Company Leave Policy", "Default", "APPROVED"),
                        article(tenant.getId(), "FAQ", "AI Decision Scope",
                                "AI output is only an auxiliary suggestion. Final HR and manager approval remains a human decision.",
                                "HR FAQ", "Default", "APPROVED")
                ));
            }
        };
    }

    private PlatformAccount linkDemoAccount(
            UserAccount profile,
            String publicId,
            PlatformAccountRepository accountRepository,
            WorkspaceMembershipRepository membershipRepository,
            UserAccountRepository userAccountRepository
    ) {
        PlatformAccount account = accountRepository.findByUsernameIgnoreCase(profile.getUsername())
                .orElseGet(() -> {
                    PlatformAccount created = new PlatformAccount();
                    created.setPublicId(publicId);
                    created.setUsername(profile.getUsername());
                    created.setEmail(profile.getEmail());
                    created.setName(profile.getName());
                    created.setPasswordHash(profile.getPasswordHash());
                    return accountRepository.save(created);
                });
        if (profile.getAccountId() == null || !profile.getAccountId().equals(account.getId())) {
            profile.setAccountId(account.getId());
            userAccountRepository.save(profile);
        }
        WorkspaceMembership membership = membershipRepository
                .findByAccountIdAndWorkspaceId(account.getId(), profile.getTenantId())
                .orElseGet(WorkspaceMembership::new);
        membership.setWorkspaceId(profile.getTenantId());
        membership.setAccountId(account.getId());
        membership.setEmployeeProfileId(profile.getId());
        if (membership.getId() == null) {
            membership.setRole(profile.getRole());
            membership.setStatus(MembershipStatus.ACTIVE);
        }
        membershipRepository.save(membership);
        return account;
    }

    private void seedPlatformAdmin(PlatformAccountRepository repository, PasswordEncoder passwordEncoder) {
        repository.findByUsernameIgnoreCase("platformadmin").orElseGet(() -> {
            PlatformAccount account = new PlatformAccount();
            account.setPublicId("USR-PLATFORM-ADMIN");
            account.setUsername("platformadmin");
            account.setEmail("platformadmin@hragent.local");
            account.setName("Platform Administrator");
            account.setPasswordHash(passwordEncoder.encode("123456"));
            account.setPlatformAdmin(true);
            return repository.save(account);
        });
    }

    private void seedDepartment(DepartmentRepository repository, Long tenantId, String name, String code) {
        repository.findByTenantIdAndName(tenantId, name).orElseGet(() -> {
            Department department = new Department();
            department.setTenantId(tenantId);
            department.setName(name);
            department.setCode(code);
            department.setDescription("Seeded department");
            return repository.save(department);
        });
    }

    private void seedJobTitle(JobTitleRepository repository, Long tenantId, String name, String code) {
        repository.findByTenantIdAndName(tenantId, name).orElseGet(() -> {
            JobTitle jobTitle = new JobTitle();
            jobTitle.setTenantId(tenantId);
            jobTitle.setName(name);
            jobTitle.setCode(code);
            jobTitle.setDescription("Seeded job title");
            return repository.save(jobTitle);
        });
    }

    private UserAccount user(
            String username,
            String employeeNo,
            String name,
            Role role,
            String department,
            String title,
            String email,
            String phone,
            LocalDate entryDate,
            Long tenantId,
            Long managerId,
            PasswordEncoder passwordEncoder
    ) {
        UserAccount user = new UserAccount();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("123456"));
        user.setEmployeeNo(employeeNo);
        user.setName(name);
        user.setRole(role);
        user.setDepartment(department);
        user.setTitle(title);
        user.setEmail(email);
        user.setPhone(phone);
        user.setEntryDate(entryDate);
        user.setEmployeeStatus(EmployeeStatus.ACTIVE);
        user.setManagerId(managerId);
        return user;
    }

    private void seedBalance(
            LeaveBalanceRepository repository,
            Long tenantId,
            Long employeeId,
            LeaveType type,
            String total,
            String used
    ) {
        repository.findByTenantIdAndEmployeeIdAndLeaveType(tenantId, employeeId, type)
                .orElseGet(() -> repository.save(balance(tenantId, employeeId, type, total, used)));
    }

    private void seedPersonalProfile(
            EmployeePersonalProfileRepository repository,
            SecretCryptoService secretCryptoService,
            UserAccount employee,
            String legalName,
            String englishName,
            String gender,
            LocalDate birthDate,
            String nationality,
            String idType,
            String idNumber,
            String passportNumber,
            LocalDate passportExpiryDate,
            String employmentType,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            String workLocation,
            String monthlySalary,
            String homeAddress,
            String emergencyContactName,
            String emergencyContactPhone
    ) {
        repository.findByTenantIdAndEmployeeId(employee.getTenantId(), employee.getId()).orElseGet(() -> {
            EmployeePersonalProfile profile = new EmployeePersonalProfile();
            profile.setTenantId(employee.getTenantId());
            profile.setEmployeeId(employee.getId());
            profile.setLegalName(legalName);
            profile.setEnglishName(englishName);
            profile.setGender(gender);
            profile.setBirthDate(birthDate);
            profile.setNationality(nationality);
            profile.setIdType(idType);
            profile.setIdNumberEncrypted(secretCryptoService.encrypt(idNumber));
            profile.setPassportNumberEncrypted(secretCryptoService.encrypt(passportNumber));
            profile.setPassportExpiryDate(passportExpiryDate);
            profile.setEmploymentType(employmentType);
            profile.setContractStartDate(contractStartDate);
            profile.setContractEndDate(contractEndDate);
            profile.setWorkLocation(workLocation);
            profile.setMonthlySalary(new BigDecimal(monthlySalary));
            profile.setCurrency("CNY");
            profile.setHomeAddress(homeAddress);
            profile.setEmergencyContactName(emergencyContactName);
            profile.setEmergencyContactPhone(emergencyContactPhone);
            profile.setUpdatedByEmployeeId(employee.getId());
            return repository.save(profile);
        });
    }

    private LeaveBalance balance(Long tenantId, Long employeeId, LeaveType type, String total, String used) {
        LeaveBalance balance = new LeaveBalance();
        balance.setTenantId(tenantId);
        balance.setEmployeeId(employeeId);
        balance.setLeaveType(type);
        balance.setTotalDays(new BigDecimal(total));
        balance.setUsedDays(new BigDecimal(used));
        return balance;
    }

    private KnowledgeArticle article(
            Long tenantId,
            String category,
            String title,
            String content,
            String source,
            String region,
            String status
    ) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setTenantId(tenantId);
        article.setCategory(category);
        article.setTitle(title);
        article.setContent(content);
        article.setSource(source);
        article.setRegion(region);
        article.setPublishedAt(LocalDate.of(2026, 1, 1));
        article.setUpdatedAt(LocalDate.of(2026, 7, 9));
        article.setReviewStatus(status);
        return article;
    }
}
