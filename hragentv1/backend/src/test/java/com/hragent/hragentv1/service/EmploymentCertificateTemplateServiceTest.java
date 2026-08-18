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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmploymentCertificateTemplateServiceTest {
    @TempDir
    Path tempDir;

    @Mock
    private EmploymentCertificateTemplateRepository templateRepository;
    @Mock
    private AuditService auditService;

    private EmploymentCertificateTemplateService service;

    @BeforeEach
    void setUp() {
        service = new EmploymentCertificateTemplateService(templateRepository, auditService, tempDir.toString());
    }

    @Test
    void hrUploadsAndDownloadsReadableDocxTemplate() throws Exception {
        UserAccount hr = user(2L, 1L, Role.HR);
        AtomicReference<EmploymentCertificateTemplate> savedTemplate = new AtomicReference<>();
        when(templateRepository.save(any(EmploymentCertificateTemplate.class))).thenAnswer(invocation -> {
            EmploymentCertificateTemplate template = invocation.getArgument(0);
            template.setId(7L);
            savedTemplate.set(template);
            return template;
        });
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "germany-visa-template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                validDocx()
        );

        EmploymentCertificateTemplateDtos.TemplateView result = service.upload(
                hr,
                file,
                "德国商务签证模板",
                "德国",
                "德国驻华大使馆",
                CertificateLanguage.BILINGUAL
        );

        EmploymentCertificateTemplate stored = savedTemplate.get();
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.active()).isTrue();
        assertThat(result.languageLabel()).isEqualTo("中英双语");
        assertThat(Files.isRegularFile(tempDir.resolve(stored.getStorageKey()))).isTrue();
        when(templateRepository.findByIdAndTenantId(7L, 1L)).thenReturn(Optional.of(stored));

        EmploymentCertificateTemplateService.TemplateDownload download = service.download(hr, 7L);

        assertThat(download.fileName()).isEqualTo("germany-visa-template.docx");
        assertThat(download.content()).isEqualTo(file.getBytes());
        verify(auditService).log(
                hr,
                "UPLOAD_CERTIFICATE_TEMPLATE",
                "EMPLOYMENT_CERTIFICATE_TEMPLATE",
                7L,
                "上传签证在职证明模板：德国商务签证模板"
        );
    }

    @Test
    void previewExtractsSupportedFieldsAndWarnsAboutSalary() throws Exception {
        UserAccount hr = user(3L, 1L, Role.EMPLOYEE);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxWithText("Name {{legalName}} / Salary {{monthlySalary}}")
        );

        EmploymentCertificateTemplateDtos.TemplatePreview preview = service.preview(hr, file);

        assertThat(preview.readable()).isTrue();
        assertThat(preview.canUpload()).isTrue();
        assertThat(preview.placeholders()).containsExactly("{{legalName}}", "{{monthlySalary}}");
        assertThat(preview.unsupportedPlaceholders()).isEmpty();
        assertThat(preview.warnings()).anyMatch(message -> message.contains("薪资字段"));
    }

    @Test
    void rejectsUploadWhenTemplateHasNoSupportedFields() throws Exception {
        UserAccount hr = user(2L, 1L, Role.HR);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "static.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxWithText("Static employment certificate template")
        );

        assertThatThrownBy(() -> service.upload(
                hr, file, "静态模板", "德国", "德国驻华大使馆", CertificateLanguage.ENGLISH
        )).isInstanceOf(AppException.class).hasMessageContaining("没有识别到");
    }

    @Test
    void rejectsUploadWhenTemplateHasUnsupportedFields() throws Exception {
        UserAccount hr = user(2L, 1L, Role.HR);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "unknown.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxWithText("{{unknownField}} {{legalName}}")
        );

        assertThatThrownBy(() -> service.upload(
                hr, file, "未知字段模板", "德国", "德国驻华大使馆", CertificateLanguage.ENGLISH
        )).isInstanceOf(AppException.class).hasMessageContaining("不支持的占位符");
    }

    @Test
    void rejectsFileThatOnlyUsesDocxExtension() {
        UserAccount hr = user(2L, 1L, Role.HR);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "not a Word file".getBytes()
        );

        assertThatThrownBy(() -> service.upload(
                hr,
                file,
                "无效模板",
                "德国",
                "德国驻华大使馆",
                CertificateLanguage.ENGLISH
        )).isInstanceOf(AppException.class).hasMessageContaining("无法读取");
    }

    @Test
    void employeeSubmitsInactiveTemplateForSingleHrReview() throws Exception {
        UserAccount employee = user(3L, 1L, Role.EMPLOYEE);
        when(templateRepository.save(any(EmploymentCertificateTemplate.class))).thenAnswer(invocation -> {
            EmploymentCertificateTemplate template = invocation.getArgument(0);
            template.setId(11L);
            return template;
        });
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "employee-template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                validDocx()
        );

        EmploymentCertificateTemplate result = service.uploadProposal(
                employee,
                file,
                "员工签证模板",
                "德国",
                "德国驻华大使馆",
                CertificateLanguage.BILINGUAL
        );

        assertThat(result.getId()).isEqualTo(11L);
        assertThat(result.getReviewStatus()).isEqualTo(CertificateTemplateReviewStatus.PENDING);
        assertThat(result.isActive()).isFalse();
        verify(auditService).log(
                employee,
                "SUBMIT_CERTIFICATE_TEMPLATE",
                "EMPLOYMENT_CERTIFICATE_TEMPLATE",
                11L,
                "随证明申请提交模板：员工签证模板"
        );
    }

    @Test
    void employeeListsCompanyTemplatesAndOwnPendingTemplateOnly() {
        UserAccount employee = user(3L, 1L, Role.EMPLOYEE);
        EmploymentCertificateTemplate company = template(1L, 2L, true, CertificateTemplateReviewStatus.APPROVED);
        EmploymentCertificateTemplate own = template(2L, 3L, false, CertificateTemplateReviewStatus.PENDING);
        EmploymentCertificateTemplate other = template(3L, 4L, false, CertificateTemplateReviewStatus.PENDING);
        when(templateRepository.findByTenantIdOrderByUpdatedAtDesc(1L))
                .thenReturn(List.of(company, own, other));

        List<EmploymentCertificateTemplateDtos.TemplateView> result = service.list(employee);

        assertThat(result).extracting(EmploymentCertificateTemplateDtos.TemplateView::id)
                .containsExactly(1L, 2L);
    }

    @Test
    void hrCanDisableTemplateInsideOwnTenant() {
        UserAccount hr = user(2L, 1L, Role.HR);
        EmploymentCertificateTemplate template = new EmploymentCertificateTemplate();
        template.setId(9L);
        template.setTenantId(1L);
        template.setName("德国签证模板");
        template.setDestinationCountry("德国");
        template.setConsulateName("德国驻华大使馆");
        template.setLanguage(CertificateLanguage.ENGLISH);
        template.setSourceFileName("template.docx");
        template.setStorageKey("tenant-1/templates/template.docx");
        template.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        template.setFileSize(3_000L);
        template.setActive(true);
        template.setUploadedByEmployeeId(2L);
        when(templateRepository.findByIdAndTenantId(9L, 1L)).thenReturn(Optional.of(template));
        when(templateRepository.save(template)).thenReturn(template);

        EmploymentCertificateTemplateDtos.TemplateView result = service.setActive(hr, 9L, false);

        assertThat(result.active()).isFalse();
        verify(auditService).log(
                hr,
                "DISABLE_CERTIFICATE_TEMPLATE",
                "EMPLOYMENT_CERTIFICATE_TEMPLATE",
                9L,
                "停用签证在职证明模板：德国签证模板"
        );
    }

    private byte[] validDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Employment certificate template: {{legalName}}");
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] docxWithText(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private UserAccount user(Long id, Long tenantId, Role role) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setRole(role);
        user.setName("Test User");
        user.setEmployeeNo("E00" + id);
        return user;
    }

    private EmploymentCertificateTemplate template(
            Long id,
            Long uploaderId,
            boolean active,
            CertificateTemplateReviewStatus reviewStatus
    ) {
        EmploymentCertificateTemplate template = new EmploymentCertificateTemplate();
        template.setId(id);
        template.setTenantId(1L);
        template.setName("Template " + id);
        template.setDestinationCountry("德国");
        template.setConsulateName("德国驻华大使馆");
        template.setLanguage(CertificateLanguage.BILINGUAL);
        template.setSourceFileName("template-" + id + ".docx");
        template.setStorageKey("tenant-1/templates/template-" + id + ".docx");
        template.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        template.setUploadedByEmployeeId(uploaderId);
        template.setActive(active);
        template.setReviewStatus(reviewStatus);
        return template;
    }
}
