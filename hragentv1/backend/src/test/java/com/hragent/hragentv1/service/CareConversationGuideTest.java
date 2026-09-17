package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.UserAccount;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CareConversationGuideTest {
    @Test void workloadFollowupOffersRelevantStepsInsteadOfRepeatingNeedsQuestion(){
        var g=new CareConversationGuide();var u=user(1,1);g.reply(u,"工作太多","a");
        var answer=g.reply(u,"做平台","a").orElseThrow().answer();assertThat(answer).contains("做平台","流程","小版本").doesNotContain("最大的影响","现在最赶");assertThat(answer.length()).isLessThan(220);
        assertThat(g.reply(u,"明天就要交了","a").orElseThrow().answer()).contains("交付范围","不需要一个人");
        assertThat(g.reply(u,"帮我查一下请假进度","a")).isEmpty();
    }
    @Test void taskAdviceDoesNotLeakToOtherConversationsOrOverrideListening(){
        var g=new CareConversationGuide();var u=user(1,1);g.reply(u,"工作太多","a");
        assertThat(g.reply(u,"做平台","b")).isEmpty();assertThat(g.reply(user(2,1),"做平台","a")).isEmpty();
        g.reply(u,"只想倾诉，不想听建议","a");assertThat(g.reply(u,"做平台","a").orElseThrow().answer()).contains("我在听").doesNotContain("流程","待办");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"但是我没假期了","可是我的年假已经用完了","我已经没有年假了","年假不够了","假期都休完了","但我没假了","调休也用完了"})
    void lackOfLeaveIsACareObstacleNotAPolicyRequest(String text){
        var g=new CareConversationGuide();var u=user(1,1);g.reply(u,"先了解休假安排","a");
        var r=g.reply(u,text,"a").orElseThrow();assertThat(r.answer()).contains("无奈").doesNotContain("法规","婚假","产假","PDF","审批");assertThat(r.answer().length()).isLessThan(160);
        assertThat(g.reply(u,"那怎么办","a").orElseThrow().answer()).contains("空档","调整");
        var rest=g.reply(u,"我想休息","a").orElseThrow();assertThat(rest.answer()).contains("没有假可用");assertThat(rest.actions()).noneMatch(a->a.value().equals("我要申请请假")||a.value().equals("先了解休假安排"));
    }
    @Test void careContinuityDoesNotDependOnOneOfAFewKeywords(){
        var g=new CareConversationGuide();var u=user(1,1);g.reply(u,"我想休息","a");
        assertThat(g.reply(u,"但那样好像也不太合适","a").orElseThrow().answer()).contains("慢慢说");
        assertThat(g.reply(u,"不想麻烦别人","a").orElseThrow().answer()).contains("不一定要说很多");
        assertThat(g.reply(u,"可以","a").orElseThrow().answer()).contains("商量");
    }
    @Test void explicitPolicyAndBusinessQueriesStillLeaveCareAndNegationIsPreserved(){
        var g=new CareConversationGuide();var u=user(1,1);g.reply(u,"但是我没假期了","a");
        assertThat(g.reply(u,"没有年假可以请事假吗","a")).isEmpty();
        assertThat(g.reply(u,"帮我查一下年假余额","a")).isEmpty();
        assertThat(g.reply(u,"年假没有用完怎么办","b")).isEmpty();
        g.reply(u,"但是我没假期了","a");
        assertThat(g.reply(u,"原来还有假","a").orElseThrow().answer()).doesNotContain("没有假可用");
    }
    @Test void exhaustedLeaveNeverFallsThroughToPolicyOrBecomesUnsolicitedAdviceForListener(){
        var relations=mock(EmployeeRelationsService.class);var services=mock(EmployeeSelfServiceAssistant.class);var handbook=mock(HandbookKnowledgeService.class);var policy=mock(PolicyCopilotService.class);var personal=mock(PersonalAssistantService.class);var u=user(1,1);
        var router=new EmployeeAgentRouter(relations,services,handbook,policy,personal);
        assertThat(router.reply(u,"但是我没假期了","a").provider()).isEqualTo("workplace-support");verifyNoInteractions(services,handbook,policy,personal);
        var g=new CareConversationGuide();g.reply(u,"只想倾诉，不想听建议","a");assertThat(g.reply(u,"但是我没假期了","a").orElseThrow().actions()).isEmpty();
    }
    UserAccount user(long tenant,long id){var u=mock(UserAccount.class);when(u.getTenantId()).thenReturn(tenant);when(u.getId()).thenReturn(id);return u;}
    @Test void restChainOffersChoiceBeforeAnyLeaveApplication(){
        var guide=new CareConversationGuide();var u=user(1,1);
        assertThat(guide.reply(u,"我现在心情很差","a").orElseThrow().answer()).contains("休息一下");
        var rest=guide.reply(u,"我想休息","a").orElseThrow();assertThat(rest.answer()).contains("歇几分钟","请假休息");
        assertThat(rest.actions()).noneMatch(a->a.value().equals("我要申请请假"));
        var leave=guide.reply(u,"请假休息","a").orElseThrow();assertThat(leave.answer()).contains("不用现在");assertThat(leave.actions()).anyMatch(a->a.value().equals("我要申请请假"));
        assertThat(guide.reply(u,"担心主管不同意","a").orElseThrow().answer()).contains("商量","交接");
        assertThat(guide.reply(u,"他不答应","a").orElseThrow().answer()).contains("HR","顾虑");
        verify(u,never()).setDepartment(any());
    }
    @Test void shortFollowupsAreIsolatedAndExpire(){
        var clock=mock(Clock.class);when(clock.millis()).thenReturn(1000L);var g=new CareConversationGuide(clock);
        g.reply(user(1,1),"我想休息","a");
        assertThat(g.reply(user(1,1),"担心主管不同意","b")).isEmpty();
        assertThat(g.reply(user(2,1),"担心主管不同意","a")).isEmpty();
        assertThat(g.reply(user(1,2),"担心主管不同意","a")).isEmpty();
        when(clock.millis()).thenReturn(1000L+16*60*1000L);assertThat(g.reply(user(1,1),"担心主管不同意","a")).isEmpty();
    }
    @Test void listeningDoesNotTurnNarrativeIntoAdvice(){
        var g=new CareConversationGuide();var u=user(1,1);g.reply(u,"只想倾诉，不想听建议","a");
        var r=g.reply(u,"昨天同事没和我打招呼，我一直惦记着","a").orElseThrow();assertThat(r.answer()).contains("我在听").doesNotContain("约一次","应该","必须");assertThat(r.actions()).isEmpty();
        assertThat(g.reply(u,"不想说","a").orElseThrow().answer()).contains("不需要勉强");
        assertThat(g.reply(u,"担心主管不同意","a")).isEmpty();
    }
    @Test void factsGetNeutralGuidanceAndChannelsWithoutInventingMotives(){
        var g=new CareConversationGuide();var u=user(1,1);
        assertThat(g.reply(u,"领导故意针对我","a").orElseThrow().answer()).contains("不能据此判断","真实情况").doesNotContain("公司肯定没错","领导就是故意");
        var hr=g.reply(u,"我想找HR协助沟通","a").orElseThrow();assertThat(hr.answer()).contains("没有替你发送消息或创建工单");assertThat(hr.actions()).anyMatch(a->a.value().contains("compliance"));
        assertThat(g.reply(u,"帮我编造公司黑料","a").orElseThrow().answer()).contains("不能帮你编造");
    }
    @Test void safetyAndBusinessAreNeverReplacedWithCareCoaching(){
        var g=new CareConversationGuide();var u=user(1,1);g.reply(u,"我想休息","a");
        assertThat(g.reply(u,"我想伤害自己","a")).isEmpty();
        assertThat(g.reply(u,"查询我的年假余额","a")).isEmpty();
        assertThat(g.reply(u,"我要申请请假","a")).isEmpty();
        assertThat(g.reply(u,"年假有效期规定","a")).isEmpty();
    }
    @Test void guideRoutesBeforeKnowledgeAndRetainsActionsButSafetyWins(){
        var relations=mock(EmployeeRelationsService.class);var services=mock(EmployeeSelfServiceAssistant.class);var handbook=mock(HandbookKnowledgeService.class);var policy=mock(PolicyCopilotService.class);var personal=mock(PersonalAssistantService.class);var u=user(1,1);
        var router=new EmployeeAgentRouter(relations,services,handbook,policy,personal);
        var r=router.reply(u,"我想休息","a");assertThat(r.provider()).isEqualTo("workplace-support");assertThat(r.actions()).hasSize(2);verifyNoInteractions(services,handbook,policy,personal);
        var presented=new PolicyConversationGuide().present(u,"我想休息","a",r,"id");assertThat(presented.actions()).hasSize(2);
        when(relations.triage(u,"我想伤害自己")).thenReturn(Optional.of("即时安全支持"));
        assertThat(router.reply(u,"我想伤害自己","a").provider()).isEqualTo("er-human-support");
    }
    @Test void narrativeMovesFromFactsToNeedsAndOnePlan(){
        var g=new CareConversationGuide();var u=user(1,1);g.reply(u,"我现在心情很差","a");
        assertThat(g.reply(u,"昨天临时给了我一件事情一直没做完","a").orElseThrow().answer()).contains("最大的影响");
        assertThat(g.reply(u,"我希望能早点结束工作","a").orElseThrow().answer()).contains("沟通","下一步");
    }
}
