package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.UserAccount;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PolicyCatalogTest {
    @Test void broadQuestionsGiveCategoriesAndExamples() {
        for (String q : new String[]{"你有哪些政策知识", "有哪些政策", "你能回答哪些政策问题", "介绍一下知识库", "政策种类有哪些", "能问什么", "有什么制度可以咨询"}) {
            assertThat(PolicyCatalog.reply(q).orElseThrow()).contains("休假与考勤", "薪酬与社保", "福利与费用", "入职与劳动合同", "证明与人事办理", "员工关怀与合规", "你想先了解哪一类", "我还有多少年假");
        }
    }
    @Test void specificPolicyAndPersonalQuestionsRemainRoutedNormally() {
        for(String q:new String[]{"产假有哪些政策", "年假有什么规定", "我还有多少年假", "申请证明需要什么材料", "你好", "休假与考勤有哪些政策", "有哪些政策适用我", "最新政策是什么"})
            assertThat(PolicyCatalog.reply(q)).isEmpty();
        assertThat(PolicyCatalog.reply(null)).isEmpty();
    }
    @Test void catalogRunsBeforeDocumentRetrieval() {
        var relations=mock(EmployeeRelationsService.class);var services=mock(EmployeeSelfServiceAssistant.class);
        var handbook=mock(HandbookKnowledgeService.class);var policies=mock(PolicyCopilotService.class);
        var personal=mock(PersonalAssistantService.class);
        var reply=new EmployeeAgentRouter(relations,services,handbook,policies,personal).reply(new UserAccount(),"你有哪些政策知识");
        assertThat(reply.provider()).isEqualTo("policy-catalog");
        verifyNoInteractions(policies,handbook,services);
    }
}
