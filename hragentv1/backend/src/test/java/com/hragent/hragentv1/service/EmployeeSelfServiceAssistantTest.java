package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.PolicyAnswer;
import com.hragent.hragentv1.repo.LeaveBalanceRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeSelfServiceAssistantTest {
    private final PolicyCopilotService policies = mock(PolicyCopilotService.class);
    private final LeaveBalanceRepository balances = mock(LeaveBalanceRepository.class);
    private final EmploymentCertificateService certificates = mock(EmploymentCertificateService.class);
    private final EmployeeSelfServiceAssistant assistant = new EmployeeSelfServiceAssistant(
            policies, mock(EmployeeReminderService.class), balances, certificates);
    @Test void askingAboutApplicationRulesRemainsAPolicyQuery() {
        var employee = new UserAccount();
        when(policies.answer(employee, "申请年假有什么规定")).thenReturn(new PolicyAnswer("经审核的制度依据", "MATCHED", null, List.of(), List.of()));
        assertThat(assistant.reply(employee, "申请年假有什么规定")).contains("经审核的制度依据");
        verifyNoInteractions(certificates);
    }
    @Test void actionRequestIsNotMistakenForPolicyLookup() {
        assertThat(assistant.reply(new UserAccount(), "我要提交年假申请")).isEmpty();
        verifyNoInteractions(policies, certificates);
    }
    @Test void balanceLookupAlwaysUsesAuthenticatedEmployee() {
        var employee = new UserAccount(); employee.setId(3L); employee.setTenantId(1L);
        when(balances.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(1L, 3L)).thenReturn(List.of());
        assertThat(assistant.reply(employee, "查询其他员工的年假余额")).contains("尚未维护你的假期余额，请 HR 核对额度。");
        verify(balances).findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(1L, 3L);
    }
}
