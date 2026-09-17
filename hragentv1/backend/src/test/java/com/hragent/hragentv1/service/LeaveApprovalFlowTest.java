package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.LeaveDtos;
import com.hragent.hragentv1.repo.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveApprovalFlowTest {
    final LeaveRequestRepository leaves = mock(LeaveRequestRepository.class);
    final LeaveBalanceRepository balances = mock(LeaveBalanceRepository.class);
    final UserAccountRepository users = mock(UserAccountRepository.class);
    final AgentNotificationService notices = mock(AgentNotificationService.class);
    final WorkdayService workdays = mock(WorkdayService.class);
    final LeaveService service = new LeaveService(balances, leaves, users, mock(AssistantService.class),
            mock(AuditService.class), workdays, notices, mock(LeaveMedicalService.class));
    final UserAccount employee = user(1L, Role.EMPLOYEE), manager = user(2L, Role.MANAGER), hr = user(3L, Role.HR);
    final LeaveRequest leave = new LeaveRequest();
    final LeaveBalance balance = new LeaveBalance();

    LeaveApprovalFlowTest() {
        leave.setId(10L); leave.setTenantId(1L); leave.setEmployeeId(1L); leave.setManagerId(2L);
        leave.setLeaveType(LeaveType.ANNUAL); leave.setStatus(RequestStatus.PENDING_MANAGER);
        leave.setStartDate(LocalDate.of(2026, 9, 10)); leave.setEndDate(leave.getStartDate()); leave.setDays(BigDecimal.ONE);
        balance.setTotalDays(BigDecimal.TEN); balance.setUsedDays(BigDecimal.ZERO);
        when(leaves.lockForReview(10L, 1L)).thenReturn(Optional.of(leave));
        when(users.findAllById(any())).thenReturn(List.of(employee, manager));
        when(users.findById(1L)).thenReturn(Optional.of(employee));
        when(workdays.workingDates(leave.getStartDate(), leave.getEndDate())).thenReturn(List.of(leave.getStartDate()));
        when(balances.findForUpdateByTenantIdAndEmployeeIdAndLeaveType(1L, 1L, LeaveType.ANNUAL)).thenReturn(Optional.of(balance));
    }
    static UserAccount user(Long id, Role role) {
        var u = new UserAccount(); u.setId(id); u.setTenantId(1L); u.setRole(role); u.setName("测试" + role); return u;
    }
    @Test void managerThenHrProducesTwoStagesAndChargesOnce() {
        var middle = service.managerReview(manager, 10L, new LeaveDtos.ReviewRequest(true, "工作已安排"));
        assertThat(middle.status()).isEqualTo(RequestStatus.PENDING_HR);
        assertThat(middle.managerOpinion()).isEqualTo("工作已安排"); assertThat(middle.hrRecordedAt()).isNull();
        verifyNoInteractions(balances); verify(notices).managerReviewed(leave);
        var result = service.hrRecord(hr, 10L, new LeaveDtos.ReviewRequest(true, "资料齐全"));
        assertThat(result.status()).isEqualTo(RequestStatus.APPROVED); assertThat(result.hrOpinion()).isEqualTo("资料齐全");
        assertThat(balance.getUsedDays()).isEqualByComparingTo("1"); verify(notices).leaveFinalized(leave);
        assertThatThrownBy(() -> service.hrRecord(hr, 10L, new LeaveDtos.ReviewRequest(true, "重复"))).hasMessageContaining("不在 HR");
        verify(balances, times(1)).save(balance); verify(notices, times(1)).leaveFinalized(leave);
    }
    @Test void managerRejectionFinishesWithoutCharging() {
        assertThat(service.managerReview(manager, 10L, new LeaveDtos.ReviewRequest(false, "请调整日期")).status()).isEqualTo(RequestStatus.REJECTED);
        verifyNoInteractions(balances); verify(notices).managerReviewed(leave);
    }
    @Test void hrRejectionPreservesManagerOpinionAndBalance() {
        service.managerReview(manager, 10L, new LeaveDtos.ReviewRequest(true, "同意"));
        var result = service.hrRecord(hr, 10L, new LeaveDtos.ReviewRequest(false, "请补充材料"));
        assertThat(result.status()).isEqualTo(RequestStatus.REJECTED); assertThat(result.managerOpinion()).isEqualTo("同意");
        assertThat(result.hrOpinion()).isEqualTo("请补充材料"); verifyNoInteractions(balances);
    }
    @Test void legacyIntegrationAlsoWaitsForHrAndDuplicateCallbackDoesNotAdvanceIt() {
        service.managerReviewAndAutoRecord(manager, 10L, new LeaveDtos.ReviewRequest(true, "同意"));
        var again = service.managerReviewAndAutoRecord(manager, 10L, new LeaveDtos.ReviewRequest(true, "重复"));
        assertThat(again.status()).isEqualTo(RequestStatus.PENDING_HR); assertThat(again.managerOpinion()).isEqualTo("同意");
        verifyNoInteractions(balances); verify(notices, times(1)).managerReviewed(leave); verify(notices, never()).leaveFinalized(any());
    }
    @Test void wrongRoleUnrelatedManagerAndSelfReviewAreRejected() {
        assertThatThrownBy(() -> service.managerReview(employee, 10L, new LeaveDtos.ReviewRequest(true, "同意"))).hasMessageContaining("仅主管");
        assertThatThrownBy(() -> service.hrRecord(manager, 10L, new LeaveDtos.ReviewRequest(true, "同意"))).hasMessageContaining("仅 HR");
        assertThatThrownBy(() -> service.managerReview(user(9L, Role.MANAGER), 10L, new LeaveDtos.ReviewRequest(true, "同意"))).hasMessageContaining("下属");
        leave.setEmployeeId(2L);
        assertThatThrownBy(() -> service.managerReview(manager, 10L, new LeaveDtos.ReviewRequest(true, "同意"))).hasMessageContaining("本人");
        assertThatThrownBy(() -> service.managerReviewAndAutoRecord(manager, 10L, new LeaveDtos.ReviewRequest(true, "同意"))).hasMessageContaining("本人");
        leave.setEmployeeId(3L); leave.setStatus(RequestStatus.PENDING_HR);
        assertThatThrownBy(() -> service.hrRecord(hr, 10L, new LeaveDtos.ReviewRequest(true, "同意"))).hasMessageContaining("本人");
        verifyNoInteractions(balances, notices);
    }
    @Test void crossTenantAndWrongStageCannotBeReviewed() {
        var other = user(2L, Role.MANAGER); other.setTenantId(2L);
        assertThatThrownBy(() -> service.managerReview(other, 10L, new LeaveDtos.ReviewRequest(true, "同意"))).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.hrRecord(hr, 10L, new LeaveDtos.ReviewRequest(true, "同意"))).hasMessageContaining("不在 HR");
        verify(leaves).lockForReview(10L, 2L); verifyNoInteractions(balances, notices);
    }
    @Test void hrPendingExcludesOwnApplication() {
        var own = new LeaveRequest(); own.setEmployeeId(3L);
        when(leaves.findByTenantIdAndStatusOrderBySubmittedAtDesc(1L, RequestStatus.PENDING_HR)).thenReturn(List.of(own, leave));
        assertThat(service.hrPending(hr)).extracting(LeaveDtos.LeaveRequestView::id).containsExactly(10L);
    }
    @Test void insufficientBalanceDoesNotFinalizeOrNotify() {
        leave.setStatus(RequestStatus.PENDING_HR); balance.setUsedDays(BigDecimal.TEN);
        assertThatThrownBy(() -> service.hrRecord(hr, 10L, new LeaveDtos.ReviewRequest(true, "同意"))).hasMessageContaining("余额不足");
        verify(balances, never()).save(any()); verifyNoInteractions(notices);
    }
}
