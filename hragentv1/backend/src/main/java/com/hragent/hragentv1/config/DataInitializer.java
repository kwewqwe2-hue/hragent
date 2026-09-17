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

            ensureDemoEmploymentData(userAccountRepository, manager, LocalDate.of(2024, 1, 15));
            ensureDemoEmploymentData(userAccountRepository, hr, LocalDate.of(2023, 8, 1));

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

            ensureDemoEmploymentData(userAccountRepository, employee, LocalDate.of(2025, 3, 10));

            if (employee.getManagerId() == null) {
                employee.setManagerId(manager.getId());
                userAccountRepository.save(employee);
            }

            UserAccount newHire = userAccountRepository.findByUsername("chenchen")
                    .orElseGet(() -> {
                        UserAccount created = user(
                                "chenchen",
                                "NH001",
                                "陈晨",
                                Role.NEW_HIRE,
                                "Pending assignment",
                                "New hire",
                                "chenchen@example.com",
                                "13800000004",
                                LocalDate.of(2026, 9, 1),
                                tenant.getId(),
                                null,
                                passwordEncoder
                        );
                        created.setEmployeeStatus(EmployeeStatus.ONBOARDING);
                        return userAccountRepository.save(created);
                    });

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
            linkDemoAccount(
                    newHire,
                    "USR-CHENCHEN-DEMO",
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
            seedPolicyKnowledge(knowledgeArticleRepository, tenant.getId());
        };
    }

    private void seedPolicyKnowledge(KnowledgeArticleRepository repository, Long tenantId) {
        ensureKnowledge(repository, tenantId, "HR 助手政策与自助服务范围", "服务目录",
                "我可以协助解答和办理以下高频事项：\n"
                        + "1. 休假与假期：年假、病假、产假/生育假、婚假、事假，以及个人可用余额。\n"
                        + "2. 考勤与加班：工时、休息日、加班补偿的国家基线；具体排班、调休和审批规则以公司及工作地制度为准。\n"
                        + "3. 差旅、费用和福利：可核对已维护的差旅标准、报销范围和福利制度；未维护公司制度时会明确提示补充，而不会编造额度。\n"
                        + "4. 证明与自助流转：在职证明、签证/出境证明申请及下载进度；含薪资信息需按用途申请并经 HR 审核。\n"
                        + "5. 社保、公积金与落户：可提供异地转移接续的通用流程和材料清单；经办渠道、金额和落户条件由参保地/迁入地规定。\n"
                        + "6. 关键事件：可解答转正、合同续签、体检和年假失效的规则与办理路径。自动提醒和代办须由 HR 在工作台配置后才会实际触发。\n\n"
                        + "回答会标注国家法规或公司制度来源；涉及地区、法人主体、职级、工种或个人余额时，会结合已维护信息核对。",
                "HR 助手服务目录（2026-09-07）", "全国", LocalDate.of(2026, 9, 7));

        ensureKnowledge(repository, tenantId, "工时、考勤与加班补偿（国家基线）", "国家法规",
                "标准工时制下，劳动者每日工作不超过 8 小时、平均每周不超过 40 小时；用人单位应保证劳动者每周至少休息 1 日。延长工作时间通常每日不超过 1 小时，特殊原因每日不超过 3 小时、每月不超过 36 小时。\n"
                        + "加班工资基线：工作日延长工时不低于工资的 150%；休息日不能安排补休的不低于 200%；法定休假日不低于 300%。具体考勤口径、加班申请、调休及适用工时制应以劳动合同、公司制度和工作地规定为准。",
                "《中华人民共和国劳动法》第 36、38、41、44 条；《国务院关于职工工作时间的规定》（国务院令第 174 号，1995-05-01 施行）", "全国", LocalDate.of(1995, 5, 1));

        ensureKnowledge(repository, tenantId, "差旅、费用报销与福利（公司制度项）", "公司制度",
                "差旅交通、住宿、餐补标准，费用报销范围与凭证要求，以及补充医疗、节日福利等福利项目，通常由公司制度、预算和员工所属主体决定，国家层面没有统一的企业报销金额表。\n"
                        + "可根据员工的工作地、职级、工种和合同主体匹配已维护制度；若对应制度尚未录入，需由 HR 提供已生效的差旅、报销或福利文件后才能确认额度和材料。",
                "公司已审核的差旅、费用报销及福利制度；未维护时不作金额承诺", "按公司/工作地", LocalDate.of(2026, 9, 7));

        ensureKnowledge(repository, tenantId, "在职与收入相关证明办理", "自助服务",
                "可申请标准在职证明或签证/出境在职证明。申请时需明确证明类型、语言、用途，以及是否展示薪资；签证/出境证明还需目的国家或地区、领事馆或受理机构。提交后由 HR 审核，审核通过后可下载已生成文件。\n"
                        + "收入信息属于敏感信息，只能按本人申请和已审核模板处理；是否加盖电子签章、收入证明的具体格式和领取方式，以公司已配置模板及 HR 审核结果为准。",
                "本系统证明申请规则（2026-09-07）；《中华人民共和国劳动合同法》第 8 条（如实告知相关信息）", "公司制度", LocalDate.of(2026, 9, 7));

        ensureKnowledge(repository, tenantId, "社保、公积金异地转移与落户咨询", "办事指引",
                "社保转移接续通常需要在新参保地建立基本养老保险关系后提出申请，由新旧参保地经办机构协同办理；可先准备身份证明、社保卡或参保凭证、联系方式等基础材料。公积金异地转移和落户条件由缴存地、迁入地住房公积金中心及公安机关的现行规定执行。\n"
                        + "办理前应确认参保/缴存地、迁入地、就业状态和当地线上办理渠道；助手可据此生成材料清单，但不应把通用流程当作当地最终受理标准。",
                "《城镇企业职工基本养老保险关系转移接续暂行办法》（国办发〔2009〕66 号）；当地社保、公积金及公安部门现行规则", "按参保地/迁入地", LocalDate.of(2010, 1, 1));

        ensureKnowledge(repository, tenantId, "转正、合同续签、体检与年假提醒", "员工服务",
                "可查询转正、合同续签、体检预约和年假失效等事项的已维护规则、状态和材料。劳动合同续订应在原合同期满前依公司流程办理；符合无固定期限劳动合同法定条件的，应依法处理。\n"
                        + "提醒功能以 HR 在工作台配置的规则和数据为前提。未配置提醒规则时，助手可以说明办理时间点和所需材料，但不会声称已经发送提醒或完成代办。",
                "《中华人民共和国劳动合同法》第 10、14 条；公司已配置的转正、续签、体检及年假提醒规则", "公司制度", LocalDate.of(2008, 1, 1));
    }

    private void ensureKnowledge(
            KnowledgeArticleRepository repository,
            Long tenantId,
            String title,
            String category,
            String content,
            String source,
            String region,
            LocalDate publishedAt
    ) {
        boolean exists = repository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .anyMatch(article -> title.equals(article.getTitle()));
        if (!exists) {
            KnowledgeArticle article = article(tenantId, category, title, content, source, region, "APPROVED");
            article.setPublishedAt(publishedAt);
            article.setUpdatedAt(LocalDate.of(2026, 9, 7));
            repository.save(article);
        }
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

    private void ensureDemoEmploymentData(
            UserAccountRepository repository,
            UserAccount account,
            LocalDate entryDate
    ) {
        boolean changed = false;
        if (account.getEntryDate() == null) {
            account.setEntryDate(entryDate);
            changed = true;
        }
        if (account.getEmployeeStatus() == null) {
            account.setEmployeeStatus(EmployeeStatus.ACTIVE);
            changed = true;
        }
        if (changed) {
            repository.save(account);
        }
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
