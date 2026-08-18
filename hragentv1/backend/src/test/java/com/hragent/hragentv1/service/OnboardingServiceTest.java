package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.OnboardingRequest;
import com.hragent.hragentv1.domain.OnboardingRequestStatus;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.domain.WorkspaceMembership;
import com.hragent.hragentv1.dto.OnboardingDtos;
import com.hragent.hragentv1.repo.OnboardingRequestRepository;
import com.hragent.hragentv1.repo.LeaveBalanceRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.repo.WorkspaceMembershipRepository;
import com.hragent.hragentv1.web.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {
    @Mock
    private OnboardingRequestRepository requestRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private WorkspaceMembershipRepository membershipRepository;
    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;
    @Mock
    private AuditService auditService;

    private OnboardingService service;

    @BeforeEach
    void setUp() {
        service = new OnboardingService(
                requestRepository, userAccountRepository, membershipRepository, leaveBalanceRepository, auditService
        );
    }

    @Test
    void newHireCanSubmitOnePendingOnboardingForm() {
        UserAccount newHire = user(4L, Role.NEW_HIRE);
        when(requestRepository.findFirstByTenantIdAndNewHireIdAndStatusOrderBySubmittedAtDesc(
                1L, 4L, OnboardingRequestStatus.PENDING_HR
        )).thenReturn(Optional.empty());
        when(requestRepository.save(any(OnboardingRequest.class))).thenAnswer(invocation -> {
            OnboardingRequest request = invocation.getArgument(0);
            request.setId(11L);
            return request;
        });
        when(userAccountRepository.findById(4L)).thenReturn(Optional.of(newHire));

        OnboardingDtos.RequestView result = service.create(newHire, validRequest());

        assertThat(result.id()).isEqualTo(11L);
        assertThat(result.status()).isEqualTo(OnboardingRequestStatus.PENDING_HR);
        assertThat(result.idNumberLast4()).isEqualTo("123X");
        assertThat(result.bankCardLast4()).isEqualTo("6789");
        verify(auditService).log(newHire, "SUBMIT_ONBOARDING", "ONBOARDING_REQUEST", 11L, "提交入职登记表");
    }

    @Test
    void ordinaryEmployeeCannotSubmitOnboardingForm() {
        assertThatThrownBy(() -> service.create(user(3L, Role.EMPLOYEE), validRequest()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("新入职员工");
    }

    @Test
    void approvedOnboardingActivatesEmployeeProfile() {
        UserAccount hr = user(2L, Role.HR);
        UserAccount newHire = user(4L, Role.NEW_HIRE);
        newHire.setAccountId(40L);
        OnboardingRequest request = savedRequest();
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setAccountId(40L);
        membership.setWorkspaceId(1L);
        when(requestRepository.findByIdAndTenantId(11L, 1L)).thenReturn(Optional.of(request));
        when(requestRepository.save(request)).thenReturn(request);
        when(userAccountRepository.findById(4L)).thenReturn(Optional.of(newHire));
        when(userAccountRepository.save(newHire)).thenReturn(newHire);
        when(membershipRepository.findByAccountIdAndWorkspaceId(40L, 1L)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(membership)).thenReturn(membership);
        when(leaveBalanceRepository.findByTenantIdAndEmployeeIdAndLeaveType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(leaveBalanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingDtos.RequestView result = service.review(
                hr, 11L, new OnboardingDtos.ReviewRequest(true, "资料齐全")
        );

        assertThat(result.status()).isEqualTo(OnboardingRequestStatus.APPROVED);
        assertThat(newHire.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(newHire.getEmployeeStatus()).isEqualTo(com.hragent.hragentv1.domain.EmployeeStatus.ACTIVE);
        assertThat(newHire.getDepartment()).isEqualTo(request.getDepartment());
        assertThat(membership.getRole()).isEqualTo(Role.EMPLOYEE);
        verify(auditService).log(hr, "APPROVE_ONBOARDING", "ONBOARDING_REQUEST", 11L, "资料齐全");
    }

    @Test
    void rejectionRequiresAnOpinion() {
        UserAccount hr = user(2L, Role.HR);
        when(requestRepository.findByIdAndTenantId(11L, 1L)).thenReturn(Optional.of(savedRequest()));

        assertThatThrownBy(() -> service.review(
                hr, 11L, new OnboardingDtos.ReviewRequest(false, " ")
        )).isInstanceOf(AppException.class).hasMessageContaining("必须填写原因");
    }

    @Test
    void newHireCanConfirmOfficeSupplies() {
        UserAccount newHire = user(4L, Role.NEW_HIRE);
        OnboardingRequest request = savedRequest();
        request.setStatus(OnboardingRequestStatus.APPROVED);
        when(requestRepository.findFirstByTenantIdAndNewHireIdAndStatusOrderBySubmittedAtDesc(
                1L, 4L, OnboardingRequestStatus.APPROVED
        )).thenReturn(Optional.of(request));
        when(requestRepository.save(request)).thenReturn(request);
        when(userAccountRepository.findById(4L)).thenReturn(Optional.of(newHire));

        OnboardingDtos.RequestView result = service.completeOfficeSupplies(newHire);

        assertThat(result.officeSuppliesReceived()).isTrue();
        verify(auditService).log(
                newHire, "COMPLETE_ONBOARDING_OFFICE_SUPPLIES", "ONBOARDING_REQUEST", 11L, "确认领取办公用品"
        );
    }

    private UserAccount user(Long id, Role role) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setTenantId(1L);
        user.setEmployeeNo(role == Role.NEW_HIRE ? "NH001" : "H001");
        user.setName(role == Role.NEW_HIRE ? "Chen Chen" : "Wang HR");
        user.setRole(role);
        return user;
    }

    private OnboardingDtos.CreateRequest validRequest() {
        return new OnboardingDtos.CreateRequest(
                "陈晨", "13800000004", "chenchen@example.com", "123x",
                LocalDate.of(2026, 9, 1), "研发中心", "测试工程师", "李四",
                "北京市海淀区", "陈先生", "13900000004", "招商银行", "6789",
                "本科", true, true, true, true, "Demo 入职登记"
        );
    }

    private OnboardingRequest savedRequest() {
        OnboardingRequest request = new OnboardingRequest();
        request.setId(11L);
        request.setTenantId(1L);
        request.setNewHireId(4L);
        request.setLegalName("陈晨");
        request.setPhone("13800000004");
        request.setPersonalEmail("chenchen@example.com");
        request.setIdNumberLast4("123X");
        request.setPlannedEntryDate(LocalDate.of(2026, 9, 1));
        request.setDepartment("研发中心");
        request.setPositionTitle("测试工程师");
        request.setManagerName("李四");
        request.setWorkLocation("北京市海淀区");
        request.setEmergencyContactName("陈先生");
        request.setEmergencyContactPhone("13900000004");
        request.setBankName("招商银行");
        request.setBankCardLast4("6789");
        request.setHighestEducation("本科");
        request.setIdDocumentPrepared(true);
        request.setBankCardPrepared(true);
        request.setEducationCertificatePrepared(true);
        request.setPhotoPrepared(true);
        request.setStatus(OnboardingRequestStatus.PENDING_HR);
        return request;
    }
}
