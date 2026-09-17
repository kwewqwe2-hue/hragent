package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeRelationsDtos.JourneyTask;
import com.hragent.hragentv1.web.AppException;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AssistantWorkspaceServiceTest {
 final EmployeeRelationsService relations=mock(EmployeeRelationsService.class);
 final EmployeeReminderService reminders=mock(EmployeeReminderService.class);
 final EmployeePersonalProfileService profiles=mock(EmployeePersonalProfileService.class);
 final LeaveService leaves=mock(LeaveService.class);
 final AssistantWorkspaceService service=new AssistantWorkspaceService(relations,reminders,profiles,leaves);
 final UserAccount u=new UserAccount();
 @BeforeEach void setup(){u.setId(1L);u.setTenantId(2L);u.setRole(Role.EMPLOYEE);u.setEmployeeStatus(EmployeeStatus.ACTIVE);}
 @Test void progressQuestionsReadFreshResultsEachTime(){
  var row=mock(com.hragent.hragentv1.dto.LeaveDtos.LeaveRequestView.class);when(row.statusLabel()).thenReturn("等待主管审批","审核通过");when(leaves.mine(u)).thenReturn(List.of(row));
  assertThat(service.reply(u,"查进度").orElseThrow().answer()).contains("等待主管审批","Kaka").doesNotContain("页面上方");
  assertThat(service.reply(u,"批了吗").orElseThrow().answer()).contains("审核通过");verify(leaves,times(2)).mine(u);
 }
 @Test void annualBalanceShowsRealQuotaAndDeductsPendingRequests(){
  when(leaves.agentBalances(u)).thenReturn(List.of(new com.hragent.hragentv1.dto.AgentIntegrationDtos.BalanceLine("ANNUAL","年假",java.math.BigDecimal.TEN,java.math.BigDecimal.ONE,java.math.BigDecimal.valueOf(2),java.math.BigDecimal.valueOf(7))));
  assertThat(service.reply(u,"查询我的年假余额").orElseThrow().answer()).contains("总额度是 10 天","已休 1 天","2 天正在审批","**7 天**");
 }
 @Test void catalogCoversEmployeeRoutesWithoutWrites(){var r=service.reply(u,"查看所有员工服务").orElseThrow();assertThat(r.actions()).anyMatch(a->a.value().contains("compliance"));assertThat(r.actions()).anyMatch(a->a.value().equals("查看我的个人信息"));assertThat(r.actions()).noneMatch(a->a.value().equals("/manager-approval")||a.value().equals("/hrssc"));verifyNoInteractions(relations,reminders,profiles,leaves);}
 @Test void roleScopedCatalog(){u.setRole(Role.MANAGER);assertThat(service.reply(u,"查看所有员工服务").orElseThrow().actions()).anyMatch(a->a.value().equals("/manager-approval"));u.setEmployeeStatus(EmployeeStatus.LEFT);assertThat(service.reply(u,"查看所有员工服务").orElseThrow().actions()).noneMatch(a->a.type().equals("panel"));assertThat(service.reply(u,"查看我的个人信息")).isEmpty();}
 @Test void taskDoesNotChangeUntilConfirmed(){String key="weekly-2026-09-07-priorities";when(relations.journey(u)).thenReturn(List.of(new JourneyTask(key,"本周","明确重点","说明",false,null)));var preview=service.reply(u,"标记任务 "+key).orElseThrow();assertThat(preview.answer()).contains("确认");verify(relations,never()).setTask(any(),anyString(),anyBoolean());service.reply(u,"确认完成任务 "+key);verify(relations).setTask(u,key,true);}
 @Test void foreignOrExpiredTaskCannotBeUpdated(){when(relations.journey(u)).thenReturn(List.of());assertThatThrownBy(()->service.reply(u,"确认完成任务 weekly-2020-01-01-priorities")).isInstanceOf(AppException.class);verify(relations,never()).setTask(any(),anyString(),anyBoolean());}
 @Test void unknownRequestFallsThroughToLifecycleAndAi(){assertThat(service.reply(u,"我要申请请假")).isEmpty();assertThat(service.reply(u,"我压力很大")).isEmpty();verifyNoInteractions(relations,reminders,profiles,leaves);}
 @Test void remindersRequireOwnedRecordAndConfirmation(){var row=mock(EmployeeServiceTask.class);when(row.getId()).thenReturn(6L);when(row.getTitle()).thenReturn("合同续签");when(reminders.mine(u)).thenReturn(List.of(row));assertThat(service.reply(u,"处理提醒 6").orElseThrow().answer()).contains("不会替你完成");verify(reminders,never()).update(any(),anyLong(),any());service.reply(u,"确认完成提醒 6");verify(reminders).update(eq(u),eq(6L),argThat(a->a.action().equals("DONE")));assertThatThrownBy(()->service.reply(u,"确认完成提醒 7")).isInstanceOf(AppException.class);}
}
