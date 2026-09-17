package com.hragent.hragentv1.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.hragent.hragentv1.service.EmployeeIntentUnderstanding.*;

class EmployeeIntentUnderstandingTest {
 @Test void distinguishesQuestionsFromOperations(){
  var examples=Map.ofEntries(Map.entry("我的年假还剩几天呀",Action.BALANCE),Map.entry("帮我请个年假，9/15-9/17",Action.START),Map.entry("年假申请批了没呀",Action.PROGRESS),Map.entry("在职证明下载后打不开",Action.DOWNLOAD),Map.entry("我想了解一下报销标准",Action.POLICY),Map.entry("帮我开个工作证明",Action.START),Map.entry("在职证明怎么申请",Action.HOW),Map.entry("帮我看看工资明细",Action.LOOKUP),Map.entry("我现在心情很差",Action.CARE),Map.entry("不办理了",Action.CANCEL));
  examples.forEach((text,action)->assertThat(analyze(text).action()).as(text).isEqualTo(action));
  assertThat(analyze("想问问带薪年假有多少天").topics()).containsExactly("年假");
  assertThat(analyze("我的病假需要诊断证明吗").topics()).containsExactly("病假");
  assertThat(analyze("想问一下五险一金").topics()).containsExactly("社保公积金");
 }
 @Test void commandsAreExistingAuthenticatedOperationsAndLeaveDatesArePreserved(){
  var expected=Map.ofEntries(Map.entry("年假还剩几天","查询我的年假余额"),Map.entry("在职证明办好了吗","查看我的服务申请"),Map.entry("年假批了没","查看我的请假"),Map.entry("下载我的收入证明","下载证明"),Map.entry("帮我开个工作证明","我要开在职证明"),Map.entry("帮我开签证在职证明","我要开出国签证在职证明"),Map.entry("帮我报账","我要办理报销协助"),Map.entry("帮我看看工资明细","查询我的工资条"));
  expected.forEach((text,cmd)->assertThat(command(analyze(text))).as(text).isEqualTo(cmd));
  assertThat(command(analyze("帮我请年假9.15-9.17"))).contains("我要申请年假","9.15-9.17");
  for(String q:List.of("我不要开在职证明","我不想申请年假","年假有多少天","能不能申请年假","确认提交"))assertThat(command(analyze(q))).as(q).isNull();
  assertThat(LifecycleService.intent("我不想开在职证明")).isNull();
  for(String q:List.of("查看在职证明","在职证明批了没","能不能开在职证明","在职证明打不开"))assertThat(LifecycleService.intent(q)).as(q).isNull();
  assertThat(command(analyze("帮我打开在职证明"))).isEqualTo("下载证明");
  assertThat(command(analyze("帮我查一下2026年8月工资条"))).contains("2026年8月");
 }
 @Test void multipleServicesAskOneChoiceWithoutStartingOrSubmitting(){
  var result=clarification("我想请年假，还想开在职证明").orElseThrow();
  assertThat(result.answer()).contains("先处理哪一件");assertThat(result.actions()).hasSize(2).noneMatch(a->a.value().equals("确认提交"));
  assertThat(clarification("帮我报销出差费用")).isEmpty();assertThat(clarification("我想伤害自己，帮我请假和开证明")).isEmpty();
 }
 @Test void contextualProgressAndDownloadRemainScopedToTheService(){
  var guide=new ServiceConversationGuide();var u=LifecycleServiceTest.user(1L,1L,com.hragent.hragentv1.domain.Role.EMPLOYEE);
  guide.observe(u,"在职证明","a",new com.hragent.hragentv1.dto.WebChatDtos.MessageResponse("已提交","hrssc-lifecycle","id"));
  assertThat(guide.command(u,"办好了没呀","a")).contains("查看我的服务申请");assertThat(guide.command(u,"下载后打不开","a")).contains("下载证明");
  assertThat(guide.command(u,"下载后打不开","b")).isEmpty();
  assertThat(guide.command(u,"年假批了没","a")).contains("查看我的请假");
 }
}
