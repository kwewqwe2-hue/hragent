package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import com.hragent.hragentv1.repo.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EmployeeReminderServiceTest {
    private final EmployeeServiceTaskRepository repository = mock(EmployeeServiceTaskRepository.class);
    private final EmployeeServiceProfileService profiles = mock(EmployeeServiceProfileService.class);
    private final LeaveBalanceRepository balances = mock(LeaveBalanceRepository.class);
    private final LifecycleRequestRepository lifecycle = mock(LifecycleRequestRepository.class);
    private final EmployeeReminderService service = new EmployeeReminderService(repository, profiles, balances, mock(AuditService.class), lifecycle);
    @Test void approvedExitCreatesReminderAndCompletionRemovesIt() {
        setup(null,null,null,null);
        var request=new LifecycleRequest();request.id=81L;request.kind="EXIT";request.status="APPROVED";request.dueDate=today.plusDays(3);
        when(lifecycle.findByTenantIdAndEmployeeIdOrderByUpdatedAtDesc(1L,3L)).thenReturn(List.of(request));
        assertThat(service.mine(user)).anyMatch(t->t.getTitle().equals("离职交接准备"));
        request.status="COMPLETED";
        assertThat(service.mine(user)).noneMatch(t->t.getTitle().equals("离职交接准备"));
    }
    private final UserAccount user = new UserAccount();
    private final LocalDate today = EmployeeReminderService.today();
    private final List<EmployeeServiceTask> saved = new ArrayList<>();
    private void setup(LocalDate probation, LocalDate contract, LocalDate medical, LocalDate annual) {
        user.setId(3L); user.setTenantId(1L); user.setRole(Role.EMPLOYEE);
        when(profiles.profile(user)).thenReturn(new ProfileView(3L, "E003", "员工", "上海", "P3", "研发", "示例公司",
                probation, contract, medical, annual, null, List.of()));
        when(repository.findByTenantIdAndEmployeeIdOrderByDueDateAsc(1L, 3L)).thenAnswer(i -> new ArrayList<>(saved));
        when(repository.save(any())).thenAnswer(i -> { EmployeeServiceTask task = i.getArgument(0); if (!saved.contains(task)) saved.add(task); return task; });
        var balance = new LeaveBalance(); balance.setLeaveType(LeaveType.ANNUAL); balance.setTotalDays(new BigDecimal("5"));
        when(balances.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(1L, 3L)).thenReturn(List.of(balance));
    }
    @Test void generatesAllFourEventsAndDoesNotDuplicateOnPolling() {
        setup(today.plusDays(30), today.plusDays(60), today.plusDays(30), today.plusDays(60));
        assertThat(service.mine(user)).hasSize(4);
        service.mine(user); assertThat(saved).hasSize(4);
        assertThat(saved).extracting(EmployeeServiceTask::getKind).containsExactlyInAnyOrder("PROBATION", "CONTRACT", "MEDICAL", "ANNUAL_LEAVE");
    }
    @Test void noInventedDatesOrPrematureEvents() {
        setup(null, today.plusDays(61), null, null);
        assertThat(service.mine(user)).isEmpty();
    }
    @Test void changedDatesRetireOldTasksButCompletedTasksStayCompleted() {
        setup(today, null, null, null);
        var task = service.mine(user).getFirst(); task.setStatus("DONE");
        assertThat(service.mine(user).getFirst().getStatus()).isEqualTo("DONE");
        when(profiles.profile(user)).thenReturn(new ProfileView(3L, "E003", "员工", "上海", "P3", "研发", "公司",
                today.plusDays(10), null, null, null, null, List.of()));
        assertThat(service.mine(user)).hasSize(1);
        assertThat(task.getStatus()).isEqualTo("OBSOLETE");
    }
    @Test void zeroBalanceCancelsAnnualLeaveWarning() {
        setup(null, null, null, today.plusDays(1)); service.mine(user);
        var balance = new LeaveBalance(); balance.setLeaveType(LeaveType.ANNUAL);
        when(balances.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(1L, 3L)).thenReturn(List.of(balance));
        assertThat(service.mine(user)).isEmpty();
    }
    @Test void cannotUpdateAnotherEmployeesReminder() {
        user.setTenantId(1L); user.setId(3L);
        when(repository.findByIdAndTenantIdAndEmployeeId(99L, 1L, 3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(user, 99L, new TaskAction("DONE", null))).hasMessageContaining("不存在");
        verify(repository, never()).save(any());
    }
    @Test void snoozeMustBeAFutureDateWithinThirtyDays() {
        user.setTenantId(1L); user.setId(3L);
        var task = new EmployeeServiceTask();
        when(repository.findByIdAndTenantIdAndEmployeeId(1L, 1L, 3L)).thenReturn(Optional.of(task));
        assertThatThrownBy(() -> service.update(user, 1L, new TaskAction("SNOOZE", today))).hasMessageContaining("未来 30 天");
        assertThatThrownBy(() -> service.update(user, 1L, new TaskAction("SNOOZE", today.plusDays(31)))).hasMessageContaining("未来 30 天");
    }
    @Test void calendarContainsOnlyOpenEventsWithDateAndAlarm() {
        setup(today, null, null, null);
        assertThat(service.calendar(user)).contains("BEGIN:VCALENDAR", "DTSTART;VALUE=DATE:", "BEGIN:VALARM", "转正答辩准备");
        saved.getFirst().setStatus("DONE");
        assertThat(service.calendar(user)).doesNotContain("BEGIN:VEVENT");
    }
}
