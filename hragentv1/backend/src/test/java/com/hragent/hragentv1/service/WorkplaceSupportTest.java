package com.hragent.hragentv1.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.hragent.hragentv1.domain.UserAccount;

class WorkplaceSupportTest {
    @ParameterizedTest @CsvSource(delimiter='|',value={
        "怎么和主管进行沟通相关的工作安排？|MANAGER|优先级",
        "工作太多做不完怎么办|WORKLOAD|取舍",
        "领导批评我，我很委屈|CRITICISM|表达的方式",
        "我今天很开心|POSITIVE|享受这个时刻",
        "怎么安排工作|PLANNING|完成标准",
        "被批评后我觉得自己能力不行|CRITICISM|具体例子",
        "最近工作压力有点大|EMOTION|拆小",
        "和同事有矛盾怎么沟通|CONFLICT|影响",
        "刚入职很紧张，怎么融入团队|ADJUSTMENT|第一周",
        "工作很累，不想上班|BURNOUT|分工",
        "想工作想到睡不着|SLEEP|明天",
        "只想倾诉，不想听建议|LISTEN|不急着找办法",
        "他不同意怎么办|PUSHBACK|最在意的限制",
        "我焦虑得睡不好|SLEEP|专业人员"
    }) void addressesActualSituation(String q,String intent,String expected){
        assertThat(WorkplaceSupport.intent(q)).isEqualTo(intent);
        String reply=WorkplaceSupport.reply(q).orElseThrow();assertThat(reply).contains(expected).doesNotContain("PDF第","根据你们提供的制度文件","你患有");
    }
    @ParameterizedTest @CsvSource({"出差有哪些规定？","病假工资怎么算","事假3天谁审批","心理咨询福利制度有哪些","主管审批权限规定","年假有效期到什么时候"})
    void factualPolicyQuestionsStillUseKnowledge(String q){assertThat(WorkplaceSupport.reply(q)).isEmpty();}
    @Test void listenerDoesNotForceActionPlan(){assertThat(WorkplaceSupport.reply("只想倾诉，不想听建议").orElseThrow()).doesNotContain("1.","你应该","必须");}
    @Test void ordinaryConversationDoesNotAutomaticallySuggestPsychologicalReferral(){
        assertThat(WorkplaceSupport.emotional("我今天很开心")).isFalse();
        assertThat(WorkplaceSupport.emotional("只想倾诉，不想听建议")).isFalse();
        assertThat(WorkplaceSupport.emotional("怎么安排工作")).isFalse();
    }
    @Test void neverDiagnosesDepression(){assertThat(WorkplaceSupport.reply("我觉得自己抑郁了，能诊断吗").orElseThrow()).contains("不能判断","专业人员").doesNotContain("你患有抑郁症");}
    @Test void supportPrecedesAnyPolicyOrBusinessLookup(){
        var relations=mock(EmployeeRelationsService.class);var services=mock(EmployeeSelfServiceAssistant.class);var handbook=mock(HandbookKnowledgeService.class);var policies=mock(PolicyCopilotService.class);var personal=mock(PersonalAssistantService.class);var user=new UserAccount();String q="怎么和主管沟通工作安排";
        when(relations.supportReply(user,q)).thenReturn(WorkplaceSupport.reply(q));
        var r=new EmployeeAgentRouter(relations,services,handbook,policies,personal).reply(user,q);
        assertThat(r.provider()).isEqualTo("workplace-support");verifyNoInteractions(policies,handbook,personal,services);
    }
    @Test void urgentSupportStillHasPriority(){
        var relations=mock(EmployeeRelationsService.class);var services=mock(EmployeeSelfServiceAssistant.class);var handbook=mock(HandbookKnowledgeService.class);var policies=mock(PolicyCopilotService.class);var personal=mock(PersonalAssistantService.class);var user=new UserAccount();String q="工作太多，我想伤害自己";
        when(relations.triage(user,q)).thenReturn(Optional.of("请先确保此刻安全"));
        assertThat(new EmployeeAgentRouter(relations,services,handbook,policies,personal).reply(user,q).provider()).isEqualTo("er-human-support");
        verify(relations,never()).supportReply(any(),any());verifyNoInteractions(policies,handbook,personal,services);
    }
}
