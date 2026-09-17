package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalAssistantServiceTest {
    private final LeaveBalanceRepository balances=mock(LeaveBalanceRepository.class);
    private final LeaveRequestRepository requests=mock(LeaveRequestRepository.class);
    private final PersonalAssistantService service=new PersonalAssistantService(balances,requests,mock(EmploymentCertificateService.class));
    private UserAccount user() {var u=new UserAccount();u.setId(3L);u.setTenantId(1L);u.setDepartment("研发部");return u;}
    @Test void colloquialQueriesReadOnlyTheRequestedBalance() {
        var b=new LeaveBalance();b.setLeaveType(LeaveType.ANNUAL);b.setTotalDays(new BigDecimal("10"));b.setUsedDays(new BigDecimal("3.5"));
        var other=new LeaveBalance();other.setLeaveType(LeaveType.SICK);other.setTotalDays(new BigDecimal("20"));
        when(balances.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(1L,3L)).thenReturn(List.of(b,other));
        for(String q:List.of("我还有多少年假","我还有几天年假","我还能休多少天年休假","查一下我的年假余额"))
            assertThat(service.reply(user(),q)).contains("你的年假还剩 6.5 天。");
        verify(balances,times(4)).findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(1L,3L);
    }
    @Test void absentDataIsNeverReportedAsZero() {
        when(balances.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(1L,3L)).thenReturn(List.of());
        assertThat(service.reply(user(),"我还有多少年假").orElseThrow()).contains("还没查到").doesNotContain("0 天");
    }
    @Test void policyQuestionDoesNotBecomePersonalAllowance() {
        assertThat(service.reply(user(),"工作满十年，年假规定多少天")).isEmpty();verifyNoInteractions(balances);
    }
    @Test void queriesOwnLeaveStatusAndProfile() {
        var r=new LeaveRequest();r.setLeaveType(LeaveType.ANNUAL);r.setStatus(RequestStatus.PENDING_MANAGER);
        r.setStartDate(java.time.LocalDate.of(2026,9,10));r.setEndDate(java.time.LocalDate.of(2026,9,11));
        when(requests.findByTenantIdAndEmployeeIdOrderBySubmittedAtDesc(1L,3L)).thenReturn(List.of(r));
        assertThat(service.reply(user(),"我的请假批了吗").orElseThrow()).contains("待主管审批","2026-09-10");
        assertThat(service.reply(user(),"我的部门是什么")).contains("部门：研发部");
        verify(requests).findByTenantIdAndEmployeeIdOrderBySubmittedAtDesc(1L,3L);
    }
}
