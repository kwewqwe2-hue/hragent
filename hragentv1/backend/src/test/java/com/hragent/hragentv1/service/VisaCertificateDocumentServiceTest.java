package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.CertificateLanguage;
import com.hragent.hragentv1.domain.EmployeePersonalProfile;
import com.hragent.hragentv1.domain.EmploymentCertificateRequest;
import com.hragent.hragentv1.domain.EmploymentCertificateTemplate;
import com.hragent.hragentv1.domain.EmploymentCertificateType;
import com.hragent.hragentv1.domain.Tenant;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.repo.TenantRepository;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisaCertificateDocumentServiceTest {
    @TempDir
    Path tempDir;

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private SecretCryptoService secretCryptoService;

    private VisaCertificateDocumentService service;

    @BeforeEach
    void setUp() {
        service = new VisaCertificateDocumentService(
                tenantRepository,
                secretCryptoService,
                tempDir.toString()
        );
    }

    @Test
    void rendersPlaceholdersAcrossRunsTablesHeadersAndFooters() throws Exception {
        Path source = tempDir.resolve("tenant-1/templates/germany.docx");
        Files.createDirectories(source.getParent());
        writeTemplate(source, false);
        Tenant tenant = tenant();
        UserAccount employee = employee();
        EmployeePersonalProfile profile = profile();
        EmploymentCertificateRequest request = request();
        EmploymentCertificateTemplate template = template("tenant-1/templates/germany.docx");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(secretCryptoService.decrypt("encrypted-passport")).thenReturn("E12345678");

        EmploymentCertificateDocumentService.GeneratedDocument result =
                service.generate(template, request, employee, profile);

        Path output = tempDir.resolve(result.storageKey());
        assertThat(Files.isRegularFile(output)).isTrue();
        try (InputStream input = Files.newInputStream(output); XWPFDocument document = new XWPFDocument(input)) {
            assertThat(document.getParagraphs().getFirst().getText()).isEqualTo("Employee: ZHANG SAN");
            assertThat(document.getTables().getFirst().getRow(0).getCell(0).getText())
                    .isEqualTo("Passport: E12345678");
            assertThat(document.getHeaderList().getFirst().getText()).contains("Example Technology Co., Ltd.");
            assertThat(document.getFooterList().getFirst().getText()).contains("2026-10-08");
        }
    }

    @Test
    void rejectsTemplateWithUnsupportedPlaceholder() throws Exception {
        Path source = tempDir.resolve("tenant-1/templates/unknown.docx");
        Files.createDirectories(source.getParent());
        writeTemplate(source, true);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant()));
        when(secretCryptoService.decrypt("encrypted-passport")).thenReturn("E12345678");

        assertThatThrownBy(() -> service.generate(
                template("tenant-1/templates/unknown.docx"), request(), employee(), profile()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不支持的占位符")
                .hasMessageContaining("{{unknownField}}");
    }

    private void writeTemplate(Path path, boolean includeUnknown) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(path)) {
            XWPFParagraph employeeParagraph = document.createParagraph();
            employeeParagraph.createRun().setText("Employee: {{english");
            employeeParagraph.createRun().setText("Name}}");
            document.createTable(1, 1).getRow(0).getCell(0)
                    .setText("Passport: {{passportNumber}}");
            document.createHeader(HeaderFooterType.DEFAULT).createParagraph()
                    .createRun().setText("{{companyName}}");
            document.createFooter(HeaderFooterType.DEFAULT).createParagraph()
                    .createRun().setText("{{passportExpiryDate}}");
            if (includeUnknown) {
                document.createParagraph().createRun().setText("{{unknownField}}");
            }
            document.write(output);
        }
    }

    private Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setCode("DEMO");
        tenant.setName("Example Technology Co., Ltd.");
        return tenant;
    }

    private UserAccount employee() {
        UserAccount employee = new UserAccount();
        employee.setId(3L);
        employee.setTenantId(1L);
        employee.setEmployeeNo("E003");
        employee.setName("张三");
        employee.setDepartment("研发中心");
        employee.setTitle("工程师");
        employee.setEntryDate(LocalDate.of(2025, 3, 10));
        return employee;
    }

    private EmployeePersonalProfile profile() {
        EmployeePersonalProfile profile = new EmployeePersonalProfile();
        profile.setTenantId(1L);
        profile.setEmployeeId(3L);
        profile.setLegalName("张三");
        profile.setEnglishName("ZHANG SAN");
        profile.setPassportNumberEncrypted("encrypted-passport");
        profile.setPassportExpiryDate(LocalDate.of(2026, 10, 8));
        profile.setMonthlySalary(new BigDecimal("15000.00"));
        profile.setCurrency("CNY");
        return profile;
    }

    private EmploymentCertificateRequest request() {
        EmploymentCertificateRequest request = new EmploymentCertificateRequest();
        request.setId(24L);
        request.setTenantId(1L);
        request.setEmployeeId(3L);
        request.setCertificateType(EmploymentCertificateType.VISA);
        request.setLanguage(CertificateLanguage.BILINGUAL);
        request.setPurpose("办理德国商务签证");
        request.setDestinationCountry("德国");
        request.setConsulateName("德国驻华大使馆");
        request.setIncludeSalary(true);
        return request;
    }

    private EmploymentCertificateTemplate template(String storageKey) {
        EmploymentCertificateTemplate template = new EmploymentCertificateTemplate();
        template.setId(8L);
        template.setTenantId(1L);
        template.setName("德国签证模板");
        template.setDestinationCountry("德国");
        template.setConsulateName("德国驻华大使馆");
        template.setLanguage(CertificateLanguage.BILINGUAL);
        template.setSourceFileName("germany.docx");
        template.setStorageKey(storageKey);
        template.setActive(true);
        return template;
    }
}
