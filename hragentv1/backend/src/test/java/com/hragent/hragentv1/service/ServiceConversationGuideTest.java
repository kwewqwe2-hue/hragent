package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.WebChatDtos.*;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceConversationGuideTest {
 private UserAccount user(long tenant,long id){var u=mock(UserAccount.class);when(u.getTenantId()).thenReturn(tenant);when(u.getId()).thenReturn(id);return u;}
 private MessageResponse reply(String provider,ChatAction... actions){return new MessageResponse("说明",provider,"id",List.of(actions));}
 @Test void everySupportedServiceCanMoveFromExplanationToGuidanceAndAnExecutableCommand(){
  var expected=Map.ofEntries(Map.entry("年假","LEAVE"),Map.entry("病假","LEAVE"),Map.entry("婚假","LEAVE"),Map.entry("产假","MATERNITY_LEAVE"),Map.entry("在职证明","EMPLOYMENT_CERT"),Map.entry("收入证明","INCOME_CERT"),Map.entry("签证在职证明","VISA_CERT"),Map.entry("离职证明","EXIT_CERT"),Map.entry("劳动关系证明","RELATION_CERT"),Map.entry("社保公积金","SOCIAL"),Map.entry("合同续签","CONTRACT"),Map.entry("入职材料","ONBOARDING"),Map.entry("离职","EXIT"),Map.entry("离职结算","SETTLEMENT"),Map.entry("档案","ARCHIVE"),Map.entry("人事变更","CHANGE"),Map.entry("报销","SERVICE_HELP"),Map.entry("借款","SERVICE_HELP"),Map.entry("出差","SERVICE_HELP"),Map.entry("加班调休","SERVICE_HELP"),Map.entry("福利","SERVICE_HELP"),Map.entry("育儿假","SERVICE_HELP"));
  for(var entry:expected.entrySet())for(String question:List.of("我应该怎么申请","怎么填写呀","申请流程","下一步呢","接下来怎么办")){
   var g=new ServiceConversationGuide();var u=user(1,1);g.observe(u,"我想了解"+entry.getKey(),"a",reply("company-policy-documents"));
   var r=g.guidance(u,question,"a").orElseThrow();assertThat(r.answer().length()).as(entry.getKey()).isLessThan(230);assertThat(r.details()).isNull();
   String command=g.command(u,"帮我填写","a").orElseThrow();assertThat(LifecycleService.intent(command)).as(entry.getKey()).isEqualTo(entry.getValue());assertThat(r.actions()).anyMatch(a->a.value().equals(command));assertThat(command).isNotEqualTo("确认提交");
  }
 }
 @Test void draftFollowupResumesCurrentFieldAndDoesNotRestartOrSubmit(){
  var g=new ServiceConversationGuide();var u=user(1,1);
  g.observe(u,"我要申请产假","a",reply("hrssc-lifecycle",LifecycleNavigation.say("取消办理","取消办理")));
  for(String q:List.of("下一步呢","帮我填写","开始吧","我应该怎么申请"))assertThat(g.command(u,q,"a")).contains("继续办理");
  assertThat(g.guidance(u,"继续办理","a")).isEmpty();assertThat(g.command(u,"确认提交","a")).isEmpty();
  assertThat(g.command(u,"如何申请贷款","a")).isEmpty();assertThat(g.guidance(u,"如何申请贷款","a")).isEmpty();
 }
 @Test void progressFollowsTheRightBusinessAndExplicitNewTopicWins(){
  var g=new ServiceConversationGuide();var u=user(1,1);g.observe(u,"收入证明","a",reply("employee-services"));
  assertThat(g.command(u,"查进度","a")).contains("查看我的服务申请");assertThat(g.command(u,"我的年假审批进度","a")).contains("查看我的请假");
  g.observe(u,"年假","b",reply("company-policy-documents"));assertThat(g.command(u,"批了吗","b")).contains("查看我的请假");
  var r=g.guidance(u,"社保怎么申请","b").orElseThrow();assertThat(r.actions()).anyMatch(a->a.value().equals("我要咨询社保公积金")).noneMatch(a->a.value().equals("我要申请年假"));
  assertThat(g.command(u,"产假审核进度","fresh")).contains("查看我的服务申请");
 }
 @Test void missingContextAsksForTheServiceInsteadOfGuessing(){
  var g=new ServiceConversationGuide();var u=user(1,1);var r=g.guidance(u,"我应该怎么申请","a").orElseThrow();assertThat(r.actions()).anyMatch(a->a.value().equals("我要申请请假")).anyMatch(a->a.value().equals("我要开在职证明"));
  assertThat(g.command(u,"帮我填写","a")).isEmpty();r=g.guidance(u,"查进度","a").orElseThrow();assertThat(r.actions()).hasSize(2);
  g.observe(u,"证明","a",reply("employee-services"));r=g.guidance(u,"怎么申请","a").orElseThrow();assertThat(r.actions()).anyMatch(a->a.value().equals("我要开收入证明"));assertThat(g.command(u,"帮我填写","a")).isEmpty();
 }
 @Test void contextIsIsolatedExpiresAndDoesNotSurviveUnrelatedOrSafetyTurns(){
  var clock=mock(Clock.class);when(clock.millis()).thenReturn(0L);var g=new ServiceConversationGuide(clock);var u=user(1,1);g.observe(u,"病假","a",reply("company-policy-documents"));
  assertThat(g.command(user(2,1),"帮我填写","a")).isEmpty();assertThat(g.command(user(1,2),"帮我填写","a")).isEmpty();assertThat(g.command(u,"帮我填写","b")).isEmpty();
  when(clock.millis()).thenReturn(900001L);assertThat(g.command(u,"帮我填写","a")).isEmpty();
  for(String interrupt:List.of("我想伤害自己","我现在心情很差","取消办理","天气怎么样")){
   g.observe(u,"病假","a",reply("company-policy-documents"));g.observe(u,interrupt,"a",reply("employee-services"));assertThat(g.command(u,"帮我填写","a")).as(interrupt).isEmpty();
  }
 }
 @Test void informativeQuestionsNeverBecomeApplicationsAndPanelsPointToExistingRoutes(){
  for(String q:List.of("我想了解入职材料","我想了解一下人事变更","产假怎样申请","我想了解社保","我想了解在职证明","我想了解收入证明","想咨询签证证明材料"))assertThat(LifecycleService.intent(q)).as(q).isNull();
  var g=new ServiceConversationGuide();var u=user(1,1);g.observe(u,"工作计划","a",reply("assistant-workspace"));
  var r=g.guidance(u,"下一步呢","a").orElseThrow();assertThat(r.actions()).anyMatch(a->a.type().equals("workbench")&&a.value().equals("/employee-experience?section=onboarding"));
  assertThat(g.guidance(u,"我要举报职场霸凌怎么申请","a")).isEmpty();
 }
}
