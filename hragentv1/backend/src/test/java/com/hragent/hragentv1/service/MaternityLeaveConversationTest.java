package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.UserAccount;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MaternityLeaveConversationTest {
 @Test void applicationFollowUpHasARealStartActionWithoutRepeatingPolicy(){
  for(String question:new String[]{"我应该怎么申请","怎么申请呀","如何办理","去哪里申请","申请流程","帮我申请"}){
   var g=new PolicyConversationGuide();var u=user(1,1);g.opening(u,"我想了解产假","a");g.opening(u,"上海","a");
   var r=g.opening(u,question,"a").orElseThrow();
   assertThat(r.answer()).contains("填写","HRSSC").doesNotContain("158天","哪个城市","具体情况吗");assertThat(r.details()).isNull();
   assertThat(r.actions()).anyMatch(a->a.type().equals("message")&&a.value().equals("我要申请产假"));
   assertThat(LifecycleService.intent("我要申请产假")).isEqualTo("MATERNITY_LEAVE");assertThat(g.interviewRelevant(u,"我要申请产假","a")).isFalse();assertThat(g.opening(u,"我要申请产假","a")).isEmpty();
  }
 }
 @Test void applicationQuestionWithoutContextOffersLeaveChoiceRatherThanGuessingMaternity(){
  var g=new PolicyConversationGuide();var u=user(1,1);var r=g.opening(u,"我应该怎么申请","new").orElseThrow();
  assertThat(r.actions()).anyMatch(a->a.value().equals("我要申请请假")).noneMatch(a->a.value().equals("我要申请产假"));
  assertThat(g.opening(u,"怎么申请贷款","new")).isEmpty();assertThat(g.opening(u,"如何申请签证","new")).isEmpty();
  g.opening(u,"年假","a");r=g.opening(u,"怎么申请","a").orElseThrow();assertThat(r.actions()).anyMatch(a->a.value().equals("我要申请年假"));
 }
 private UserAccount user(long tenant,long employee){var u=mock(UserAccount.class);when(u.getTenantId()).thenReturn(tenant);when(u.getId()).thenReturn(employee);return u;}
 @Test void cityReplyContinuesConversationAndCaveatFollowsTheRule(){
  var guide=new PolicyConversationGuide();var u=user(1,1);
  var first=guide.opening(u,"我想了解一下产假","a").orElseThrow();
  assertThat(first.answer()).contains("哪个城市").doesNotContain("HR","核对","158","适用范围");assertThat(first.details()).isNull();
  assertThat(guide.interviewRelevant(u,"上海","a")).isTrue();
  var second=guide.opening(u,"上海","a").orElseThrow();
  assertThat(second.answer()).contains("158天","98天","60天","HR").doesNotContain("哪个城市");
  assertThat(second.answer().indexOf("HR")).isGreaterThan(second.answer().indexOf("60天"));
  assertThat(second.answer().length()).isLessThan(230);
  assertThat(second.details()).contains("shanghai.gov.cn","第三十一条","2021-11-25","第七条");
  assertThat(guide.opening(u,"难产呢","a").orElseThrow().answer()).contains("增加15天");
  assertThat(guide.opening(u,"需要什么材料","a").orElseThrow().answer()).contains("计划休假","材料").doesNotContain("哪个城市","158天");
 }
 @Test void oneShotAndCityVariantsNeverRequireRepeatingCity(){
  for(String q:new String[]{"我在上海，想了解产假","上海市浦东新区产假多少天"}){
   var r=new MaternityLeaveConversation().reply(user(1,1),q,"a").orElseThrow();
   assertThat(r.answer()).contains("158天","符合法律法规规定生育").doesNotContain("哪个城市");
  }
 }
 @Test void unknownLocalRulesDoNotInheritShanghaiAndNationalChoiceIsRespected(){
  var g=new MaternityLeaveConversation();var u=user(1,1);
  var r=g.reply(u,"北京产假有几天","a").orElseThrow();assertThat(r.answer()).contains("98天","地方增加").doesNotContain("158","60天");
  g.reply(u,"上海产假","a");r=g.reply(u,"只看国家规定","a").orElseThrow();
  assertThat(r.answer()).contains("98天").doesNotContain("158","60天");
 }
 @Test void preservesQuestionFocusAcrossCityClarification(){
  var g=new MaternityLeaveConversation();var u=user(1,1);
  assertThat(g.reply(u,"产假需要什么材料","a").orElseThrow().answer()).contains("哪个城市");
  assertThat(g.reply(u,"上海","a").orElseThrow().answer()).contains("材料","计划休假").doesNotContain("158天","哪个城市");
  assertThat(g.reply(u,"产假几天","a").orElseThrow().answer()).contains("158天");
 }
 @Test void contextIsIsolatedAndNeverMutatesEmployee(){
  var g=new MaternityLeaveConversation();var u=user(1,1);g.reply(u,"产假","a");
  assertThat(g.reply(user(2,1),"上海","a")).isEmpty();assertThat(g.reply(user(1,2),"上海","a")).isEmpty();assertThat(g.reply(u,"上海","b")).isEmpty();
  g.reply(u,"上海","a");verify(u,never()).setDepartment(any());
  assertThat(mockingDetails(u).getInvocations()).allMatch(i->i.getMethod().getName().startsWith("get"));
 }
 @Test void cancellationCareAndOtherRequestsLeaveThePolicyInterview(){
  for(String interruption:new String[]{"结束","不办理了","我要请年假","查询我的年假余额","我现在心情很差","我想伤害自己","出差需要什么材料"}){
   var g=new MaternityLeaveConversation();var u=user(1,1);g.reply(u,"产假","a");
   assertThat(g.reply(u,interruption,"a")).as(interruption).isEmpty();assertThat(g.reply(u,"上海","a")).isEmpty();
  }
  var g=new PolicyConversationGuide();var u=user(1,1);g.opening(u,"产假","a");g.clear(u,"a");assertThat(g.opening(u,"上海","a")).isEmpty();
 }
 @Test void switchingFromAnnualInterviewDoesNotSwallowMaternityCity(){
  var g=new PolicyConversationGuide();var u=user(1,1);g.opening(u,"年假多少天","a");
  assertThat(g.opening(u,"产假","a").orElseThrow().answer()).contains("哪个城市");
  assertThat(g.opening(u,"上海","a").orElseThrow().answer()).contains("158天");
  assertThat(g.opening(u,"年假多少天","a").orElseThrow().answer()).contains("累计工作多久");
 }
 @Test void expiresWithoutLeakingEarlierCity(){
  var clock=mock(Clock.class);when(clock.millis()).thenReturn(0L);var g=new MaternityLeaveConversation(clock);var u=user(1,1);g.reply(u,"产假","a");
  when(clock.millis()).thenReturn(900001L);assertThat(g.relevant(u,"上海","a")).isFalse();assertThat(g.reply(u,"上海","a")).isEmpty();
 }
 @Test void genericClarificationOmitsApplicabilityNotesButSubstantiveAnswerKeepsThem(){
  var g=new PolicyConversationGuide();var u=user(1,1);
  String source="规定｜休假\n"+"不同情形需要结合具体情况处理。".repeat(20)+"\n出处：制度\n\n不能直接认定为个人标准。法律依据与核对提示";
  var r=g.present(u,"婚假有哪些适用条件","a",new EmployeeAgentRouter.Reply(true,source,"company-policy-documents"),"id");
  assertThat(r.answer()).contains("哪个城市").doesNotContain("HR","核对合同","法规补充");
  r=g.present(u,"上海婚假有哪些适用条件","a",new EmployeeAgentRouter.Reply(true,source,"company-policy-documents"),"id");assertThat(r.answer()).contains("上海").doesNotContain("哪个城市");
  r=g.present(u,"工作时间","a",new EmployeeAgentRouter.Reply(true,"规定｜工时\n周一至周五为工作日。\n出处：制度\n\n不能直接认定为个人标准。","company-policy-documents"),"id");
  assertThat(r.answer()).contains("周一至周五","HR");assertThat(r.answer().indexOf("HR")).isGreaterThan(r.answer().indexOf("周一至周五"));
 }
}
