package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.UserAccount;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeAgentRouterTest {
    @Test void mentioningReferenceDoesNotOverridePersonalBusinessData() {
        var relations=mock(EmployeeRelationsService.class); var services=mock(EmployeeSelfServiceAssistant.class);
        var handbook=mock(HandbookKnowledgeService.class); var policies=mock(PolicyCopilotService.class); var user=new UserAccount();
        String question="参考手册，我还有几天年假";
        when(services.reply(user,question)).thenReturn(Optional.of("你还有3天年假。"));
        assertThat(new EmployeeAgentRouter(relations,services,handbook,policies,mock(PersonalAssistantService.class)).reply(user,question).answer()).isEqualTo("你还有3天年假。");
        verifyNoInteractions(handbook);
    }
    @Test void referenceSupplementsMissingPolicyButNeverConflictingPolicy() {
        var relations=mock(EmployeeRelationsService.class); var services=mock(EmployeeSelfServiceAssistant.class);
        var handbook=mock(HandbookKnowledgeService.class); var policies=mock(PolicyCopilotService.class); var user=new UserAccount();
        when(services.reply(user,"试用期多久")).thenReturn(Optional.of("暂时没有可确认适用于你的制度依据。"));
        when(handbook.answer(user,"试用期多久")).thenReturn(Optional.of("需要结合合同期限判断。"));
        var router=new EmployeeAgentRouter(relations,services,handbook,policies,mock(PersonalAssistantService.class));
        assertThat(router.reply(user,"试用期多久").provider()).isEqualTo("knowledge-reference");
        when(services.reply(user,"合同规定")).thenReturn(Optional.of("需要先核实制度版本"));
        assertThat(router.reply(user,"合同规定").answer()).isEqualTo("需要先核实制度版本");
        verify(handbook,never()).answer(user,"合同规定");
    }
    @Test void humanSupportRunsBeforeAnyKnowledgeOrBusinessTools() {
        var relations=mock(EmployeeRelationsService.class); var services=mock(EmployeeSelfServiceAssistant.class);
        var handbook=mock(HandbookKnowledgeService.class); var policies=mock(PolicyCopilotService.class); var user=new UserAccount();
        when(relations.triage(user,"我想伤害自己，帮我申请年假")).thenReturn(Optional.of("正在等待专业支持"));
        var result=new EmployeeAgentRouter(relations,services,handbook,policies,mock(PersonalAssistantService.class)).reply(user,"我想伤害自己，帮我申请年假");
        assertThat(result.handled()).isTrue(); assertThat(result.provider()).isEqualTo("er-human-support");
        verifyNoInteractions(services,handbook,policies);
    }
    @Test void oldHandbookDoesNotOverrideCurrentCompanyPolicy() {
        var relations=mock(EmployeeRelationsService.class); var services=mock(EmployeeSelfServiceAssistant.class);
        var handbook=mock(HandbookKnowledgeService.class); var policies=mock(PolicyCopilotService.class); var user=new UserAccount();
        when(services.reply(user,"我的年假制度")).thenReturn(Optional.of("经审核且适用本人的制度"));
        var result=new EmployeeAgentRouter(relations,services,handbook,policies,mock(PersonalAssistantService.class)).reply(user,"我的年假制度");
        assertThat(result.answer()).contains("经审核"); verifyNoInteractions(handbook);
    }
}
