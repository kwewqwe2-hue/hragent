package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.ApiCallLog;
import com.hragent.hragentv1.domain.IntegrationApiKey;
import com.hragent.hragentv1.domain.KnowledgeArticle;
import com.hragent.hragentv1.domain.PolicyMonitorCandidate;
import com.hragent.hragentv1.domain.PolicyMonitorCheckpoint;
import com.hragent.hragentv1.domain.PolicyReviewStatus;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.DemoPolicyDtos;
import com.hragent.hragentv1.repo.ApiCallLogRepository;
import com.hragent.hragentv1.repo.KnowledgeArticleRepository;
import com.hragent.hragentv1.repo.PolicyMonitorCandidateRepository;
import com.hragent.hragentv1.repo.PolicyMonitorCheckpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PolicyMonitorServiceTest {
    @Mock
    private OpenApiService openApiService;
    @Mock
    private PolicyMonitorCandidateRepository candidateRepository;
    @Mock
    private PolicyMonitorCheckpointRepository checkpointRepository;
    @Mock
    private ApiCallLogRepository apiCallLogRepository;
    @Mock
    private KnowledgeArticleRepository knowledgeArticleRepository;
    @Mock
    private KnowledgeIndexClient knowledgeIndexClient;
    @Mock
    private AuditService auditService;

    private PolicyMonitorService service;
    private AtomicReference<PolicyMonitorCandidate> storedCandidate;
    private AtomicReference<PolicyMonitorCheckpoint> storedCheckpoint;
    private Map<String, PolicyMonitorCandidate> storedCandidates;

    @BeforeEach
    void setUp() {
        service = new PolicyMonitorService(
                openApiService,
                candidateRepository,
                checkpointRepository,
                apiCallLogRepository,
                knowledgeArticleRepository,
                knowledgeIndexClient,
                auditService
        );
        storedCandidate = new AtomicReference<>();
        storedCheckpoint = new AtomicReference<>();
        storedCandidates = new HashMap<>();

        IntegrationApiKey key = new IntegrationApiKey();
        key.setId(9L);
        key.setTenantId(3L);
        when(openApiService.authenticate(any(), any(), any())).thenReturn(key);
        when(apiCallLogRepository.save(any(ApiCallLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(checkpointRepository.findByTenantIdAndSourceId(any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(storedCheckpoint.get()));
        when(checkpointRepository.save(any(PolicyMonitorCheckpoint.class)))
                .thenAnswer(invocation -> {
                    PolicyMonitorCheckpoint checkpoint = invocation.getArgument(0);
                    checkpoint.setId(5L);
                    storedCheckpoint.set(checkpoint);
                    return checkpoint;
                });
        when(candidateRepository.findByTenantIdAndSourceIdAndContentHash(any(), any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        storedCandidates.get(invocation.getArgument(2, String.class))));
        when(candidateRepository.save(any(PolicyMonitorCandidate.class)))
                .thenAnswer(invocation -> {
                    PolicyMonitorCandidate candidate = invocation.getArgument(0);
                    candidate.setId(17L);
                    storedCandidate.set(candidate);
                    storedCandidates.put(candidate.getContentHash(), candidate);
                    return candidate;
                });
    }

    @Test
    void persistsBaselineCreatesOneCandidateAndSkipsDuplicates() {
        DemoPolicyDtos.MonitorCheckResult baseline = service.check("hra_demo_key", input("2026.1", "1"));
        DemoPolicyDtos.MonitorCheckResult changed = service.check("hra_demo_key", input("2026.2", "2"));
        DemoPolicyDtos.MonitorCheckResult unchanged = service.check("hra_demo_key", input("2026.2", "2"));
        DemoPolicyDtos.MonitorCheckResult reset = service.check("hra_demo_key", input("2026.1", "1"));
        DemoPolicyDtos.MonitorCheckResult duplicate = service.check("hra_demo_key", input("2026.2", "2"));

        assertThat(baseline.monitorStatus()).isEqualTo("BASELINE_CREATED");
        assertThat(changed.monitorStatus()).isEqualTo("CANDIDATE_CREATED");
        assertThat(changed.candidate().reviewStatus()).isEqualTo(PolicyReviewStatus.PENDING_REVIEW);
        assertThat(unchanged.monitorStatus()).isEqualTo("UNCHANGED");
        assertThat(reset.monitorStatus()).isEqualTo("CANDIDATE_CREATED");
        assertThat(reset.candidate().reviewStatus()).isEqualTo(PolicyReviewStatus.PENDING_REVIEW);
        assertThat(duplicate.monitorStatus()).isEqualTo("DUPLICATE_SKIPPED");
        assertThat(duplicate.candidate().id()).isEqualTo(changed.candidate().id());
    }

    @Test
    void reopensPreviouslyReviewedVersionWhenSourceChangesBack() {
        service.check("hra_demo_key", input("2026.1", "1"));
        DemoPolicyDtos.MonitorCheckResult changed = service.check("hra_demo_key", input("2026.2", "2"));
        storedCandidates.get("2".repeat(64)).setReviewStatus(PolicyReviewStatus.APPROVED);

        service.check("hra_demo_key", input("2026.1", "1"));
        DemoPolicyDtos.MonitorCheckResult changedBack = service.check("hra_demo_key", input("2026.2", "2"));

        assertThat(changedBack.monitorStatus()).isEqualTo("CANDIDATE_REOPENED");
        assertThat(changedBack.candidate().id()).isEqualTo(changed.candidate().id());
        assertThat(changedBack.candidate().reviewStatus()).isEqualTo(PolicyReviewStatus.PENDING_REVIEW);
    }

    @Test
    void approvesCandidateIndexesTextAndCreatesKnowledgeArticle() {
        service.check("hra_demo_key", input("2026.1", "1"));
        service.check("hra_demo_key", input("2026.2", "2"));
        when(candidateRepository.findById(17L))
                .thenAnswer(invocation -> Optional.ofNullable(storedCandidate.get()));
        when(knowledgeArticleRepository.save(any(KnowledgeArticle.class)))
                .thenAnswer(invocation -> {
                    KnowledgeArticle article = invocation.getArgument(0);
                    article.setId(23L);
                    return article;
                });

        DemoPolicyDtos.CandidateView reviewed = service.review(
                hrActor(),
                17L,
                new DemoPolicyDtos.ReviewRequest(PolicyReviewStatus.APPROVED, "内容有效")
        );

        assertThat(reviewed.reviewStatus()).isEqualTo(PolicyReviewStatus.APPROVED);
        assertThat(reviewed.knowledgeArticleId()).isEqualTo(23L);
        assertThat(reviewed.reviewOpinion()).isEqualTo("内容有效");
        verify(knowledgeIndexClient).uploadText(
                "政策演示通知 2026.2-2026.2.txt",
                "标题：政策演示通知 2026.2\n"
                        + "版本：2026.2\n"
                        + "来源：示例市人力资源政策演示中心\n"
                        + "来源地址：http://localhost:5173/policy-source-demo\n"
                        + "适用地区：示例市\n"
                        + "发布日期：2026-08-11\n"
                        + "生效日期：2026-09-01\n"
                        + "摘要：测试摘要\n"
                        + "变更摘要：测试变更\n\n"
                        + "正文：\n测试内容 2026.2"
        );
    }

    @Test
    void rejectsCandidateWithoutWritingKnowledge() {
        service.check("hra_demo_key", input("2026.1", "1"));
        service.check("hra_demo_key", input("2026.2", "2"));
        when(candidateRepository.findById(17L))
                .thenAnswer(invocation -> Optional.ofNullable(storedCandidate.get()));

        DemoPolicyDtos.CandidateView reviewed = service.review(
                hrActor(),
                17L,
                new DemoPolicyDtos.ReviewRequest(PolicyReviewStatus.REJECTED, "来源待核实")
        );

        assertThat(reviewed.reviewStatus()).isEqualTo(PolicyReviewStatus.REJECTED);
        assertThat(reviewed.knowledgeArticleId()).isNull();
        assertThat(reviewed.reviewOpinion()).isEqualTo("来源待核实");
        verifyNoInteractions(knowledgeIndexClient, knowledgeArticleRepository);
    }

    private UserAccount hrActor() {
        UserAccount actor = new UserAccount();
        actor.setId(12L);
        actor.setTenantId(3L);
        actor.setName("王 HR");
        return actor;
    }

    private DemoPolicyDtos.CandidateInput input(String version, String hashSeed) {
        return new DemoPolicyDtos.CandidateInput(
                "demo-employment-policy",
                "示例市人力资源政策演示中心",
                "http://localhost:5173/policy-source-demo",
                "政策演示通知 " + version,
                version,
                "示例市",
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 9, 1),
                "测试摘要",
                "测试内容 " + version,
                "测试变更",
                hashSeed.repeat(64),
                LocalDateTime.of(2026, 8, 11, 12, 0)
        );
    }
}
