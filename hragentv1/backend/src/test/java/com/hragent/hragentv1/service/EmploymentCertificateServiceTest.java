package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmploymentCertificateDtos;
import com.hragent.hragentv1.repo.EmployeePersonalProfileRepository;
import com.hragent.hragentv1.repo.EmploymentCertificateRequestRepository;
import com.hragent.hragentv1.repo.EmploymentCertificateTemplateRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.web.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmploymentCertificateServiceTest {
    @Mock
    private EmploymentCertificateRequestRepository requestRepository;
    @Mock
    private EmployeePersonalProfileRepository profileRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private EmploymentCertificateDocumentService documentService;
    @Mock
    private EmploymentCertificateTemplateRepository templateRepository;
    @Mock
    private EmploymentCertificateTemplateService templateService;
    @Mock
    private VisaCertificateDocumentService visaDocumentService;

    private EmploymentCertificateService service;

    @BeforeEach
    void setUp() {
        service = new EmploymentCertificateService(
                requestRepository,
                profileRepository,
                userAccountRepository,
                auditService,
                documentService,
                templateRepository,
                templateService,
                visaDocumentService
        );
    }

    @Test
    void employeeCreatesVisaRequest() {
        UserAccount employee = employee(3L, 1L, Role.EMPLOYEE);
        EmployeePersonalProfile profile = readyProfile(employee);
        when(requestRepository.save(any(EmploymentCertificateRequest.class))).thenAnswer(invocation -> {
            EmploymentCertificateRequest saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });
        when(profileRepository.findByTenantIdAndEmployeeId(1L, 3L)).thenReturn(Optional.of(profile));

        EmploymentCertificateDtos.RequestView result = service.create(
                employee,
                new EmploymentCertificateDtos.CreateRequest(
                        EmploymentCertificateType.VISA,
                        CertificateLanguage.BILINGUAL,
                        "办理德国商务签证",
                        "德国",
                        "德国驻华大使馆",
                        true,
                        "计划十月出行"
                )
        );

        assertThat(result.id()).isEqualTo(12L);
        assertThat(result.status()).isEqualTo(CertificateRequestStatus.PENDING_HR);
        assertThat(result.profileReady()).isTrue();
        verify(auditService).log(
                employee,
                "CREATE_CERTIFICATE_REQUEST",
                "EMPLOYMENT_CERTIFICATE_REQUEST",
                12L,
                "提交在职证明申请：出境/签证在职证明"
        );
    }

    @Test
    void employeeCreatesVisaRequestWithPendingDocxTemplate() {
        UserAccount employee = employee(3L, 1L, Role.EMPLOYEE);
        EmployeePersonalProfile profile = readyProfile(employee);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "employee-template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[]{1, 2, 3}
        );
        EmploymentCertificateTemplate template = template(18L, 1L);
        template.setSourceFileName("employee-template.docx");
        template.setReviewStatus(CertificateTemplateReviewStatus.PENDING);
        template.setActive(false);
        when(requestRepository.save(any(EmploymentCertificateRequest.class))).thenAnswer(invocation -> {
            EmploymentCertificateRequest saved = invocation.getArgument(0);
            saved.setId(13L);
            return saved;
        });
        when(templateService.uploadProposal(
                employee,
                file,
                "德国员工模板",
                "德国",
                "德国驻华大使馆",
                CertificateLanguage.BILINGUAL
        )).thenReturn(template);
        when(profileRepository.findByTenantIdAndEmployeeId(1L, 3L)).thenReturn(Optional.of(profile));

        EmploymentCertificateDtos.RequestView result = service.createWithTemplate(
                employee,
                new EmploymentCertificateDtos.CreateRequest(
                        EmploymentCertificateType.VISA,
                        CertificateLanguage.BILINGUAL,
                        "办理德国商务签证",
                        "德国",
                        "德国驻华大使馆",
                        false,
                        null
                ),
                file,
                "德国员工模板"
        );

        assertThat(result.id()).isEqualTo(13L);
        assertThat(result.requestedTemplateId()).isEqualTo(18L);
        assertThat(result.requestedTemplateFileName()).isEqualTo("employee-template.docx");
        assertThat(result.status()).isEqualTo(CertificateRequestStatus.PENDING_HR);
        verify(auditService).log(
                employee,
                "CREATE_CERTIFICATE_REQUEST",
                "EMPLOYMENT_CERTIFICATE_REQUEST",
                13L,
                "提交在职证明申请：出境/签证在职证明（附员工模板）"
        );
    }

    @Test
    void visaRequestRequiresCountryAndConsulate() {
        UserAccount employee = employee(3L, 1L, Role.EMPLOYEE);

        assertThatThrownBy(() -> service.create(
                employee,
                new EmploymentCertificateDtos.CreateRequest(
                        EmploymentCertificateType.VISA,
                        CertificateLanguage.ENGLISH,
                        "办理签证",
                        "",
                        "",
                        false,
                        null
                )
        )).isInstanceOf(AppException.class).hasMessageContaining("目的国家");
    }

    @Test
    void employeeCannotCancelAnotherEmployeesRequest() {
        UserAccount employee = employee(3L, 1L, Role.EMPLOYEE);
        EmploymentCertificateRequest request = request(18L, 1L, 4L);
        when(requestRepository.findByIdAndTenantId(18L, 1L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(employee, 18L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("只能取消自己的");
    }

    @Test
    void hrApprovesRequestWhenEmployeeProfileIsReady() {
        UserAccount hr = employee(2L, 1L, Role.HR);
        UserAccount target = employee(3L, 1L, Role.EMPLOYEE);
        EmploymentCertificateRequest request = request(21L, 1L, 3L);
        request.setCertificateType(EmploymentCertificateType.VISA);
        request.setIncludeSalary(true);
        when(requestRepository.findByIdAndTenantId(21L, 1L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findById(3L)).thenReturn(Optional.of(target));
        when(profileRepository.findByTenantIdAndEmployeeId(1L, 3L)).thenReturn(Optional.of(readyProfile(target)));
        when(requestRepository.save(any(EmploymentCertificateRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmploymentCertificateDtos.RequestView result = service.review(
                hr,
                21L,
                new EmploymentCertificateDtos.ReviewRequest(true, "信息核验通过")
        );

        assertThat(result.status()).isEqualTo(CertificateRequestStatus.APPROVED);
        assertThat(result.hrOpinion()).isEqualTo("信息核验通过");
        verify(auditService).log(
                hr,
                "APPROVE_CERTIFICATE_REQUEST",
                "EMPLOYMENT_CERTIFICATE_REQUEST",
                21L,
                "通过在职证明申请：E003"
        );
    }

    @Test
    void standardChineseRequestIsGeneratedImmediatelyAfterApproval() {
        UserAccount hr = employee(2L, 1L, Role.HR);
        UserAccount target = employee(3L, 1L, Role.EMPLOYEE);
        EmployeePersonalProfile profile = readyProfile(target);
        EmploymentCertificateRequest request = request(23L, 1L, 3L);
        when(requestRepository.findByIdAndTenantId(23L, 1L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findById(3L)).thenReturn(Optional.of(target));
        when(profileRepository.findByTenantIdAndEmployeeId(1L, 3L)).thenReturn(Optional.of(profile));
        when(documentService.generateStandardChinese(request, target, profile))
                .thenReturn(new EmploymentCertificateDocumentService.GeneratedDocument(
                        "在职证明-E003-23.docx",
                        "tenant-1/request-23/employment-certificate-E003-23.docx"
                ));
        when(requestRepository.save(any(EmploymentCertificateRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmploymentCertificateDtos.RequestView result = service.review(
                hr,
                23L,
                new EmploymentCertificateDtos.ReviewRequest(true, "同意开具")
        );

        assertThat(result.status()).isEqualTo(CertificateRequestStatus.GENERATED);
        assertThat(result.documentReady()).isTrue();
        assertThat(result.generatedFileName()).isEqualTo("在职证明-E003-23.docx");
    }

    @Test
    void visaRequestUsesLatestMatchingActiveTemplateAfterApproval() {
        UserAccount hr = employee(2L, 1L, Role.HR);
        UserAccount target = employee(3L, 1L, Role.EMPLOYEE);
        EmployeePersonalProfile profile = readyProfile(target);
        EmploymentCertificateRequest request = request(24L, 1L, 3L);
        request.setCertificateType(EmploymentCertificateType.VISA);
        request.setLanguage(CertificateLanguage.BILINGUAL);
        request.setDestinationCountry("德国");
        request.setConsulateName("德国驻华大使馆");
        request.setRequestedTemplateId(8L);
        request.setRequestedTemplateFileName("germany.docx");
        EmploymentCertificateTemplate template = template(8L, 1L);
        when(requestRepository.findByIdAndTenantId(24L, 1L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findById(3L)).thenReturn(Optional.of(target));
        when(profileRepository.findByTenantIdAndEmployeeId(1L, 3L)).thenReturn(Optional.of(profile));
        when(templateService.reviewProposal(hr, 8L, 3L, true, "同意开具"))
                .thenReturn(template);
        when(visaDocumentService.generate(template, request, target, profile))
                .thenReturn(new EmploymentCertificateDocumentService.GeneratedDocument(
                        "签证在职证明-E003-24.docx",
                        "tenant-1/request-24/visa-employment-certificate-E003-24.docx"
                ));
        when(requestRepository.save(any(EmploymentCertificateRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmploymentCertificateDtos.RequestView result = service.review(
                hr,
                24L,
                new EmploymentCertificateDtos.ReviewRequest(true, "同意开具")
        );

        assertThat(result.status()).isEqualTo(CertificateRequestStatus.GENERATED);
        assertThat(result.sourceTemplateFileName()).isEqualTo("germany.docx");
        assertThat(result.generatedFileName()).isEqualTo("签证在职证明-E003-24.docx");
        assertThat(result.documentReady()).isTrue();
        verify(templateService).reviewProposal(hr, 8L, 3L, true, "同意开具");
    }

    @Test
    void rejectingRequestAlsoRejectsAttachedEmployeeTemplate() {
        UserAccount hr = employee(2L, 1L, Role.HR);
        UserAccount target = employee(3L, 1L, Role.EMPLOYEE);
        EmploymentCertificateRequest request = request(27L, 1L, 3L);
        request.setCertificateType(EmploymentCertificateType.VISA);
        request.setRequestedTemplateId(19L);
        request.setRequestedTemplateFileName("employee-template.docx");
        EmploymentCertificateTemplate template = template(19L, 1L);
        template.setReviewStatus(CertificateTemplateReviewStatus.REJECTED);
        template.setActive(false);
        when(requestRepository.findByIdAndTenantId(27L, 1L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findById(3L)).thenReturn(Optional.of(target));
        when(templateService.reviewProposal(hr, 19L, 3L, false, "模板格式不符合要求"))
                .thenReturn(template);
        when(profileRepository.findByTenantIdAndEmployeeId(1L, 3L))
                .thenReturn(Optional.of(readyProfile(target)));
        when(requestRepository.save(any(EmploymentCertificateRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmploymentCertificateDtos.RequestView result = service.review(
                hr,
                27L,
                new EmploymentCertificateDtos.ReviewRequest(false, "模板格式不符合要求")
        );

        assertThat(result.status()).isEqualTo(CertificateRequestStatus.REJECTED);
        verify(templateService).reviewProposal(hr, 19L, 3L, false, "模板格式不符合要求");
    }

    @Test
    void approvedVisaRequestWaitsWhenNoExactTemplateMatches() {
        UserAccount hr = employee(2L, 1L, Role.HR);
        UserAccount target = employee(3L, 1L, Role.EMPLOYEE);
        EmploymentCertificateRequest request = request(25L, 1L, 3L);
        request.setCertificateType(EmploymentCertificateType.VISA);
        request.setLanguage(CertificateLanguage.ENGLISH);
        request.setDestinationCountry("法国");
        request.setConsulateName("法国签证中心");
        when(requestRepository.findByIdAndTenantId(25L, 1L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findById(3L)).thenReturn(Optional.of(target));
        when(profileRepository.findByTenantIdAndEmployeeId(1L, 3L))
                .thenReturn(Optional.of(readyProfile(target)));
        when(requestRepository.save(any(EmploymentCertificateRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmploymentCertificateDtos.RequestView result = service.review(
                hr,
                25L,
                new EmploymentCertificateDtos.ReviewRequest(true, "信息核验通过")
        );

        assertThat(result.status()).isEqualTo(CertificateRequestStatus.APPROVED);
        assertThat(result.generationError()).contains("未找到").contains("完全匹配");
        assertThat(result.documentReady()).isFalse();
    }

    @Test
    void hrCanRetryVisaGenerationAfterUploadingTemplate() {
        UserAccount hr = employee(2L, 1L, Role.HR);
        UserAccount target = employee(3L, 1L, Role.EMPLOYEE);
        EmployeePersonalProfile profile = readyProfile(target);
        EmploymentCertificateRequest request = request(26L, 1L, 3L);
        request.setCertificateType(EmploymentCertificateType.VISA);
        request.setLanguage(CertificateLanguage.BILINGUAL);
        request.setDestinationCountry("德国");
        request.setConsulateName("德国驻华大使馆");
        request.setStatus(CertificateRequestStatus.APPROVED);
        request.setGenerationError("未找到模板");
        EmploymentCertificateTemplate template = template(9L, 1L);
        when(requestRepository.findByIdAndTenantId(26L, 1L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findById(3L)).thenReturn(Optional.of(target));
        when(profileRepository.findByTenantIdAndEmployeeId(1L, 3L)).thenReturn(Optional.of(profile));
        when(templateRepository
                .findFirstByTenantIdAndDestinationCountryIgnoreCaseAndConsulateNameIgnoreCaseAndLanguageAndActiveTrueOrderByUpdatedAtDesc(
                        1L, "德国", "德国驻华大使馆", CertificateLanguage.BILINGUAL
                )).thenReturn(Optional.of(template));
        when(visaDocumentService.generate(template, request, target, profile))
                .thenReturn(new EmploymentCertificateDocumentService.GeneratedDocument(
                        "签证在职证明-E003-26.docx",
                        "tenant-1/request-26/visa-employment-certificate-E003-26.docx"
                ));
        when(requestRepository.save(any(EmploymentCertificateRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmploymentCertificateDtos.RequestView result = service.retryGeneration(hr, 26L);

        assertThat(result.status()).isEqualTo(CertificateRequestStatus.GENERATED);
        assertThat(result.generationError()).isNull();
        assertThat(result.sourceTemplateFileName()).isEqualTo("germany.docx");
        verify(auditService).log(
                hr,
                "RETRY_CERTIFICATE_GENERATION",
                "EMPLOYMENT_CERTIFICATE_REQUEST",
                26L,
                "重新生成签证在职证明：E003"
        );
    }

    @Test
    void hrCannotApproveRequestWithMissingPersonalProfile() {
        UserAccount hr = employee(2L, 1L, Role.HR);
        UserAccount target = employee(3L, 1L, Role.EMPLOYEE);
        EmploymentCertificateRequest request = request(22L, 1L, 3L);
        when(requestRepository.findByIdAndTenantId(22L, 1L)).thenReturn(Optional.of(request));
        when(userAccountRepository.findById(3L)).thenReturn(Optional.of(target));
        when(profileRepository.findByTenantIdAndEmployeeId(1L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(
                hr,
                22L,
                new EmploymentCertificateDtos.ReviewRequest(true, "通过")
        )).isInstanceOf(AppException.class).hasMessageContaining("法定姓名");
    }

    @Test
    void nonHrCannotViewCompanyCertificateQueue() {
        UserAccount employee = employee(3L, 1L, Role.EMPLOYEE);

        assertThatThrownBy(() -> service.hrAll(employee))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("只有空间管理员");
    }

    @Test
    void employeeCanDownloadOnlyTheirGeneratedDocument() {
        UserAccount employee = employee(3L, 1L, Role.EMPLOYEE);
        EmploymentCertificateRequest ownRequest = request(31L, 1L, 3L);
        ownRequest.setStatus(CertificateRequestStatus.GENERATED);
        ownRequest.setGeneratedFileName("在职证明-E003-31.docx");
        ownRequest.setGeneratedFileStorageKey("tenant-1/request-31/document.docx");
        when(requestRepository.findByIdAndTenantId(31L, 1L)).thenReturn(Optional.of(ownRequest));
        when(documentService.read("tenant-1/request-31/document.docx")).thenReturn(new byte[]{1, 2, 3});

        EmploymentCertificateService.DocumentDownload result = service.download(employee, 31L);

        assertThat(result.fileName()).isEqualTo("在职证明-E003-31.docx");
        assertThat(result.content()).containsExactly(1, 2, 3);
        verify(auditService).log(
                employee,
                "DOWNLOAD_CERTIFICATE_DOCUMENT",
                "EMPLOYMENT_CERTIFICATE_REQUEST",
                31L,
                "下载在职证明文件"
        );

        EmploymentCertificateRequest otherRequest = request(32L, 1L, 4L);
        otherRequest.setStatus(CertificateRequestStatus.GENERATED);
        otherRequest.setGeneratedFileStorageKey("tenant-1/request-32/document.docx");
        when(requestRepository.findByIdAndTenantId(32L, 1L)).thenReturn(Optional.of(otherRequest));

        assertThatThrownBy(() -> service.download(employee, 32L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("只能下载自己的");
    }

    private UserAccount employee(Long id, Long tenantId, Role role) {
        UserAccount employee = new UserAccount();
        employee.setId(id);
        employee.setTenantId(tenantId);
        employee.setEmployeeNo("E00" + id);
        employee.setName("Test User");
        employee.setRole(role);
        employee.setDepartment("研发中心");
        employee.setTitle("Java 工程师");
        employee.setEmployeeStatus(EmployeeStatus.ACTIVE);
        employee.setActive(true);
        return employee;
    }

    private EmployeePersonalProfile readyProfile(UserAccount employee) {
        EmployeePersonalProfile profile = new EmployeePersonalProfile();
        profile.setTenantId(employee.getTenantId());
        profile.setEmployeeId(employee.getId());
        profile.setLegalName("张三");
        profile.setEnglishName("ZHANG SAN");
        profile.setPassportNumberEncrypted("encrypted-passport");
        profile.setPassportExpiryDate(LocalDate.of(2031, 6, 30));
        profile.setContractStartDate(LocalDate.of(2025, 3, 10));
        profile.setMonthlySalary(new BigDecimal("15000.00"));
        return profile;
    }

    private EmploymentCertificateRequest request(Long id, Long tenantId, Long employeeId) {
        EmploymentCertificateRequest request = new EmploymentCertificateRequest();
        request.setId(id);
        request.setTenantId(tenantId);
        request.setEmployeeId(employeeId);
        request.setCertificateType(EmploymentCertificateType.STANDARD);
        request.setLanguage(CertificateLanguage.CHINESE);
        request.setPurpose("办理业务");
        request.setStatus(CertificateRequestStatus.PENDING_HR);
        return request;
    }

    private EmploymentCertificateTemplate template(Long id, Long tenantId) {
        EmploymentCertificateTemplate template = new EmploymentCertificateTemplate();
        template.setId(id);
        template.setTenantId(tenantId);
        template.setName("德国签证模板");
        template.setDestinationCountry("德国");
        template.setConsulateName("德国驻华大使馆");
        template.setLanguage(CertificateLanguage.BILINGUAL);
        template.setSourceFileName("germany.docx");
        template.setStorageKey("tenant-1/templates/germany.docx");
        template.setActive(true);
        return template;
    }
}
