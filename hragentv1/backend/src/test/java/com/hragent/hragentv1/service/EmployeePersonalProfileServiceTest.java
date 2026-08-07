package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.EmployeePersonalProfile;
import com.hragent.hragentv1.domain.EmployeeStatus;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.EmployeePersonalProfileDtos;
import com.hragent.hragentv1.repo.EmployeePersonalProfileRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.web.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeePersonalProfileServiceTest {
    @Mock
    private EmployeePersonalProfileRepository profileRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private AuditService auditService;

    private EmployeePersonalProfileService service;

    @BeforeEach
    void setUp() {
        service = new EmployeePersonalProfileService(
                profileRepository,
                userAccountRepository,
                new SecretCryptoService("test-secret-with-more-than-16-characters"),
                auditService
        );
    }

    @Test
    void employeeCanReadOnlyTheirOwnFullProfile() {
        UserAccount employee = employee(3L, 1L, Role.EMPLOYEE);
        EmployeePersonalProfile profile = profile(employee, serviceSecret().encrypt("DEMO-ID-001"));
        when(profileRepository.findByTenantIdAndEmployeeId(1L, 3L)).thenReturn(Optional.of(profile));

        EmployeePersonalProfileDtos.ProfileView result = service.mine(employee);

        assertThat(result.employeeId()).isEqualTo(3L);
        assertThat(result.idNumber()).isEqualTo("DEMO-ID-001");
        assertThat(result.monthlySalary()).isEqualByComparingTo("15000.00");
    }

    @Test
    void nonHrCannotListOtherEmployees() {
        UserAccount employee = employee(3L, 1L, Role.EMPLOYEE);

        assertThatThrownBy(() -> service.list(employee))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("只有空间管理员");
    }

    @Test
    void hrUpdateEncryptsSensitiveNumbersAndWritesAuditLog() {
        UserAccount hr = employee(9L, 1L, Role.HR);
        UserAccount target = employee(3L, 1L, Role.EMPLOYEE);
        when(userAccountRepository.findById(3L)).thenReturn(Optional.of(target));
        when(profileRepository.findByTenantIdAndEmployeeId(1L, 3L)).thenReturn(Optional.empty());
        when(profileRepository.save(any(EmployeePersonalProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmployeePersonalProfileDtos.ProfileUpdateRequest request = new EmployeePersonalProfileDtos.ProfileUpdateRequest(
                "张三",
                "ZHANG SAN",
                "男",
                LocalDate.of(1996, 3, 12),
                "中国",
                "居民身份证（演示）",
                "DEMO-ID-001",
                "DEMO-PASSPORT-001",
                LocalDate.of(2031, 6, 30),
                "全日制",
                LocalDate.of(2025, 3, 10),
                LocalDate.of(2028, 3, 9),
                "北京市海淀区",
                new BigDecimal("15000"),
                "cny",
                "演示地址",
                "张先生",
                "13900000001"
        );

        EmployeePersonalProfileDtos.ProfileView result = service.update(hr, 3L, request);

        ArgumentCaptor<EmployeePersonalProfile> captor = ArgumentCaptor.forClass(EmployeePersonalProfile.class);
        verify(profileRepository).save(captor.capture());
        assertThat(captor.getValue().getIdNumberEncrypted()).doesNotContain("DEMO-ID-001");
        assertThat(captor.getValue().getPassportNumberEncrypted()).doesNotContain("DEMO-PASSPORT-001");
        assertThat(result.idNumber()).isEqualTo("DEMO-ID-001");
        assertThat(result.currency()).isEqualTo("CNY");
        verify(auditService).log(
                hr,
                "UPDATE_PERSONAL_PROFILE",
                "EMPLOYEE_PERSONAL_PROFILE",
                3L,
                "更新员工个人档案：E003 / Test User"
        );
    }

    @Test
    void hrCannotReadEmployeeFromAnotherWorkspace() {
        UserAccount hr = employee(9L, 1L, Role.HR);
        UserAccount outsider = employee(8L, 2L, Role.EMPLOYEE);
        when(userAccountRepository.findById(8L)).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> service.detail(hr, 8L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("员工档案不存在");
    }

    private UserAccount employee(Long id, Long tenantId, Role role) {
        UserAccount employee = new UserAccount();
        employee.setId(id);
        employee.setTenantId(tenantId);
        employee.setEmployeeNo("E00" + id);
        employee.setName("Test User");
        employee.setRole(role);
        employee.setDepartment("R&D Center");
        employee.setTitle("Java Engineer");
        employee.setEmployeeStatus(EmployeeStatus.ACTIVE);
        employee.setActive(true);
        return employee;
    }

    private EmployeePersonalProfile profile(UserAccount employee, String encryptedIdNumber) {
        EmployeePersonalProfile profile = new EmployeePersonalProfile();
        profile.setTenantId(employee.getTenantId());
        profile.setEmployeeId(employee.getId());
        profile.setLegalName("张三");
        profile.setIdNumberEncrypted(encryptedIdNumber);
        profile.setMonthlySalary(new BigDecimal("15000.00"));
        profile.setCurrency("CNY");
        return profile;
    }

    private SecretCryptoService serviceSecret() {
        return new SecretCryptoService("test-secret-with-more-than-16-characters");
    }
}
