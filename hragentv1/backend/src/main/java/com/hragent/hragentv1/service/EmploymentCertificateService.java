package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmploymentCertificateDtos;
import com.hragent.hragentv1.repo.EmployeePersonalProfileRepository;
import com.hragent.hragentv1.repo.EmploymentCertificateRequestRepository;
import com.hragent.hragentv1.repo.EmploymentCertificateTemplateRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EmploymentCertificateService {
    private final EmploymentCertificateRequestRepository requestRepository;
    private final EmployeePersonalProfileRepository profileRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditService auditService;
    private final EmploymentCertificateDocumentService documentService;
    private final EmploymentCertificateTemplateRepository templateRepository;
    private final EmploymentCertificateTemplateService templateService;
    private final VisaCertificateDocumentService visaDocumentService;

    public EmploymentCertificateService(
            EmploymentCertificateRequestRepository requestRepository,
            EmployeePersonalProfileRepository profileRepository,
            UserAccountRepository userAccountRepository,
            AuditService auditService,
            EmploymentCertificateDocumentService documentService,
            EmploymentCertificateTemplateRepository templateRepository,
            EmploymentCertificateTemplateService templateService,
            VisaCertificateDocumentService visaDocumentService
    ) {
        this.requestRepository = requestRepository;
        this.profileRepository = profileRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditService = auditService;
        this.documentService = documentService;
        this.templateRepository = templateRepository;
        this.templateService = templateService;
        this.visaDocumentService = visaDocumentService;
    }

    public EmploymentCertificateDtos.FormOptions options() {
        return new EmploymentCertificateDtos.FormOptions(
                List.of(EmploymentCertificateType.values()).stream()
                        .map(value -> new EmploymentCertificateDtos.Option<>(value, value.getLabel()))
                        .toList(),
                List.of(CertificateLanguage.values()).stream()
                        .map(value -> new EmploymentCertificateDtos.Option<>(value, value.getLabel()))
                        .toList()
        );
    }

    @Transactional
    public EmploymentCertificateDtos.RequestView create(
            UserAccount actor,
            EmploymentCertificateDtos.CreateRequest input
    ) {
        validateCreate(input);
        requireActiveEmployee(actor);
        EmploymentCertificateRequest request = buildRequest(actor, input);
        bindRequestedTemplate(actor, input, request);
        EmploymentCertificateRequest saved = requestRepository.save(request);
        auditCreate(actor, saved, saved.getRequestedTemplateId() != null);
        return view(saved, actor);
    }

    @Transactional
    public EmploymentCertificateDtos.RequestView createWithTemplate(
            UserAccount actor,
            EmploymentCertificateDtos.CreateRequest input,
            MultipartFile file,
            String templateName
    ) {
        validateCreate(input);
        requireActiveEmployee(actor);
        if (input.certificateType() != EmploymentCertificateType.VISA) {
            throw AppException.badRequest("员工上传的专用模板只用于出境或签证在职证明");
        }

        EmploymentCertificateRequest request = requestRepository.save(buildRequest(actor, input));
        EmploymentCertificateTemplate template = templateService.uploadProposal(
                actor,
                file,
                templateName,
                input.destinationCountry(),
                input.consulateName(),
                input.language()
        );
        request.setRequestedTemplateId(template.getId());
        request.setRequestedTemplateFileName(template.getSourceFileName());
        EmploymentCertificateRequest saved = requestRepository.save(request);
        auditCreate(actor, saved, true);
        return view(saved, actor);
    }

    @Transactional(readOnly = true)
    public List<EmploymentCertificateDtos.RequestView> mine(UserAccount actor) {
        return requestRepository.findByTenantIdAndEmployeeIdOrderBySubmittedAtDesc(actor.getTenantId(), actor.getId())
                .stream()
                .map(request -> view(request, actor))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmploymentCertificateDtos.RequestView> hrAll(UserAccount actor) {
        requireHr(actor);
        return views(actor.getTenantId(), requestRepository.findByTenantIdOrderBySubmittedAtDesc(actor.getTenantId()));
    }

    @Transactional(readOnly = true)
    public List<EmploymentCertificateDtos.RequestView> hrPending(UserAccount actor) {
        requireHr(actor);
        return views(
                actor.getTenantId(),
                requestRepository.findByTenantIdAndStatusOrderBySubmittedAtDesc(
                        actor.getTenantId(),
                        CertificateRequestStatus.PENDING_HR
                )
        );
    }

    @Transactional
    public EmploymentCertificateDtos.RequestView cancel(UserAccount actor, Long requestId) {
        EmploymentCertificateRequest request = requireRequest(actor.getTenantId(), requestId);
        if (!request.getEmployeeId().equals(actor.getId())) {
            throw AppException.forbidden("只能取消自己的证明申请");
        }
        if (request.getStatus() != CertificateRequestStatus.PENDING_HR) {
            throw AppException.badRequest("只有待 HR 审核的申请可以取消");
        }
        if (request.getRequestedTemplateId() != null) {
            templateService.cancelProposal(actor, request.getRequestedTemplateId());
        }
        request.setStatus(CertificateRequestStatus.CANCELLED);
        EmploymentCertificateRequest saved = requestRepository.save(request);
        auditService.log(
                actor,
                "CANCEL_CERTIFICATE_REQUEST",
                "EMPLOYMENT_CERTIFICATE_REQUEST",
                saved.getId(),
                "取消在职证明申请"
        );
        return view(saved, actor);
    }

    @Transactional
    public EmploymentCertificateDtos.RequestView review(
            UserAccount actor,
            Long requestId,
            EmploymentCertificateDtos.ReviewRequest input
    ) {
        requireHr(actor);
        EmploymentCertificateRequest request = requireRequest(actor.getTenantId(), requestId);
        if (request.getStatus() != CertificateRequestStatus.PENDING_HR) {
            throw AppException.badRequest("该申请已经处理，请刷新列表");
        }
        UserAccount employee = requireEmployee(actor.getTenantId(), request.getEmployeeId());
        if (input.approved()) {
            List<String> missingFields = missingProfileFields(request, employee);
            if (!missingFields.isEmpty()) {
                throw AppException.badRequest("员工档案不完整：" + String.join("、", missingFields));
            }
        }

        EmploymentCertificateTemplate requestedTemplate = null;
        if (request.getRequestedTemplateId() != null) {
            requestedTemplate = templateService.reviewProposal(
                    actor,
                    request.getRequestedTemplateId(),
                    request.getEmployeeId(),
                    input.approved(),
                    input.opinion()
            );
        }

        request.setStatus(input.approved()
                ? CertificateRequestStatus.APPROVED
                : CertificateRequestStatus.REJECTED);
        request.setHrOpinion(clean(input.opinion()));
        request.setReviewedByEmployeeId(actor.getId());
        request.setReviewedAt(LocalDateTime.now());

        if (input.approved()) {
            EmployeePersonalProfile profile = profileRepository
                    .findByTenantIdAndEmployeeId(actor.getTenantId(), employee.getId())
                    .orElseThrow(() -> AppException.badRequest("员工个人档案未维护"));
            if (request.getCertificateType() == EmploymentCertificateType.STANDARD
                    && request.getLanguage() == CertificateLanguage.CHINESE) {
                generateStandard(request, employee, profile);
            } else if (request.getCertificateType() == EmploymentCertificateType.VISA) {
                generateVisa(request, employee, profile, requestedTemplate);
            }
        }
        EmploymentCertificateRequest saved = requestRepository.save(request);
        auditService.log(
                actor,
                input.approved() ? "APPROVE_CERTIFICATE_REQUEST" : "REJECT_CERTIFICATE_REQUEST",
                "EMPLOYMENT_CERTIFICATE_REQUEST",
                saved.getId(),
                (input.approved() ? "通过" : "驳回") + "在职证明申请：" + employee.getEmployeeNo()
        );
        return view(saved, employee);
    }

    @Transactional
    public EmploymentCertificateDtos.RequestView retryGeneration(UserAccount actor, Long requestId) {
        requireHr(actor);
        EmploymentCertificateRequest request = requireRequest(actor.getTenantId(), requestId);
        if (request.getCertificateType() != EmploymentCertificateType.VISA) {
            throw AppException.badRequest("当前只支持重新生成签证/领事馆证明");
        }
        if (request.getStatus() != CertificateRequestStatus.APPROVED
                && request.getStatus() != CertificateRequestStatus.GENERATION_FAILED) {
            throw AppException.badRequest("只有审核通过待生成或生成失败的申请可以重新生成");
        }

        UserAccount employee = requireEmployee(actor.getTenantId(), request.getEmployeeId());
        List<String> missingFields = missingProfileFields(request, employee);
        if (!missingFields.isEmpty()) {
            throw AppException.badRequest("员工档案不完整：" + String.join("、", missingFields));
        }
        EmployeePersonalProfile profile = profileRepository
                .findByTenantIdAndEmployeeId(actor.getTenantId(), employee.getId())
                .orElseThrow(() -> AppException.badRequest("员工个人档案未维护"));

        request.setStatus(CertificateRequestStatus.APPROVED);
        request.setSourceTemplateFileName(null);
        request.setSourceTemplateStorageKey(null);
        request.setGeneratedFileName(null);
        request.setGeneratedFileStorageKey(null);
        request.setGeneratedAt(null);
        request.setGenerationError(null);
        generateVisa(request, employee, profile, null);
        EmploymentCertificateRequest saved = requestRepository.save(request);
        auditService.log(
                actor,
                "RETRY_CERTIFICATE_GENERATION",
                "EMPLOYMENT_CERTIFICATE_REQUEST",
                saved.getId(),
                "重新生成签证在职证明：" + employee.getEmployeeNo()
        );
        return view(saved, employee);
    }

    private void generateStandard(
            EmploymentCertificateRequest request,
            UserAccount employee,
            EmployeePersonalProfile profile
    ) {
        try {
            EmploymentCertificateDocumentService.GeneratedDocument generated =
                    documentService.generateStandardChinese(request, employee, profile);
            markGenerated(request, generated);
        } catch (RuntimeException exception) {
            markGenerationFailed(request, exception);
        }
    }

    private void generateVisa(
            EmploymentCertificateRequest request,
            UserAccount employee,
            EmployeePersonalProfile profile,
            EmploymentCertificateTemplate reviewedTemplate
    ) {
        EmploymentCertificateTemplate template;
        if (request.getRequestedTemplateId() != null) {
            template = reviewedTemplate != null
                    ? reviewedTemplate
                    : templateRepository.findByIdAndTenantId(
                            request.getRequestedTemplateId(), request.getTenantId()
                    ).filter(this::isApprovedAndActive).orElse(null);
        } else {
            template = templateRepository
                    .findFirstByTenantIdAndDestinationCountryIgnoreCaseAndConsulateNameIgnoreCaseAndLanguageAndActiveTrueOrderByUpdatedAtDesc(
                            request.getTenantId(),
                            request.getDestinationCountry(),
                            request.getConsulateName(),
                            request.getLanguage()
                    )
                    .filter(this::isApprovedAndActive)
                    .orElse(null);
        }
        if (template == null) {
            request.setGenerationError(request.getRequestedTemplateId() == null
                    ? "未找到与目的国家、受理机构和语言完全匹配的启用模板，请 HR 上传或启用模板后重新处理"
                    : "员工提交的模板尚未通过审核或已停用");
            return;
        }

        request.setSourceTemplateFileName(template.getSourceFileName());
        request.setSourceTemplateStorageKey(template.getStorageKey());
        try {
            EmploymentCertificateDocumentService.GeneratedDocument generated =
                    visaDocumentService.generate(template, request, employee, profile);
            markGenerated(request, generated);
        } catch (RuntimeException exception) {
            markGenerationFailed(request, exception);
        }
    }

    private void markGenerated(
            EmploymentCertificateRequest request,
            EmploymentCertificateDocumentService.GeneratedDocument generated
    ) {
        request.setGeneratedFileName(generated.fileName());
        request.setGeneratedFileStorageKey(generated.storageKey());
        request.setGeneratedAt(LocalDateTime.now());
        request.setGenerationError(null);
        request.setStatus(CertificateRequestStatus.GENERATED);
    }

    private void markGenerationFailed(EmploymentCertificateRequest request, RuntimeException exception) {
        request.setStatus(CertificateRequestStatus.GENERATION_FAILED);
        request.setGenerationError(limit(exception.getMessage(), 1000));
    }

    @Transactional
    public DocumentDownload download(UserAccount actor, Long requestId) {
        EmploymentCertificateRequest request = requireRequest(actor.getTenantId(), requestId);
        if (!request.getEmployeeId().equals(actor.getId()) && actor.getRole() != Role.HR) {
            throw AppException.forbidden("只能下载自己的在职证明");
        }
        if (request.getStatus() != CertificateRequestStatus.GENERATED
                || isBlank(request.getGeneratedFileStorageKey())) {
            throw AppException.badRequest("证明文件尚未生成");
        }
        byte[] content = documentService.read(request.getGeneratedFileStorageKey());
        auditService.log(
                actor,
                "DOWNLOAD_CERTIFICATE_DOCUMENT",
                "EMPLOYMENT_CERTIFICATE_REQUEST",
                request.getId(),
                "下载在职证明文件"
        );
        return new DocumentDownload(request.getGeneratedFileName(), content);
    }

    private List<EmploymentCertificateDtos.RequestView> views(
            Long tenantId,
            List<EmploymentCertificateRequest> requests
    ) {
        Map<Long, UserAccount> employees = userAccountRepository.findByTenantIdOrderByIdAsc(tenantId).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        return requests.stream()
                .map(request -> view(request, employees.get(request.getEmployeeId())))
                .toList();
    }

    private EmploymentCertificateDtos.RequestView view(
            EmploymentCertificateRequest request,
            UserAccount employee
    ) {
        UserAccount resolvedEmployee = employee == null
                ? requireEmployee(request.getTenantId(), request.getEmployeeId())
                : employee;
        List<String> missingFields = missingProfileFields(request, resolvedEmployee);
        return new EmploymentCertificateDtos.RequestView(
                request.getId(),
                resolvedEmployee.getId(),
                resolvedEmployee.getEmployeeNo(),
                resolvedEmployee.getName(),
                resolvedEmployee.getDepartment(),
                resolvedEmployee.getTitle(),
                request.getCertificateType(),
                request.getCertificateType().getLabel(),
                request.getLanguage(),
                request.getLanguage().getLabel(),
                request.getPurpose(),
                request.getDestinationCountry(),
                request.getConsulateName(),
                request.isIncludeSalary(),
                request.getRemarks(),
                request.getStatus(),
                request.getStatus().getLabel(),
                request.getHrOpinion(),
                request.getSubmittedAt(),
                request.getReviewedAt(),
                missingFields.isEmpty(),
                missingFields,
                request.getRequestedTemplateId(),
                request.getRequestedTemplateFileName(),
                request.getSourceTemplateFileName(),
                request.getGeneratedFileName(),
                request.getGenerationError(),
                request.getGeneratedAt(),
                request.getStatus() == CertificateRequestStatus.PENDING_HR,
                request.getStatus() == CertificateRequestStatus.GENERATED
                        && request.getGeneratedFileStorageKey() != null
        );
    }

    private List<String> missingProfileFields(
            EmploymentCertificateRequest request,
            UserAccount employee
    ) {
        List<String> missing = new ArrayList<>();
        EmployeePersonalProfile profile = profileRepository
                .findByTenantIdAndEmployeeId(employee.getTenantId(), employee.getId())
                .orElse(null);
        if (profile == null || isBlank(profile.getLegalName())) missing.add("法定姓名");
        if (isBlank(employee.getDepartment())) missing.add("部门");
        if (isBlank(employee.getTitle())) missing.add("岗位");
        if (employee.getEntryDate() == null && (profile == null || profile.getContractStartDate() == null)) {
            missing.add("入职日期");
        }
        if (request.getCertificateType() == EmploymentCertificateType.VISA) {
            if (profile == null || isBlank(profile.getEnglishName())) missing.add("英文姓名");
            if (profile == null || isBlank(profile.getPassportNumberEncrypted())) missing.add("护照号码");
            if (profile == null || profile.getPassportExpiryDate() == null) missing.add("护照有效期");
        }
        if (request.isIncludeSalary() && (profile == null || profile.getMonthlySalary() == null)) {
            missing.add("薪资信息");
        }
        return missing;
    }

    private void validateCreate(EmploymentCertificateDtos.CreateRequest input) {
        if (input == null || input.certificateType() == null || input.language() == null) {
            throw AppException.badRequest("请选择证明类型和文件语言");
        }
        if (isBlank(input.purpose())) {
            throw AppException.badRequest("请填写证明用途");
        }
        if (input.purpose().trim().length() > 200) {
            throw AppException.badRequest("证明用途不能超过 200 个字符");
        }
        if (input.remarks() != null && input.remarks().trim().length() > 600) {
            throw AppException.badRequest("补充说明不能超过 600 个字符");
        }
        if (input.certificateType() == EmploymentCertificateType.VISA) {
            if (isBlank(input.destinationCountry())) {
                throw AppException.badRequest("签证证明需要填写目的国家或地区");
            }
            if (isBlank(input.consulateName())) {
                throw AppException.badRequest("签证证明需要填写领事馆或受理机构");
            }
            if (input.destinationCountry().trim().length() > 100) {
                throw AppException.badRequest("目的国家或地区不能超过 100 个字符");
            }
            if (input.consulateName().trim().length() > 160) {
                throw AppException.badRequest("领事馆或受理机构不能超过 160 个字符");
            }
        }
    }

    private EmploymentCertificateRequest buildRequest(
            UserAccount actor,
            EmploymentCertificateDtos.CreateRequest input
    ) {
        EmploymentCertificateRequest request = new EmploymentCertificateRequest();
        request.setTenantId(actor.getTenantId());
        request.setEmployeeId(actor.getId());
        request.setCertificateType(input.certificateType());
        request.setLanguage(input.language());
        request.setPurpose(clean(input.purpose()));
        request.setDestinationCountry(input.certificateType() == EmploymentCertificateType.VISA
                ? clean(input.destinationCountry()) : null);
        request.setConsulateName(input.certificateType() == EmploymentCertificateType.VISA
                ? clean(input.consulateName()) : null);
        request.setIncludeSalary(input.includeSalary());
        request.setRemarks(clean(input.remarks()));
        request.setStatus(CertificateRequestStatus.PENDING_HR);
        request.setSubmittedAt(LocalDateTime.now());
        return request;
    }

    private void requireActiveEmployee(UserAccount actor) {
        if (actor.getEmployeeStatus() != null && actor.getEmployeeStatus() != EmployeeStatus.ACTIVE) {
            throw AppException.badRequest("只有在职员工可以申请在职证明");
        }
    }

    private void auditCreate(
            UserAccount actor,
            EmploymentCertificateRequest request,
            boolean withEmployeeTemplate
    ) {
        auditService.log(
                actor,
                "CREATE_CERTIFICATE_REQUEST",
                "EMPLOYMENT_CERTIFICATE_REQUEST",
                request.getId(),
                "提交在职证明申请：" + request.getCertificateType().getLabel()
                        + (withEmployeeTemplate ? "（附员工模板）" : "")
        );
    }

    private boolean isApprovedAndActive(EmploymentCertificateTemplate template) {
        return template.isActive()
                && (template.getReviewStatus() == null
                || template.getReviewStatus() == CertificateTemplateReviewStatus.APPROVED);
    }

    private void bindRequestedTemplate(
            UserAccount actor,
            EmploymentCertificateDtos.CreateRequest input,
            EmploymentCertificateRequest request
    ) {
        Long templateId = input.requestedTemplateId();
        if (templateId == null) {
            return;
        }
        if (input.certificateType() != EmploymentCertificateType.VISA) {
            throw AppException.badRequest("员工上传的专用模板只能用于出境或签证在职证明");
        }

        EmploymentCertificateTemplate template = templateRepository
                .findByIdAndTenantId(templateId, actor.getTenantId())
                .orElseThrow(() -> AppException.notFound("指定的证明模板不存在"));
        if (!actor.getId().equals(template.getUploadedByEmployeeId())) {
            throw AppException.forbidden("只能使用当前员工本人上传的证明模板");
        }
        CertificateTemplateReviewStatus status = template.getReviewStatus();
        if (status != null
                && status != CertificateTemplateReviewStatus.PENDING
                && status != CertificateTemplateReviewStatus.APPROVED) {
            throw AppException.badRequest("该证明模板已被驳回或取消，请重新上传");
        }

        request.setRequestedTemplateId(template.getId());
        request.setRequestedTemplateFileName(template.getSourceFileName());
    }

    private EmploymentCertificateRequest requireRequest(Long tenantId, Long requestId) {
        return requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> AppException.notFound("证明申请不存在"));
    }

    private UserAccount requireEmployee(Long tenantId, Long employeeId) {
        return userAccountRepository.findById(employeeId)
                .filter(employee -> employee.getTenantId().equals(tenantId))
                .orElseThrow(() -> AppException.notFound("员工档案不存在"));
    }

    private void requireHr(UserAccount actor) {
        if (actor.getRole() != Role.HR) {
            throw AppException.forbidden("只有空间管理员可以审核在职证明");
        }
    }

    private String clean(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String limit(String value, int maxLength) {
        String message = isBlank(value) ? "未知生成错误" : value.trim();
        return message.substring(0, Math.min(maxLength, message.length()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record DocumentDownload(String fileName, byte[] content) {
    }
}
