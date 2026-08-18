package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.CertificateLanguage;
import com.hragent.hragentv1.domain.CertificateTemplateReviewStatus;
import com.hragent.hragentv1.domain.EmploymentCertificateTemplate;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.EmploymentCertificateTemplateDtos;
import com.hragent.hragentv1.repo.EmploymentCertificateTemplateRepository;
import com.hragent.hragentv1.web.AppException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmploymentCertificateTemplateService {
    private static final long MAX_TEMPLATE_BYTES = 5L * 1024 * 1024;
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9]*)}}");
    private static final Set<String> SUPPORTED_PLACEHOLDERS = Set.of(
            "legalName",
            "englishName",
            "employeeNo",
            "department",
            "title",
            "entryDate",
            "passportNumber",
            "passportExpiryDate",
            "monthlySalary",
            "currency",
            "companyName",
            "issueDate",
            "purpose",
            "destinationCountry",
            "consulateName"
    );

    private final EmploymentCertificateTemplateRepository templateRepository;
    private final AuditService auditService;
    private final Path storageRoot;

    public EmploymentCertificateTemplateService(
            EmploymentCertificateTemplateRepository templateRepository,
            AuditService auditService,
            @Value("${app.certificate.storage-root}") String storageRoot
    ) {
        this.templateRepository = templateRepository;
        this.auditService = auditService;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<EmploymentCertificateTemplateDtos.TemplateView> list(UserAccount actor) {
        return templateRepository.findByTenantIdOrderByUpdatedAtDesc(actor.getTenantId()).stream()
                .filter(template -> actor.getRole() == Role.HR
                        || template.getUploadedByEmployeeId().equals(actor.getId())
                        || (template.isActive()
                        && effectiveReviewStatus(template) == CertificateTemplateReviewStatus.APPROVED))
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmploymentCertificateTemplateDtos.TemplatePreview preview(
            UserAccount actor,
            MultipartFile file
    ) {
        return analyze(file);
    }

    @Transactional
    public EmploymentCertificateTemplateDtos.TemplateView upload(
            UserAccount actor,
            MultipartFile file,
            String name,
            String destinationCountry,
            String consulateName,
            CertificateLanguage language
    ) {
        requireHr(actor);
        return view(createTemplate(
                actor,
                file,
                name,
                destinationCountry,
                consulateName,
                language,
                CertificateTemplateReviewStatus.APPROVED,
                true,
                "UPLOAD_CERTIFICATE_TEMPLATE",
                "上传签证在职证明模板："
        ));
    }

    @Transactional
    public EmploymentCertificateTemplate uploadProposal(
            UserAccount actor,
            MultipartFile file,
            String name,
            String destinationCountry,
            String consulateName,
            CertificateLanguage language
    ) {
        return createTemplate(
                actor,
                file,
                name,
                destinationCountry,
                consulateName,
                language,
                CertificateTemplateReviewStatus.PENDING,
                false,
                "SUBMIT_CERTIFICATE_TEMPLATE",
                "随证明申请提交模板："
        );
    }

    private EmploymentCertificateTemplate createTemplate(
            UserAccount actor,
            MultipartFile file,
            String name,
            String destinationCountry,
            String consulateName,
            CertificateLanguage language,
            CertificateTemplateReviewStatus reviewStatus,
            boolean active,
            String auditAction,
            String auditDetailPrefix
    ) {
        String validatedName = required(name, "模板名称", 120);
        String validatedCountry = required(destinationCountry, "目的国家或地区", 100);
        String validatedConsulate = required(consulateName, "领事馆或受理机构", 160);
        if (language == null) {
            throw AppException.badRequest("请选择模板语言");
        }

        StoredTemplate stored = store(actor.getTenantId(), file);
        try {
            EmploymentCertificateTemplate template = new EmploymentCertificateTemplate();
            template.setTenantId(actor.getTenantId());
            template.setName(validatedName);
            template.setDestinationCountry(validatedCountry);
            template.setConsulateName(validatedConsulate);
            template.setLanguage(language);
            template.setSourceFileName(stored.fileName());
            template.setStorageKey(stored.storageKey());
            template.setContentType(DOCX_CONTENT_TYPE);
            template.setFileSize(stored.fileSize());
            template.setActive(active);
            template.setUploadedByEmployeeId(actor.getId());
            template.setReviewStatus(reviewStatus);
            if (reviewStatus == CertificateTemplateReviewStatus.APPROVED) {
                template.setReviewedByEmployeeId(actor.getId());
                template.setReviewedAt(LocalDateTime.now());
            }
            template.setCreatedAt(LocalDateTime.now());
            template.setUpdatedAt(LocalDateTime.now());
            EmploymentCertificateTemplate saved = templateRepository.save(template);
            auditService.log(
                    actor,
                    auditAction,
                    "EMPLOYMENT_CERTIFICATE_TEMPLATE",
                    saved.getId(),
                    auditDetailPrefix + saved.getName()
            );
            return saved;
        } catch (RuntimeException exception) {
            deleteQuietly(stored.storageKey());
            throw exception;
        }
    }

    @Transactional
    public EmploymentCertificateTemplateDtos.TemplateView setActive(
            UserAccount actor,
            Long templateId,
            boolean active
    ) {
        requireHr(actor);
        EmploymentCertificateTemplate template = requireTemplate(actor, templateId);
        if (effectiveReviewStatus(template) != CertificateTemplateReviewStatus.APPROVED) {
            throw AppException.badRequest("只有审核通过的模板可以启用或停用");
        }
        template.setActive(active);
        template.setUpdatedAt(LocalDateTime.now());
        EmploymentCertificateTemplate saved = templateRepository.save(template);
        auditService.log(
                actor,
                active ? "ENABLE_CERTIFICATE_TEMPLATE" : "DISABLE_CERTIFICATE_TEMPLATE",
                "EMPLOYMENT_CERTIFICATE_TEMPLATE",
                saved.getId(),
                (active ? "启用" : "停用") + "签证在职证明模板：" + saved.getName()
        );
        return view(saved);
    }

    @Transactional
    public TemplateDownload download(UserAccount actor, Long templateId) {
        EmploymentCertificateTemplate template = requireTemplate(actor, templateId);
        boolean companyTemplate = template.isActive()
                && effectiveReviewStatus(template) == CertificateTemplateReviewStatus.APPROVED;
        if (actor.getRole() != Role.HR
                && !template.getUploadedByEmployeeId().equals(actor.getId())
                && !companyTemplate) {
            throw AppException.forbidden("只能下载自己的模板或企业已启用模板");
        }
        Path file = resolveStorageKey(template.getStorageKey());
        if (!Files.isRegularFile(file)) {
            throw AppException.notFound("模板文件不存在，请重新上传");
        }
        try {
            byte[] content = Files.readAllBytes(file);
            auditService.log(
                    actor,
                    "DOWNLOAD_CERTIFICATE_TEMPLATE",
                    "EMPLOYMENT_CERTIFICATE_TEMPLATE",
                    template.getId(),
                    "下载签证在职证明模板：" + template.getName()
            );
            return new TemplateDownload(template.getSourceFileName(), template.getContentType(), content);
        } catch (IOException exception) {
            throw new IllegalStateException("读取签证在职证明模板失败", exception);
        }
    }

    @Transactional
    public EmploymentCertificateTemplate reviewProposal(
            UserAccount actor,
            Long templateId,
            Long applicantEmployeeId,
            boolean approved,
            String opinion
    ) {
        requireHr(actor);
        EmploymentCertificateTemplate template = requireTemplate(actor, templateId);
        if (!template.getUploadedByEmployeeId().equals(applicantEmployeeId)) {
            throw AppException.badRequest("申请人与模板提交人不一致");
        }
        if (effectiveReviewStatus(template) != CertificateTemplateReviewStatus.PENDING) {
            throw AppException.badRequest("该员工模板已经处理，请刷新列表");
        }
        template.setReviewStatus(approved
                ? CertificateTemplateReviewStatus.APPROVED
                : CertificateTemplateReviewStatus.REJECTED);
        template.setActive(approved);
        template.setReviewOpinion(clean(opinion, 600));
        template.setReviewedByEmployeeId(actor.getId());
        template.setReviewedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        EmploymentCertificateTemplate saved = templateRepository.save(template);
        auditService.log(
                actor,
                approved ? "APPROVE_CERTIFICATE_TEMPLATE" : "REJECT_CERTIFICATE_TEMPLATE",
                "EMPLOYMENT_CERTIFICATE_TEMPLATE",
                saved.getId(),
                (approved ? "通过" : "驳回") + "员工提交的证明模板：" + saved.getName()
        );
        return saved;
    }

    @Transactional
    public void cancelProposal(UserAccount actor, Long templateId) {
        EmploymentCertificateTemplate template = requireTemplate(actor, templateId);
        if (!template.getUploadedByEmployeeId().equals(actor.getId())) {
            throw AppException.forbidden("只能取消自己提交的模板");
        }
        if (effectiveReviewStatus(template) != CertificateTemplateReviewStatus.PENDING) {
            return;
        }
        template.setReviewStatus(CertificateTemplateReviewStatus.CANCELLED);
        template.setActive(false);
        template.setReviewOpinion("申请人取消证明申请");
        template.setUpdatedAt(LocalDateTime.now());
        templateRepository.save(template);
    }

    private StoredTemplate store(Long tenantId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("请选择非空的 Word 模板");
        }
        if (file.getSize() > MAX_TEMPLATE_BYTES) {
            throw AppException.badRequest("Word 模板不能超过 5 MB");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw AppException.badRequest("仅支持 DOCX 格式的 Word 模板");
        }

        String storageKey = "tenant-" + tenantId + "/templates/" + UUID.randomUUID() + ".docx";
        Path destination = resolveStorageKey(storageKey);
        try {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), "template-", ".tmp");
            try {
                file.transferTo(temporary);
                validateDocx(temporary);
                validateTemplatePlaceholders(temporary);
                moveAtomically(temporary, destination);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new StoredTemplate(fileName, storageKey, file.getSize());
        } catch (AppException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("保存签证在职证明模板失败", exception);
        }
    }

    private void validateDocx(Path path) {
        try (InputStream input = Files.newInputStream(path); XWPFDocument ignored = new XWPFDocument(input)) {
            // Opening through Apache POI verifies that this is a readable DOCX package.
        } catch (Exception exception) {
            throw AppException.badRequest("Word 模板无法读取，请上传有效的 DOCX 文件");
        }
    }

    private void validateTemplatePlaceholders(Path path) {
        try (InputStream input = Files.newInputStream(path); XWPFDocument document = new XWPFDocument(input)) {
            TemplateAnalysis analysis = analyzeDocument(document);
            if (!analysis.placeholders().isEmpty() && analysis.unsupportedPlaceholders().isEmpty()) {
                return;
            }
            if (analysis.placeholders().isEmpty()) {
                throw AppException.badRequest("模板没有识别到可替换的 {{fieldName}} 占位符");
            }
            throw AppException.badRequest("模板包含不支持的占位符："
                    + String.join("、", analysis.unsupportedPlaceholders()));
        } catch (AppException exception) {
            throw exception;
        } catch (IOException exception) {
            throw AppException.badRequest("Word 模板无法读取，请上传有效的 DOCX 文件");
        }
    }

    private EmploymentCertificateTemplateDtos.TemplatePreview analyze(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("请选择非空的 Word 模板");
        }
        if (file.getSize() > MAX_TEMPLATE_BYTES) {
            throw AppException.badRequest("Word 模板不能超过 5 MB");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw AppException.badRequest("仅支持 DOCX 格式的 Word 模板");
        }

        try (InputStream input = file.getInputStream(); XWPFDocument document = new XWPFDocument(input)) {
            TemplateAnalysis analysis = analyzeDocument(document);
            List<String> warnings = new ArrayList<>();
            if (analysis.placeholders().isEmpty()) {
                warnings.add("没有识别到可替换字段，上传后无法自动生成员工证明");
            }
            if (!analysis.unsupportedPlaceholders().isEmpty()) {
                warnings.add("存在不支持的字段，上传会被阻止");
            }
            if (analysis.placeholders().contains("{{monthlySalary}}")
                    || analysis.placeholders().contains("{{currency}}")) {
                warnings.add("申请人不勾选包含薪资时，薪资字段会留空");
            }
            return new EmploymentCertificateTemplateDtos.TemplatePreview(
                    fileName,
                    file.getSize(),
                    true,
                    !analysis.placeholders().isEmpty(),
                    !analysis.placeholders().isEmpty() && analysis.unsupportedPlaceholders().isEmpty(),
                    analysis.placeholders(),
                    analysis.unsupportedPlaceholders(),
                    warnings
            );
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest("Word 模板无法读取，请上传有效的 DOCX 文件");
        }
    }

    private TemplateAnalysis analyzeDocument(XWPFDocument document) {
        LinkedHashSet<String> placeholders = new LinkedHashSet<>();
        collectPlaceholders(document.getBodyElements(), placeholders);
        document.getHeaderList().forEach(header -> collectPlaceholders(header.getBodyElements(), placeholders));
        document.getFooterList().forEach(footer -> collectPlaceholders(footer.getBodyElements(), placeholders));

        List<String> supported = placeholders.stream()
                .filter(SUPPORTED_PLACEHOLDERS::contains)
                .map(value -> "{{" + value + "}}")
                .toList();
        List<String> unsupported = placeholders.stream()
                .filter(value -> !SUPPORTED_PLACEHOLDERS.contains(value))
                .map(value -> "{{" + value + "}}")
                .toList();
        return new TemplateAnalysis(supported, unsupported);
    }

    private void collectPlaceholders(
            List<org.apache.poi.xwpf.usermodel.IBodyElement> elements,
            Set<String> placeholders
    ) {
        for (org.apache.poi.xwpf.usermodel.IBodyElement element : elements) {
            if (element instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph) {
                Matcher matcher = PLACEHOLDER_PATTERN.matcher(paragraphText(paragraph));
                while (matcher.find()) {
                    placeholders.add(matcher.group(1));
                }
            } else if (element instanceof org.apache.poi.xwpf.usermodel.XWPFTable table) {
                table.getRows().forEach(row -> row.getTableCells()
                        .forEach(cell -> collectPlaceholders(cell.getBodyElements(), placeholders)));
            }
        }
    }

    private String paragraphText(org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph) {
        return paragraph.getRuns().stream()
                .map(run -> run.text() == null ? "" : run.text())
                .reduce("", String::concat);
    }

    private EmploymentCertificateTemplate requireTemplate(UserAccount actor, Long templateId) {
        return templateRepository.findByIdAndTenantId(templateId, actor.getTenantId())
                .orElseThrow(() -> AppException.notFound("签证在职证明模板不存在"));
    }

    private EmploymentCertificateTemplateDtos.TemplateView view(EmploymentCertificateTemplate template) {
        CertificateTemplateReviewStatus reviewStatus = effectiveReviewStatus(template);
        return new EmploymentCertificateTemplateDtos.TemplateView(
                template.getId(),
                template.getName(),
                template.getDestinationCountry(),
                template.getConsulateName(),
                template.getLanguage(),
                template.getLanguage().getLabel(),
                template.getSourceFileName(),
                template.getFileSize(),
                template.isActive(),
                template.getUploadedByEmployeeId(),
                reviewStatus,
                reviewStatus.getLabel(),
                template.getReviewOpinion(),
                template.getReviewedAt(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw AppException.notFound("模板文件尚未上传");
        }
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw AppException.forbidden("模板文件路径无效");
        }
        return resolved;
    }

    private void deleteQuietly(String storageKey) {
        try {
            Files.deleteIfExists(resolveStorageKey(storageKey));
        } catch (Exception ignored) {
            // The database transaction still rolls back; orphan cleanup can be handled by maintenance tooling.
        }
    }

    private void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String safeFileName(String originalName) {
        String candidate = originalName == null ? "" : originalName.trim().replace('\\', '/');
        candidate = candidate.substring(candidate.lastIndexOf('/') + 1).replace("..", "_");
        if (candidate.isBlank() || candidate.length() > 240) {
            throw AppException.badRequest("模板文件名无效或过长");
        }
        return candidate;
    }

    private String required(String value, String field, int maxLength) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isBlank()) {
            throw AppException.badRequest("请填写" + field);
        }
        if (cleaned.length() > maxLength) {
            throw AppException.badRequest(field + "不能超过 " + maxLength + " 个字符");
        }
        return cleaned;
    }

    private String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private CertificateTemplateReviewStatus effectiveReviewStatus(EmploymentCertificateTemplate template) {
        return template.getReviewStatus() == null
                ? CertificateTemplateReviewStatus.APPROVED
                : template.getReviewStatus();
    }

    private void requireHr(UserAccount actor) {
        if (actor.getRole() != Role.HR) {
            throw AppException.forbidden("只有空间管理员可以管理签证在职证明模板");
        }
    }

    private record StoredTemplate(String fileName, String storageKey, long fileSize) {
    }

    private record TemplateAnalysis(List<String> placeholders, List<String> unsupportedPlaceholders) {
    }

    public record TemplateDownload(String fileName, String contentType, byte[] content) {
    }
}
