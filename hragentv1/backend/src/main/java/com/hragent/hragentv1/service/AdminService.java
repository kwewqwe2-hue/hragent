package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.AdminDtos;
import com.hragent.hragentv1.dto.LeaveDtos;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.web.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.client.MultipartBodyBuilder;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminService {
    private final UserAccountRepository userAccountRepository;
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final DepartmentRepository departmentRepository;
    private final JobTitleRepository jobTitleRepository;
    private final ImportBatchRepository importBatchRepository;
    private final IntegrationApiKeyRepository integrationApiKeyRepository;
    private final ApiCallLogRepository apiCallLogRepository;
    private final PlatformAccountRepository platformAccountRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final AuditService auditService;
    private final AssistantService assistantService;
    private final AiConfigurationService aiConfigurationService;
    private final DeepSeekClient deepSeekClient;
    private final PasswordEncoder passwordEncoder;
    private final URI n8nKnowledgeUploadUri;
    private final URI n8nKnowledgeDeleteUri;
    private final RestClient n8nClient;

    public AdminService(
            UserAccountRepository userAccountRepository,
            KnowledgeArticleRepository knowledgeArticleRepository,
            DepartmentRepository departmentRepository,
            JobTitleRepository jobTitleRepository,
            ImportBatchRepository importBatchRepository,
            IntegrationApiKeyRepository integrationApiKeyRepository,
            ApiCallLogRepository apiCallLogRepository,
            PlatformAccountRepository platformAccountRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            AuditService auditService,
            AssistantService assistantService,
            AiConfigurationService aiConfigurationService,
            DeepSeekClient deepSeekClient,
            PasswordEncoder passwordEncoder,
            @Value("${app.knowledge.n8n-upload-url}") String n8nKnowledgeUploadUrl,
            @Value("${app.knowledge.n8n-delete-url}") String n8nKnowledgeDeleteUrl
    ) {
        this.userAccountRepository = userAccountRepository;
        this.knowledgeArticleRepository = knowledgeArticleRepository;
        this.departmentRepository = departmentRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.importBatchRepository = importBatchRepository;
        this.integrationApiKeyRepository = integrationApiKeyRepository;
        this.apiCallLogRepository = apiCallLogRepository;
        this.platformAccountRepository = platformAccountRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.auditService = auditService;
        this.assistantService = assistantService;
        this.aiConfigurationService = aiConfigurationService;
        this.deepSeekClient = deepSeekClient;
        this.passwordEncoder = passwordEncoder;
        this.n8nKnowledgeUploadUri = URI.create(n8nKnowledgeUploadUrl);
        this.n8nKnowledgeDeleteUri = URI.create(n8nKnowledgeDeleteUrl);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.n8nClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public List<AdminDtos.EmployeeView> employees(Long tenantId) {
        return userAccountRepository.findByTenantIdOrderByIdAsc(tenantId)
                .stream()
                .map(this::employeeView)
                .toList();
    }

    @Transactional
    public AdminDtos.EmployeeView createEmployee(UserAccount actor, AdminDtos.EmployeeUpsertRequest request) {
        validateOrg(actor.getTenantId(), request.department(), request.title());
        userAccountRepository.findByTenantIdAndEmployeeNo(actor.getTenantId(), request.employeeNo())
                .ifPresent(existing -> {
                    throw AppException.badRequest("Employee number already exists");
                });
        UserAccount employee = new UserAccount();
        employee.setTenantId(actor.getTenantId());
        employee.setUsername(profileUsername(actor.getTenantId(), request.employeeNo()));
        employee.setPasswordHash(passwordEncoder.encode("123456"));
        employee.setEmployeeNo(request.employeeNo());
        employee.setRole(request.role() == null ? Role.EMPLOYEE : request.role());
        applyEmployee(employee, request, actor.getTenantId());
        UserAccount saved = bindAccount(userAccountRepository.save(employee), request.accountPublicId(), actor.getTenantId());
        ensureDefaultBalances(saved);
        auditService.log(actor, "CREATE_EMPLOYEE", "user_account", saved.getId(), saved.getEmployeeNo());
        return employeeView(saved);
    }

    @Transactional
    public AdminDtos.EmployeeView updateEmployee(UserAccount actor, Long id, AdminDtos.EmployeeUpsertRequest request) {
        UserAccount employee = userAccountRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Employee not found"));
        ensureTenant(employee.getTenantId(), actor.getTenantId());
        validateOrg(actor.getTenantId(), request.department(), request.title());
        if (!employee.getEmployeeNo().equals(request.employeeNo())) {
            userAccountRepository.findByTenantIdAndEmployeeNo(actor.getTenantId(), request.employeeNo())
                    .ifPresent(existing -> {
                        throw AppException.badRequest("Employee number already exists");
                    });
            employee.setEmployeeNo(request.employeeNo());
        }
        employee.setRole(request.role() == null ? employee.getRole() : request.role());
        applyEmployee(employee, request, actor.getTenantId());
        UserAccount saved = bindAccount(userAccountRepository.save(employee), request.accountPublicId(), actor.getTenantId());
        ensureDefaultBalances(saved);
        auditService.log(actor, "UPDATE_EMPLOYEE", "user_account", saved.getId(), saved.getEmployeeNo());
        return employeeView(saved);
    }

    @Transactional
    public AdminDtos.EmployeeView resetEmployeePassword(UserAccount actor, Long id) {
        UserAccount employee = userAccountRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Employee not found"));
        ensureTenant(employee.getTenantId(), actor.getTenantId());
        if (employee.getAccountId() != null) {
            throw AppException.badRequest("该员工已绑定平台注册账号，密码只能由账号本人在个人设置中修改");
        }
        employee.setPasswordHash(passwordEncoder.encode("123456"));
        UserAccount saved = userAccountRepository.save(employee);
        auditService.log(actor, "RESET_PASSWORD", "user_account", saved.getId(), saved.getEmployeeNo());
        return employeeView(saved);
    }

    public List<LeaveDtos.BalanceView> employeeBalances(UserAccount actor, Long employeeId) {
        UserAccount employee = requireTenantEmployee(actor.getTenantId(), employeeId);
        return balanceViews(employee);
    }

    @Transactional
    public List<LeaveDtos.BalanceView> updateEmployeeBalances(
            UserAccount actor,
            Long employeeId,
            AdminDtos.EmployeeBalanceUpdateRequest request
    ) {
        UserAccount employee = requireTenantEmployee(actor.getTenantId(), employeeId);
        for (AdminDtos.BalanceUpdateItem item : request.balances()) {
            if (item.usedDays().compareTo(item.totalDays()) > 0) {
                throw AppException.badRequest(item.leaveType().getLabel() + "已用天数不能大于总额度");
            }
            LeaveBalance balance = leaveBalanceRepository
                    .findByTenantIdAndEmployeeIdAndLeaveType(actor.getTenantId(), employeeId, item.leaveType())
                    .orElseGet(() -> {
                        LeaveBalance created = new LeaveBalance();
                        created.setTenantId(actor.getTenantId());
                        created.setEmployeeId(employeeId);
                        created.setLeaveType(item.leaveType());
                        return created;
                    });
            balance.setTotalDays(item.totalDays());
            balance.setUsedDays(item.usedDays());
            leaveBalanceRepository.save(balance);
        }
        auditService.log(actor, "UPDATE_LEAVE_BALANCES", "user_account", employeeId, employee.getEmployeeNo());
        return balanceViews(employee);
    }

    public List<AdminDtos.DepartmentView> departments(Long tenantId) {
        return departmentRepository.findByTenantIdOrderByNameAsc(tenantId)
                .stream()
                .map(this::departmentView)
                .toList();
    }

    @Transactional
    public AdminDtos.DepartmentView upsertDepartment(UserAccount actor, Long id, AdminDtos.BasicConfigRequest request) {
        Department department = id == null ? new Department() : departmentRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Department not found"));
        if (id != null) {
            ensureTenant(department.getTenantId(), actor.getTenantId());
        } else {
            department.setTenantId(actor.getTenantId());
        }
        department.setName(request.name());
        department.setCode(request.code());
        department.setDescription(request.description());
        department.setActive(request.active() == null || request.active());
        Department saved = departmentRepository.save(department);
        auditService.log(actor, id == null ? "CREATE_DEPARTMENT" : "UPDATE_DEPARTMENT", "department", saved.getId(), saved.getName());
        return departmentView(saved);
    }

    public List<AdminDtos.JobTitleView> jobTitles(Long tenantId) {
        return jobTitleRepository.findByTenantIdOrderByNameAsc(tenantId)
                .stream()
                .map(this::jobTitleView)
                .toList();
    }

    @Transactional
    public AdminDtos.JobTitleView upsertJobTitle(UserAccount actor, Long id, AdminDtos.BasicConfigRequest request) {
        JobTitle jobTitle = id == null ? new JobTitle() : jobTitleRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Job title not found"));
        if (id != null) {
            ensureTenant(jobTitle.getTenantId(), actor.getTenantId());
        } else {
            jobTitle.setTenantId(actor.getTenantId());
        }
        jobTitle.setName(request.name());
        jobTitle.setCode(request.code());
        jobTitle.setDescription(request.description());
        jobTitle.setActive(request.active() == null || request.active());
        JobTitle saved = jobTitleRepository.save(jobTitle);
        auditService.log(actor, id == null ? "CREATE_JOB_TITLE" : "UPDATE_JOB_TITLE", "job_title", saved.getId(), saved.getName());
        return jobTitleView(saved);
    }

    public List<KnowledgeArticle> knowledge(Long tenantId, boolean includeDrafts) {
        return knowledgeArticleRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .filter(article -> includeDrafts || "APPROVED".equalsIgnoreCase(article.getReviewStatus()))
                .toList();
    }

    @Transactional
    public KnowledgeArticle createKnowledge(UserAccount actor, AdminDtos.KnowledgeUpsertRequest request) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setTenantId(actor.getTenantId());
        apply(article, request);
        KnowledgeArticle saved = knowledgeArticleRepository.save(article);
        auditService.log(actor, "CREATE_KNOWLEDGE", "knowledge_article", saved.getId(), saved.getTitle());
        return saved;
    }

    @Transactional
    public KnowledgeArticle uploadKnowledge(
            UserAccount actor,
            MultipartFile file,
            String category,
            String source,
            String region,
            Long articleId
    ) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("请选择要导入的知识库文件");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        String extension = extensionOf(fileName);
        if (!List.of("pdf", "txt", "csv", "xlsx").contains(extension)) {
            throw AppException.badRequest("当前第一版支持 PDF、TXT、CSV、XLSX 文件");
        }
        if (category == null || category.isBlank()) {
            throw AppException.badRequest("请填写知识库分类");
        }

        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("data", resource)
                    .filename(fileName)
                    .contentType(contentTypeOf(extension));
            n8nClient.post()
                    .uri(n8nKnowledgeUploadUri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (IOException exception) {
            rollbackKnowledgeArtifacts(fileName);
            throw new AppException(HttpStatus.BAD_GATEWAY, "读取文件失败，文件没有写入 n8n 知识库");
        } catch (RestClientException exception) {
            rollbackKnowledgeArtifacts(fileName);
            if ("pdf".equals(extension)) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "PDF 无法提取可检索文字，可能是扫描件或图片型 PDF，请先进行 OCR 后再上传"
                );
            }
            throw new AppException(HttpStatus.BAD_GATEWAY, "n8n 知识库导入失败，请检查 n8n 和 RAG 工作流是否正在运行");
        }

        KnowledgeArticle article = articleId == null
                ? new KnowledgeArticle()
                : knowledgeArticleRepository.findById(articleId)
                .orElseThrow(() -> AppException.notFound("Knowledge article not found"));
        if (articleId != null) {
            ensureTenant(article.getTenantId(), actor.getTenantId());
        } else {
            article.setTenantId(actor.getTenantId());
        }
        article.setCategory(category.trim());
        article.setTitle(fileName);
        article.setContent("该文件已同步到 n8n RAG 知识库，正文以 n8n 检索结果为准。");
        article.setSource(source == null || source.isBlank() ? "SaaS 知识库上传" : source.trim());
        article.setRegion(region == null || region.isBlank() ? "全国" : region.trim());
        article.setUpdatedAt(LocalDate.now());
        article.setReviewStatus("APPROVED");
        KnowledgeArticle saved = knowledgeArticleRepository.save(article);
        auditService.log(actor, articleId == null ? "UPLOAD_KNOWLEDGE" : "REINDEX_KNOWLEDGE", "knowledge_article", saved.getId(), saved.getTitle());
        return saved;
    }

    @Transactional
    public KnowledgeArticle updateKnowledge(UserAccount actor, Long id, AdminDtos.KnowledgeUpsertRequest request) {
        KnowledgeArticle article = knowledgeArticleRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Knowledge article not found"));
        ensureTenant(article.getTenantId(), actor.getTenantId());
        apply(article, request);
        KnowledgeArticle saved = knowledgeArticleRepository.save(article);
        auditService.log(actor, "UPDATE_KNOWLEDGE", "knowledge_article", saved.getId(), saved.getTitle());
        return saved;
    }

    @Transactional
    public void deleteKnowledge(UserAccount actor, Long id) {
        KnowledgeArticle article = knowledgeArticleRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Knowledge article not found"));
        ensureTenant(article.getTenantId(), actor.getTenantId());
        try {
            n8nClient.post()
                    .uri(n8nKnowledgeDeleteUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("fileName", article.getTitle()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "n8n 知识库删除失败，SaaS 记录未删除");
        }
        knowledgeArticleRepository.delete(article);
        auditService.log(actor, "DELETE_KNOWLEDGE", "knowledge_article", id, article.getTitle());
    }

    private void rollbackKnowledgeArtifacts(String fileName) {
        try {
            n8nClient.post()
                    .uri(n8nKnowledgeDeleteUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fileName", fileName))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ignored) {
            // The original import error is more useful to the caller than a cleanup error.
        }
    }

    public List<AdminDtos.ImportBatchView> importBatches(Long tenantId) {
        return importBatchRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(batch -> new AdminDtos.ImportBatchView(
                        batch.getId(),
                        batch.getImportType(),
                        batch.getFileName(),
                        batch.getTotalRows(),
                        batch.getSuccessRows(),
                        batch.getFailedRows(),
                        batch.getStatus(),
                        batch.getMessage(),
                        batch.getCreatedAt()
                ))
                .toList();
    }

    public List<AuditLog> auditLogs(Long tenantId) {
        return auditService.latest(tenantId);
    }

    public List<AiCallRecord> aiCalls(Long tenantId) {
        return assistantService.latestCalls(tenantId);
    }

    public AdminDtos.AiConfigView aiConfig(Long tenantId) {
        return aiConfigurationService.view(tenantId);
    }

    public AdminDtos.AiConfigView updateAiConfig(UserAccount actor, AdminDtos.AiConfigUpdateRequest request) {
        return aiConfigurationService.update(actor, request);
    }

    public AdminDtos.AiConfigTestResult testAiConfig(UserAccount actor) {
        try {
            DeepSeekClient.ConnectionTest result = deepSeekClient.testConnection(actor.getTenantId());
            return new AdminDtos.AiConfigTestResult(
                    true,
                    "连接成功",
                    result.provider(),
                    result.model(),
                    result.latencyMs()
            );
        } catch (Exception exception) {
            return new AdminDtos.AiConfigTestResult(
                    false,
                    exception.getMessage(),
                    "DEEPSEEK",
                    aiConfigurationService.view(actor.getTenantId()).model(),
                    0
            );
        }
    }

    public List<AdminDtos.ApiKeyView> apiKeys(Long tenantId) {
        return integrationApiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(key -> new AdminDtos.ApiKeyView(
                        key.getId(),
                        key.getName(),
                        key.getKeyPrefix(),
                        key.isActive(),
                        key.getCreatedAt(),
                        key.getLastUsedAt()
                ))
                .toList();
    }

    @Transactional
    public AdminDtos.ApiKeyCreateResponse createApiKey(UserAccount actor, AdminDtos.ApiKeyCreateRequest request) {
        String rawKey = "hra_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String prefix = rawKey.substring(0, 12);
        IntegrationApiKey key = new IntegrationApiKey();
        key.setTenantId(actor.getTenantId());
        key.setName(request.name());
        key.setKeyPrefix(prefix);
        key.setKeyHash(hash(rawKey));
        IntegrationApiKey saved = integrationApiKeyRepository.save(key);
        auditService.log(actor, "CREATE_API_KEY", "integration_api_key", saved.getId(), saved.getName());
        return new AdminDtos.ApiKeyCreateResponse(saved.getId(), saved.getName(), rawKey, saved.getKeyPrefix());
    }

    @Transactional
    public AdminDtos.ApiKeyView setApiKeyActive(UserAccount actor, Long id, boolean active) {
        IntegrationApiKey key = integrationApiKeyRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("API key not found"));
        ensureTenant(key.getTenantId(), actor.getTenantId());
        key.setActive(active);
        IntegrationApiKey saved = integrationApiKeyRepository.save(key);
        auditService.log(actor, active ? "ENABLE_API_KEY" : "DISABLE_API_KEY", "integration_api_key", id, saved.getName());
        return new AdminDtos.ApiKeyView(saved.getId(), saved.getName(), saved.getKeyPrefix(), saved.isActive(), saved.getCreatedAt(), saved.getLastUsedAt());
    }

    public List<AdminDtos.ApiCallLogView> apiCallLogs(Long tenantId) {
        return apiCallLogRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(log -> new AdminDtos.ApiCallLogView(
                        log.getId(),
                        log.getApiKeyId(),
                        log.getMethod(),
                        log.getPath(),
                        log.getStatusCode(),
                        log.getMessage(),
                        log.getCreatedAt()
                ))
                .toList();
    }

    private void applyEmployee(UserAccount employee, AdminDtos.EmployeeUpsertRequest request, Long tenantId) {
        employee.setName(request.name());
        employee.setPhone(request.phone());
        employee.setEmail(request.email());
        employee.setDepartment(request.department());
        employee.setTitle(request.title());
        employee.setEntryDate(request.entryDate());
        EmployeeStatus status = parseStatus(request.employeeStatus());
        employee.setEmployeeStatus(status);
        employee.setActive(request.active() == null ? status == EmployeeStatus.ACTIVE : request.active());
        if (request.managerEmployeeNo() == null || request.managerEmployeeNo().isBlank()) {
            employee.setManagerId(null);
        } else {
            UserAccount manager = userAccountRepository.findByTenantIdAndEmployeeNo(tenantId, request.managerEmployeeNo())
                    .orElseThrow(() -> AppException.badRequest("Manager employee number not found: " + request.managerEmployeeNo()));
            if (employee.getId() != null && employee.getId().equals(manager.getId())) {
                throw AppException.badRequest("员工不能设置自己为直属主管");
            }
            if (manager.getRole() != Role.MANAGER && manager.getRole() != Role.HR) {
                throw AppException.badRequest("直属主管必须具有主管或空间管理员角色");
            }
            employee.setManagerId(manager.getId());
        }
    }

    private UserAccount bindAccount(UserAccount employee, String publicId, Long tenantId) {
        if (publicId != null && !publicId.isBlank()) {
            PlatformAccount account = platformAccountRepository.findByPublicId(publicId.trim().toUpperCase())
                    .orElseThrow(() -> AppException.badRequest("注册账号ID不存在: " + publicId));
            WorkspaceMembership membership = workspaceMembershipRepository
                    .findByAccountIdAndWorkspaceId(account.getId(), tenantId)
                    .orElseThrow(() -> AppException.badRequest("该账号尚未申请加入当前空间"));
            if (membership.getStatus() != MembershipStatus.PENDING_PROFILE
                    && membership.getStatus() != MembershipStatus.ACTIVE) {
                throw AppException.badRequest("该账号尚未通过加入审核");
            }
            userAccountRepository.findByTenantIdAndAccountId(tenantId, account.getId())
                    .filter(existing -> !existing.getId().equals(employee.getId()))
                    .ifPresent(existing -> {
                        throw AppException.badRequest("该账号已绑定工号: " + existing.getEmployeeNo());
                    });
            if (employee.getAccountId() != null && !employee.getAccountId().equals(account.getId())) {
                throw AppException.badRequest("该员工档案已经绑定其他注册账号");
            }
            employee.setAccountId(account.getId());
            UserAccount savedEmployee = userAccountRepository.save(employee);
            membership.setEmployeeProfileId(savedEmployee.getId());
            membership.setRole(savedEmployee.getRole());
            membership.setStatus(MembershipStatus.ACTIVE);
            workspaceMembershipRepository.save(membership);
            return savedEmployee;
        }
        if (employee.getAccountId() != null) {
            workspaceMembershipRepository.findByAccountIdAndWorkspaceId(employee.getAccountId(), tenantId)
                    .ifPresent(membership -> {
                        membership.setEmployeeProfileId(employee.getId());
                        membership.setRole(employee.getRole());
                        membership.setStatus(MembershipStatus.ACTIVE);
                        workspaceMembershipRepository.save(membership);
                    });
        }
        return employee;
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

    private UserAccount requireTenantEmployee(Long tenantId, Long employeeId) {
        return userAccountRepository.findById(employeeId)
                .filter(employee -> employee.getTenantId().equals(tenantId))
                .orElseThrow(() -> AppException.notFound("员工档案不存在"));
    }

    private List<LeaveDtos.BalanceView> balanceViews(UserAccount employee) {
        return leaveBalanceRepository
                .findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(employee.getTenantId(), employee.getId())
                .stream()
                .map(balance -> new LeaveDtos.BalanceView(
                        balance.getLeaveType(),
                        balance.getLeaveType().getLabel(),
                        balance.getTotalDays(),
                        balance.getUsedDays(),
                        balance.remainingDays()
                ))
                .toList();
    }

    private void validateOrg(Long tenantId, String department, String title) {
        departmentRepository.findByTenantIdAndName(tenantId, department)
                .filter(Department::isActive)
                .orElseThrow(() -> AppException.badRequest("Department not found or inactive: " + department));
        jobTitleRepository.findByTenantIdAndName(tenantId, title)
                .filter(JobTitle::isActive)
                .orElseThrow(() -> AppException.badRequest("Job title not found or inactive: " + title));
    }

    private EmployeeStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return EmployeeStatus.ACTIVE;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.equals("LEFT") || normalized.equals("LEAVE") || normalized.equals("RESIGNED") || normalized.equals("OFFBOARD")) {
            return EmployeeStatus.LEFT;
        }
        if (normalized.equals("INACTIVE") || normalized.equals("DISABLED") || normalized.equals("STOPPED")) {
            return EmployeeStatus.INACTIVE;
        }
        return EmployeeStatus.ACTIVE;
    }

    public AdminDtos.EmployeeView employeeView(UserAccount user) {
        String managerName = null;
        if (user.getManagerId() != null) {
            managerName = userAccountRepository.findById(user.getManagerId()).map(UserAccount::getName).orElse(null);
        }
        return new AdminDtos.EmployeeView(
                user.getId(),
                user.getEmployeeNo(),
                user.getUsername(),
                user.getAccountId() == null ? null : platformAccountRepository.findById(user.getAccountId())
                        .map(PlatformAccount::getPublicId)
                        .orElse(null),
                user.getName(),
                user.getRole(),
                user.getDepartment(),
                user.getTitle(),
                user.getEmail(),
                user.getPhone(),
                user.getEntryDate(),
                (user.getEmployeeStatus() == null ? EmployeeStatus.ACTIVE : user.getEmployeeStatus()).name(),
                user.getManagerId(),
                managerName,
                user.isActive()
        );
    }

    private AdminDtos.DepartmentView departmentView(Department department) {
        return new AdminDtos.DepartmentView(
                department.getId(),
                department.getName(),
                department.getCode(),
                department.getDescription(),
                department.isActive(),
                department.getCreatedAt()
        );
    }

    private AdminDtos.JobTitleView jobTitleView(JobTitle jobTitle) {
        return new AdminDtos.JobTitleView(
                jobTitle.getId(),
                jobTitle.getName(),
                jobTitle.getCode(),
                jobTitle.getDescription(),
                jobTitle.isActive(),
                jobTitle.getCreatedAt()
        );
    }

    private void apply(KnowledgeArticle article, AdminDtos.KnowledgeUpsertRequest request) {
        article.setCategory(request.category());
        article.setTitle(request.title());
        article.setContent(request.content());
        article.setSource(request.source());
        article.setRegion(request.region());
        article.setPublishedAt(request.publishedAt());
        article.setUpdatedAt(request.updatedAt() == null ? LocalDate.now() : request.updatedAt());
        article.setReviewStatus(request.reviewStatus() == null || request.reviewStatus().isBlank()
                ? "PENDING_REVIEW"
                : request.reviewStatus());
    }

    private String safeFileName(String originalName) {
        String candidate = originalName == null || originalName.isBlank() ? "knowledge-upload" : originalName;
        candidate = candidate.replace('\\', '/');
        int slash = candidate.lastIndexOf('/');
        candidate = slash >= 0 ? candidate.substring(slash + 1) : candidate;
        candidate = candidate.replace("..", "_").trim();
        return candidate.isBlank() ? "knowledge-upload" : candidate;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private MediaType contentTypeOf(String extension) {
        return switch (extension) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "csv" -> MediaType.parseMediaType("text/csv");
            case "xlsx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            default -> MediaType.TEXT_PLAIN;
        };
    }

    private void ensureTenant(Long actual, Long expected) {
        if (!actual.equals(expected)) {
            throw AppException.notFound("Resource not found");
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

    private String profileUsername(Long tenantId, String employeeNo) {
        return employeeNo + "." + tenantId;
    }
}
