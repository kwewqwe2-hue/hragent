package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.repo.LeaveBalanceRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuickConversationTest {
    @Test void shortcutsRunBeforeUnrelatedDocumentSearch() {
        var relations = mock(EmployeeRelationsService.class);
        var reminders = mock(EmployeeReminderService.class);
        var balances = mock(LeaveBalanceRepository.class);
        var policies = mock(PolicyCopilotService.class);
        var handbook = mock(HandbookKnowledgeService.class);
        var services = new EmployeeSelfServiceAssistant(policies, reminders, balances, mock(EmploymentCertificateService.class));
        var router = new EmployeeAgentRouter(relations, services, handbook, policies, mock(PersonalAssistantService.class));
        var user = new UserAccount(); user.setId(3L); user.setTenantId(1L);
        String[][] cases = {
            {"请帮我整理今天需要关注的人事事项和待办。", "提醒"},
            {"我是新员工，请告诉我入职第一天需要完成哪些事项。", "账号"},
            {"请用简洁、清晰的方式帮我理解公司制度中最需要注意的内容。", "休假与考勤"},
            {"请在我申请请假前，帮我列出需要确认的信息和材料。", "起止日期"}
        };
        for (var example : cases) {
            var reply = router.reply(user, example[0]);
            assertThat(reply.handled()).isTrue();
            assertThat(reply.answer()).contains(example[1]);
        }
        verify(reminders).mine(user);
        verify(balances).findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(1L, 3L);
        verifyNoInteractions(policies, handbook);
        assertThat(services.quickReply(user, "合同到期提醒制度有什么规定")).isEmpty();
    }
}
