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
import com.hragent.hragentv1.web.AppException;
import com.hragent.hragentv1.web.RequestCorrelation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PolicyMonitorService {
    private static final String CHECK_PATH = "/internal/agent/v1/policy-monitor/check";

    private final OpenApiService openApiService;
    private final PolicyMonitorCandidateRepository candidateRepository;
    private final PolicyMonitorCheckpointRepository checkpointRepository;
    private final ApiCallLogRepository apiCallLogRepository;
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final KnowledgeIndexClient knowledgeIndexClient;
    private final AuditService auditService;

    public PolicyMonitorService(
            OpenApiService openApiService,
            PolicyMonitorCandidateRepository candidateRepository,
            PolicyMonitorCheckpointRepository checkpointRepository,
            ApiCallLogRepository apiCallLogRepository,
            KnowledgeArticleRepository knowledgeArticleRepository,
            KnowledgeIndexClient knowledgeIndexClient,
            AuditService auditService
    ) {
        this.openApiService = openApiService;
        this.candidateRepository = candidateRepository;
        this.checkpointRepository = checkpointRepository;
        this.apiCallLogRepository = apiCallLogRepository;
        this.knowledgeArticleRepository = knowledgeArticleRepository;
        this.knowledgeIndexClient = knowledgeIndexClient;
        this.auditService = auditService;
    }

    @Transactional
    public DemoPolicyDtos.MonitorCheckResult check(
            String rawApiKey,
            DemoPolicyDtos.CandidateInput input
    ) {
        IntegrationApiKey key = openApiService.authenticate(rawApiKey, "POST", CHECK_PATH);
        String sourceId = input.sourceId().trim();
        String currentHash = input.contentHash().trim().toLowerCase();
        String currentVersion = input.version().trim();
        PolicyMonitorCheckpoint checkpoint = checkpointRepository
                .findByTenantIdAndSourceId(key.getTenantId(), sourceId)
                .orElse(null);

        if (checkpoint == null) {
            checkpoint = new PolicyMonitorCheckpoint();
            checkpoint.setTenantId(key.getTenantId());
            checkpoint.setSourceId(sourceId);
            updateCheckpoint(checkpoint, currentVersion, currentHash);
            log(key, 200, "Policy monitor baseline created: " + currentVersion);
            return result("BASELINE_CREATED", null, null);
        }

        String previousVersion = checkpoint.getLastVersion();
        if (checkpoint.getLastContentHash().equals(currentHash)) {
            checkpoint.setLastCheckedAt(LocalDateTime.now());
            checkpointRepository.save(checkpoint);
            log(key, 200, "Policy source unchanged: " + currentVersion);
            return result("UNCHANGED", previousVersion, null);
        }

        PolicyMonitorCandidate candidate = candidateRepository
                .findByTenantIdAndSourceIdAndContentHash(key.getTenantId(), sourceId, currentHash)
                .orElse(null);
        String monitorStatus;
        int statusCode;
        if (candidate == null) {
            candidate = createCandidate(key.getTenantId(), sourceId, currentHash, input);
            monitorStatus = "CANDIDATE_CREATED";
            statusCode = 201;
        } else if (candidate.getReviewStatus() != PolicyReviewStatus.PENDING_REVIEW) {
            reopenCandidate(candidate, input);
            monitorStatus = "CANDIDATE_REOPENED";
            statusCode = 201;
        } else {
            monitorStatus = "DUPLICATE_SKIPPED";
            statusCode = 200;
        }

        updateCheckpoint(checkpoint, currentVersion, currentHash);
        log(key, statusCode, "Policy monitor result " + monitorStatus + ": " + currentVersion);
        return result(monitorStatus, previousVersion, candidate);
    }

    private void reopenCandidate(
            PolicyMonitorCandidate candidate,
            DemoPolicyDtos.CandidateInput input
    ) {
        candidate.setSourceName(input.sourceName().trim());
        candidate.setSourceUrl(input.sourceUrl().trim());
        candidate.setTitle(input.title().trim());
        candidate.setVersion(input.version().trim());
        candidate.setRegion(trimToNull(input.region()));
        candidate.setPublishedAt(input.publishedAt());
        candidate.setEffectiveAt(input.effectiveAt());
        candidate.setSummary(trimToNull(input.summary()));
        candidate.setContent(input.content().trim());
        candidate.setChangeSummary(trimToNull(input.changeSummary()));
        candidate.setSourceUpdatedAt(input.sourceUpdatedAt());
        candidate.setDetectedAt(LocalDateTime.now());
        candidate.setReviewStatus(PolicyReviewStatus.PENDING_REVIEW);
        candidate.setReviewedByUserId(null);
        candidate.setReviewedAt(null);
        candidate.setReviewOpinion(null);
        candidate.setKnowledgeArticleId(null);
        candidateRepository.save(candidate);
    }

    public List<DemoPolicyDtos.CandidateView> list(Long tenantId) {
        return candidateRepository.findByTenantIdOrderByDetectedAtDesc(tenantId)
                .stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public DemoPolicyDtos.CandidateView review(
            UserAccount actor,
            Long candidateId,
            DemoPolicyDtos.ReviewRequest request
    ) {
        PolicyMonitorCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> AppException.notFound("政策候选不存在"));
        if (!candidate.getTenantId().equals(actor.getTenantId())) {
            throw AppException.notFound("政策候选不存在");
        }
        if (candidate.getReviewStatus() != PolicyReviewStatus.PENDING_REVIEW) {
            throw AppException.badRequest("该政策候选已经完成审核，不能重复操作");
        }
        if (request.decision() == PolicyReviewStatus.PENDING_REVIEW) {
            throw AppException.badRequest("审核结果只能选择通过或驳回");
        }

        String opinion = trimToNull(request.opinion());
        if (request.decision() == PolicyReviewStatus.REJECTED && opinion == null) {
            throw AppException.badRequest("驳回政策候选时必须填写审核意见");
        }

        if (request.decision() == PolicyReviewStatus.APPROVED) {
            KnowledgeArticle article = publishKnowledge(actor, candidate);
            candidate.setKnowledgeArticleId(article.getId());
        }
        candidate.setReviewStatus(request.decision());
        candidate.setReviewedByUserId(actor.getId());
        candidate.setReviewedAt(LocalDateTime.now());
        candidate.setReviewOpinion(opinion);
        PolicyMonitorCandidate saved = candidateRepository.save(candidate);
        auditService.log(
                actor,
                request.decision() == PolicyReviewStatus.APPROVED
                        ? "APPROVE_POLICY_CANDIDATE"
                        : "REJECT_POLICY_CANDIDATE",
                "policy_monitor_candidate",
                saved.getId(),
                saved.getTitle() + " " + saved.getVersion()
        );
        return view(saved);
    }

    private KnowledgeArticle publishKnowledge(UserAccount actor, PolicyMonitorCandidate candidate) {
        String fileName = policyDocumentName(candidate);
        knowledgeIndexClient.uploadText(fileName, indexContent(candidate));

        KnowledgeArticle article = new KnowledgeArticle();
        article.setTenantId(candidate.getTenantId());
        article.setCategory("政策法规");
        article.setTitle(fileName);
        article.setContent(candidate.getContent());
        article.setSource(limit(candidate.getSourceName() + " | " + candidate.getSourceUrl(), 240));
        article.setRegion(candidate.getRegion() == null ? "全国" : candidate.getRegion());
        article.setPublishedAt(candidate.getPublishedAt());
        article.setUpdatedAt(candidate.getSourceUpdatedAt() == null
                ? LocalDate.now()
                : candidate.getSourceUpdatedAt().toLocalDate());
        article.setReviewStatus("APPROVED");
        KnowledgeArticle saved = knowledgeArticleRepository.save(article);
        auditService.log(actor, "PUBLISH_POLICY_TO_KNOWLEDGE", "knowledge_article", saved.getId(), fileName);
        return saved;
    }

    private String policyDocumentName(PolicyMonitorCandidate candidate) {
        String suffix = "-" + candidate.getVersion() + ".txt";
        String base = candidate.getTitle()
                .replaceAll("[\\\\/:*?<>|]", "_")
                .replace('"', '_')
                .trim();
        int maxBaseLength = Math.max(1, 160 - suffix.length());
        return limit(base, maxBaseLength) + suffix;
    }

    private String indexContent(PolicyMonitorCandidate candidate) {
        return "标题：" + candidate.getTitle() + "\n"
                + "版本：" + candidate.getVersion() + "\n"
                + "来源：" + candidate.getSourceName() + "\n"
                + "来源地址：" + candidate.getSourceUrl() + "\n"
                + "适用地区：" + valueOrEmpty(candidate.getRegion()) + "\n"
                + "发布日期：" + valueOrEmpty(candidate.getPublishedAt()) + "\n"
                + "生效日期：" + valueOrEmpty(candidate.getEffectiveAt()) + "\n"
                + "摘要：" + valueOrEmpty(candidate.getSummary()) + "\n"
                + "变更摘要：" + valueOrEmpty(candidate.getChangeSummary()) + "\n\n"
                + "正文：\n" + candidate.getContent();
    }

    private String valueOrEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private PolicyMonitorCandidate createCandidate(
            Long tenantId,
            String sourceId,
            String contentHash,
            DemoPolicyDtos.CandidateInput input
    ) {
        PolicyMonitorCandidate candidate = new PolicyMonitorCandidate();
        candidate.setTenantId(tenantId);
        candidate.setSourceId(sourceId);
        candidate.setSourceName(input.sourceName().trim());
        candidate.setSourceUrl(input.sourceUrl().trim());
        candidate.setTitle(input.title().trim());
        candidate.setVersion(input.version().trim());
        candidate.setRegion(trimToNull(input.region()));
        candidate.setPublishedAt(input.publishedAt());
        candidate.setEffectiveAt(input.effectiveAt());
        candidate.setSummary(trimToNull(input.summary()));
        candidate.setContent(input.content().trim());
        candidate.setChangeSummary(trimToNull(input.changeSummary()));
        candidate.setContentHash(contentHash);
        candidate.setSourceUpdatedAt(input.sourceUpdatedAt());
        candidate.setDetectedAt(LocalDateTime.now());
        candidate.setReviewStatus(PolicyReviewStatus.PENDING_REVIEW);
        return candidateRepository.save(candidate);
    }

    private void updateCheckpoint(
            PolicyMonitorCheckpoint checkpoint,
            String version,
            String contentHash
    ) {
        checkpoint.setLastVersion(version);
        checkpoint.setLastContentHash(contentHash);
        checkpoint.setLastCheckedAt(LocalDateTime.now());
        checkpointRepository.save(checkpoint);
    }

    private DemoPolicyDtos.MonitorCheckResult result(
            String monitorStatus,
            String previousVersion,
            PolicyMonitorCandidate candidate
    ) {
        return new DemoPolicyDtos.MonitorCheckResult(
                monitorStatus,
                previousVersion,
                candidate == null ? null : view(candidate)
        );
    }

    private DemoPolicyDtos.CandidateView view(PolicyMonitorCandidate candidate) {
        return new DemoPolicyDtos.CandidateView(
                candidate.getId(),
                candidate.getSourceId(),
                candidate.getSourceName(),
                candidate.getSourceUrl(),
                candidate.getTitle(),
                candidate.getVersion(),
                candidate.getRegion(),
                candidate.getPublishedAt(),
                candidate.getEffectiveAt(),
                candidate.getSummary(),
                candidate.getContent(),
                candidate.getChangeSummary(),
                candidate.getContentHash(),
                candidate.getSourceUpdatedAt(),
                candidate.getDetectedAt(),
                candidate.getReviewStatus(),
                candidate.getReviewedAt(),
                candidate.getReviewOpinion(),
                candidate.getKnowledgeArticleId()
        );
    }

    private int compareVersion(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            int leftValue = index < leftParts.length ? parseVersionPart(leftParts[index]) : 0;
            int rightValue = index < rightParts.length ? parseVersionPart(rightParts[index]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private int parseVersionPart(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void log(IntegrationApiKey key, int statusCode, String message) {
        ApiCallLog log = new ApiCallLog();
        log.setTenantId(key.getTenantId());
        log.setApiKeyId(key.getId());
        log.setMethod("POST");
        log.setPath(CHECK_PATH);
        log.setStatusCode(statusCode);
        log.setRequestId(RequestCorrelation.currentId());
        String detail = "[" + RequestCorrelation.currentId() + "] " + message;
        log.setMessage(detail.substring(0, Math.min(600, detail.length())));
        apiCallLogRepository.save(log);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
