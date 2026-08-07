package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.TenantRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmploymentCertificateDocumentServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesReadableStandardChineseDocx() throws Exception {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setCode("DEMO");
        tenant.setName("示例科技有限公司");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        EmploymentCertificateDocumentService service = new EmploymentCertificateDocumentService(
                tenantRepository,
                tempDir.toString(),
                "北京市海淀区示例地址 1 号",
                "010-88886666"
        );

        UserAccount employee = new UserAccount();
        employee.setId(3L);
        employee.setTenantId(1L);
        employee.setEmployeeNo("E001");
        employee.setName("张三");
        employee.setDepartment("研发中心");
        employee.setTitle("Java 工程师");
        employee.setEntryDate(LocalDate.of(2025, 3, 10));

        EmployeePersonalProfile profile = new EmployeePersonalProfile();
        profile.setTenantId(1L);
        profile.setEmployeeId(3L);
        profile.setLegalName("张三");
        profile.setMonthlySalary(new BigDecimal("15000.00"));
        profile.setCurrency("CNY");

        EmploymentCertificateRequest request = new EmploymentCertificateRequest();
        request.setId(45L);
        request.setTenantId(1L);
        request.setEmployeeId(3L);
        request.setCertificateType(EmploymentCertificateType.STANDARD);
        request.setLanguage(CertificateLanguage.CHINESE);
        request.setPurpose("办理银行业务");
        request.setIncludeSalary(true);

        EmploymentCertificateDocumentService.GeneratedDocument generated =
                service.generateStandardChinese(request, employee, profile);
        byte[] content = service.read(generated.storageKey());

        assertThat(generated.fileName()).isEqualTo("在职证明-E001-45.docx");
        assertThat(content.length).isGreaterThan(2_000);
        assertThat(Files.isRegularFile(tempDir.resolve(generated.storageKey()))).isTrue();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            String footerText = document.getFooterList().stream()
                    .flatMap(footer -> footer.getParagraphs().stream())
                    .map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(text).contains("在 职 证 明");
            assertThat(text).contains("张三");
            assertThat(text).contains("研发中心Java 工程师");
            assertThat(text).contains("CNY 15,000.00");
            assertThat(text).contains("办理银行业务");
            assertThat(text).contains("示例科技有限公司");
            assertThat(footerText).contains("北京市海淀区示例地址 1 号");
            assertThat(footerText).contains("010-88886666");
        }
    }
}
