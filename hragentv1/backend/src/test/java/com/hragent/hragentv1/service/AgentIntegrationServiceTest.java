package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.ApiCallLog;
import com.hragent.hragentv1.domain.IntegrationApiKey;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.AgentIntegrationDtos;
import com.hragent.hragentv1.dto.EmployeePersonalProfileDtos;
import com.hragent.hragentv1.repo.AgentNotificationRepository;
import com.hragent.hragentv1.repo.ApiCallLogRepository;
import com.hragent.hragentv1.repo.LeaveRequestRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.dto.OnboardingDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentIntegrationServiceTest {
    @Mock
    private OpenApiService openApiService;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private ApiCallLogRepository apiCallLogRepository;
    @Mock
    private AgentNotificationRepository notificationRepository;
    @Mock
    private LeaveService leaveService;
    @Mock
    private AgentNotificationService agentNotificationService;
    @Mock
    private AgentCardTokenService cardTokenService;
    @Mock
    private WebChatIdentityService webChatIdentityService;
    @Mock
    private EmployeePersonalProfileService personalProfileService;
    @Mock
    private EmploymentCertificateService employmentCertificateService;
    @Mock
    private OnboardingService onboardingService;

    @InjectMocks
    private AgentIntegrationService service;

    @Test
    void personalProfileIncludesCurrentEmployeesSalaryAndCurrency() {
        IntegrationApiKey key = new IntegrationApiKey();
        key.setId(9L);
        key.setTenantId(1L);

        UserAccount employee = new UserAccount();
        employee.setId(3L);
        employee.setTenantId(1L);
        employee.setEmployeeNo("E001");
        employee.setName("Zhang San");
        employee.setRole(Role.EMPLOYEE);
        employee.setDingtalkUserId("ding-zhangsan");
        employee.setActive(true);

        EmployeePersonalProfileDtos.ProfileView profile = new EmployeePersonalProfileDtos.ProfileView(
                3L,
                "E001",
                "Zhang San",
                "Zhang San",
                "ZHANG SAN",
                Role.EMPLOYEE,
                "Engineering",
                "Java Engineer",
                "zhangsan@example.com",
                "13800000001",
                LocalDate.of(2025, 3, 10),
                "ACTIVE",
                "Li Si",
                null,
                null,
                "China",
                null,
                "DEMO-ID-001",
                null,
                null,
                "FULL_TIME",
                LocalDate.of(2025, 3, 10),
                LocalDate.of(2028, 3, 9),
                "Beijing",
                new BigDecimal("15000.00"),
                "CNY",
                null,
                null,
                null,
                LocalDateTime.of(2026, 8, 11, 10, 0),
                true
        );

        when(openApiService.authenticate(
                "demo-key",
                "GET",
                "/internal/agent/v1/personal-profile"
        )).thenReturn(key);
        when(webChatIdentityService.isWebIdentity("ding-zhangsan")).thenReturn(false);
        when(userAccountRepository.findByTenantIdAndDingtalkUserId(1L, "ding-zhangsan"))
                .thenReturn(Optional.of(employee));
        when(personalProfileService.mine(employee)).thenReturn(profile);

        AgentIntegrationDtos.PersonalProfile result = service.personalProfile(
                "demo-key",
                "ding-zhangsan"
        );

        assertThat(result.employeeNo()).isEqualTo("E001");
        assertThat(result.monthlySalary()).isEqualByComparingTo("15000.00");
        assertThat(result.currency()).isEqualTo("CNY");
        verify(apiCallLogRepository).save(any(ApiCallLog.class));
    }

    @Test
    void onboardingRequestsOnlyReturnsCurrentNewHireRequests() {
        IntegrationApiKey key = new IntegrationApiKey();
        key.setId(10L);
        key.setTenantId(1L);

        UserAccount newHire = new UserAccount();
        newHire.setId(4L);
        newHire.setTenantId(1L);
        newHire.setRole(Role.NEW_HIRE);
        newHire.setActive(true);

        OnboardingDtos.RequestView request = new OnboardingDtos.RequestView(
                7L, 4L, "NH001", "Chen Chen", "Chen Chen", "13800000004",
                "chenchen@example.com", "A1B2", LocalDate.of(2026, 9, 1),
                "Pending assignment", "New hire", null, "Beijing",
                "Emergency contact", "13900000004", "Demo Bank", "1234",
                  "Bachelor", false, false, false, false, false, null,
                com.hragent.hragentv1.domain.OnboardingRequestStatus.PENDING_HR,
                "Pending HR review", null, LocalDateTime.now(), null
        );

        when(openApiService.authenticate(
                "demo-key", "GET", "/internal/agent/v1/onboarding/requests"
        )).thenReturn(key);
        when(webChatIdentityService.isWebIdentity("ding-chenchen")).thenReturn(false);
        when(userAccountRepository.findByTenantIdAndDingtalkUserId(1L, "ding-chenchen"))
                .thenReturn(Optional.of(newHire));
        when(onboardingService.mine(newHire)).thenReturn(java.util.List.of(request));

        var result = service.onboardingRequests("demo-key", "ding-chenchen");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).statusLabel()).isEqualTo("Pending HR review");
        verify(apiCallLogRepository).save(any(ApiCallLog.class));
    }
}
