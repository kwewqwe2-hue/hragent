package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeRelationsDtos.*;
import com.hragent.hragentv1.repo.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeRelationsServiceTest {
    final ErSettingsRepository settings=mock(ErSettingsRepository.class);
    final ErCaseRepository cases=mock(ErCaseRepository.class);
    final ErJourneyTaskRepository tasks=mock(ErJourneyTaskRepository.class);
    final ErPreferenceRepository preferences=mock(ErPreferenceRepository.class);
    final ErSignalRepository signals=mock(ErSignalRepository.class);
    final ErKnowledgeGapRepository gaps=mock(ErKnowledgeGapRepository.class);
    final UserAccountRepository users=mock(UserAccountRepository.class);
    final KnowledgeArticleRepository articles=mock(KnowledgeArticleRepository.class);
    final AuditService audit=mock(AuditService.class);
    final SecretCryptoService crypto=new SecretCryptoService("er-test-encryption-key-no-production");
    final EmployeeRelationsService service=new EmployeeRelationsService(settings,cases,tasks,preferences,signals,gaps,users,articles,crypto,audit,new ErKnowledge());
    final UserAccount employee=user(1L,1L,Role.EMPLOYEE), investigator=user(2L,1L,Role.HR), otherHr=user(3L,1L,Role.HR);
    final ErSettings config=new ErSettings();
    static UserAccount user(Long id,Long tenant,Role role) { var u=new UserAccount(); u.setId(id); u.setTenantId(tenant); u.setRole(role); u.setActive(true); u.setName("测试员工"); u.setEmployeeNo("TEST-"+id); return u; }
    @BeforeEach void setup() {
        config.setTenantId(1L); config.setInvestigatorIds("2");
        when(settings.findByTenantId(1L)).thenReturn(Optional.of(config));
        when(users.findById(1L)).thenReturn(Optional.of(employee));
        when(users.findById(2L)).thenReturn(Optional.of(investigator));
        when(users.findById(3L)).thenReturn(Optional.of(otherHr));
        when(cases.save(any())).thenAnswer(i -> { ErCase c=i.getArgument(0); c.setId(10L); return c; });
    }
    CaseInput complaint(boolean anonymous) { return new CaseInput("COMPLAINT",anonymous,"测试事实需要核实",null,null,"待核实线索",null,true); }
    @Test void anonymousCaseHasNoEmployeeLinkAndOnlyStoresTokenHashAndCiphertext() {
        var result=service.submit(employee,complaint(true));
        var captor=org.mockito.ArgumentCaptor.forClass(ErCase.class); verify(cases).save(captor.capture()); var c=captor.getValue();
        assertThat(c.getEmployeeId()).isNull(); assertThat(c.getAnonymous()).isTrue();
        assertThat(c.getEncryptedBody()).doesNotContain("测试事实");
        assertThat(crypto.decrypt(c.getEncryptedBody())).contains("测试事实");
        assertThat(result.receipt()).hasSize(43); assertThat(c.getReceiptHash()).isEqualTo(EmployeeRelationsService.hash(result.receipt()));
        assertThat(result.caseInfo().employee()).isEqualTo("匿名员工"); verifyNoInteractions(audit);
    }
    @Test void missingInvestigatorsDoesNotCollectComplaintOrPretendHandoff() {
        config.setInvestigatorIds("");
        assertThatThrownBy(() -> service.submit(employee,complaint(true))).hasMessageContaining("尚未指定");
        assertThat(service.triage(employee,"我不想活了").orElseThrow()).contains("没有创建或转交工单","120","12356");
        verify(cases,never()).save(any());
    }
    @Test void configuredCrisisQueuesSupportAndKeepsSubsequentMessagesOutOfAutomation() {
        assertThat(service.triage(employee,"我想伤害自己").orElseThrow()).contains("等待人工接单","实名人工支持工单");
        var captor=org.mockito.ArgumentCaptor.forClass(ErCase.class); verify(cases).save(captor.capture()); var c=captor.getValue();
        when(cases.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(1L,1L)).thenReturn(List.of(c));
        assertThat(service.triage(employee,"我还有几天年假").orElseThrow()).contains("补充已加密加入人工工单");
        assertThat(crypto.decrypt(c.getEncryptedBody())).contains("我还有几天年假");
        c.setStatus("RESOLVED"); assertThat(service.triage(employee,"我还有几天年假")).isEmpty();
    }
    @Test void quotedPreventionQuestionIsNotAutomaticallyReported() {
        assertThat(service.triage(employee,"如何预防职场霸凌")).isEmpty(); verify(cases,never()).save(any());
    }
    @Test void employeeAndOrdinaryHrCannotViewInvestigationQueue() {
        assertThatThrownBy(() -> service.queue(employee)).hasMessageContaining("指定");
        assertThatThrownBy(() -> service.detail(otherHr,10L)).hasMessageContaining("指定");
        verify(cases,never()).findByIdAndTenantId(any(),any());
    }
    @Test void crossTenantCaseCannotBeRead() {
        assertThatThrownBy(() -> service.detail(investigator,99L)).hasMessageContaining("不存在");
        verify(cases).findByIdAndTenantId(99L,1L);
    }
    @Test void anonymousLookupUsesTenantAndReceiptRatherThanEmployeeIdentity() {
        assertThatThrownBy(() -> service.lookup(employee,"invalid-receipt")).hasMessageContaining("无效");
        verify(cases).findByTenantIdAndReceiptHash(1L,EmployeeRelationsService.hash("invalid-receipt"));
    }
    @Test void employeeCannotFollowUpSomeoneElsesNamedCase() {
        var c=new ErCase(); c.setEmployeeId(3L); c.setAnonymous(false);
        when(cases.findByIdAndTenantId(10L,1L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.followup(employee,10L,new Followup("试图改写",null))).hasMessageContaining("不存在");
        verify(cases,never()).save(any());
    }
    @Test void generalHrCannotAppointCrossTenantInvestigator() {
        when(users.findById(4L)).thenReturn(Optional.of(user(4L,2L,Role.HR)));
        assertThatThrownBy(() -> service.saveSettings(otherHr,new SettingsInput("EAP",null,null,null,List.of(4L)))).hasMessageContaining("本企业");
        verify(settings,never()).save(any());
    }
    @Test void unsafeEapLinkIsRejected() {
        assertThatThrownBy(() -> service.saveSettings(otherHr,new SettingsInput("EAP",null,"javascript:alert(1)",null,List.of(2L)))).hasMessageContaining("http");
    }
    @Test void consentIsOffByDefaultAndSensitiveConversationsNeverProduceAnalytics() {
        service.observe(employee,"出差报销标准",false); verify(signals,never()).save(any());
        clearInvocations(preferences,signals,gaps);
        service.observe(employee,"最近抑郁失眠",true); verifyNoInteractions(preferences,signals,gaps);
    }
    @Test void unmetPolicyCreatesOnlyTopicGap() {
        service.observe(employee,"我叫测试姓名，我的出差报销标准是多少",true);
        var captor=org.mockito.ArgumentCaptor.forClass(ErKnowledgeGap.class); verify(gaps).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo("差旅与报销");
        assertThat(captor.getValue().getStatus()).isEqualTo("OPEN"); verify(signals,never()).save(any());
    }
    @Test void withdrawingConsentRemovesRecentPseudonymousSignals() {
        service.consent(employee,false); verify(signals,times(9)).deleteByTenantIdAndParticipant(eq(1L),anyString());
    }
    @Test void insightsSuppressSmallGroupsAndPreviousSmallCounts() {
        LocalDate week=EmployeeReminderService.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var input=new ArrayList<ErSignal>();
        for (int i=0;i<5;i++) { var s=new ErSignal(); s.setDepartment("技术团队"); s.setTopic("加班与调休"); s.setParticipant("p"+i); s.setWeek(week); input.add(s); }
        var small=new ErSignal(); small.setDepartment("小团队"); small.setTopic("考勤制度"); small.setParticipant("p6"); small.setWeek(week); input.add(small);
        when(signals.findByTenantIdAndWeekGreaterThanEqual(1L,week.minusWeeks(1))).thenReturn(input);
        var result=service.insights(otherHr); assertThat(result.topics()).hasSize(1);
        assertThat(result.topics().getFirst().participants()).isEqualTo(5);
        assertThat(result.topics().getFirst().previousParticipants()).isEqualTo(-1);
    }
    @Test void knowledgeDraftsNeverClaimEffectiveCompanyPolicy() {
        assertThat(service.seedDrafts(otherHr)).isEqualTo(8);
        var captor=org.mockito.ArgumentCaptor.forClass(KnowledgeArticle.class); verify(articles,times(8)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(a -> { assertThat(a.getReviewStatus()).isEqualTo("DRAFT"); assertThat(a.getEffectiveFrom()).isNull(); });
    }
    @Test void safetyDetectsPunctuationAndInvisibleCharacterVariants() {
        assertThat(ErSafety.risk("我想跳\u200b 楼")).isEqualTo("URGENT");
        assertThat(ErSafety.risk("我遭到性 骚 扰")).isEqualTo("HUMAN");
        assertThat(ErSafety.risk("I want to kill myself")).isEqualTo("URGENT");
    }
    @Test void unconfiguredEapHasPublicResourceButNoFakeAppointment() {
        assertThat(service.guideReply(employee,"最近工作压力好大").orElseThrow()).contains("12356","企业 EAP 暂未配置","国家卫生健康委");
    }
}
