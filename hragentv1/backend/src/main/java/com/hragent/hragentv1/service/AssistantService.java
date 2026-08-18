package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.AiCallRecord;
import com.hragent.hragentv1.domain.KnowledgeArticle;
import com.hragent.hragentv1.domain.LeaveBalance;
import com.hragent.hragentv1.domain.LeaveType;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.AssistantDtos;
import com.hragent.hragentv1.repo.AiCallRecordRepository;
import com.hragent.hragentv1.repo.KnowledgeArticleRepository;
import com.hragent.hragentv1.repo.LeaveBalanceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class AssistantService {
    private final DeepSeekClient deepSeekClient;
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final AiCallRecordRepository aiCallRecordRepository;

    public AssistantService(
            DeepSeekClient deepSeekClient,
            KnowledgeArticleRepository knowledgeArticleRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            AiCallRecordRepository aiCallRecordRepository
    ) {
        this.deepSeekClient = deepSeekClient;
        this.knowledgeArticleRepository = knowledgeArticleRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.aiCallRecordRepository = aiCallRecordRepository;
    }

    public AssistantDtos.ChatResponse chat(UserAccount user, String message) {
        List<KnowledgeArticle> articles = searchKnowledge(user.getTenantId(), message);
        String prompt = buildConsultPrompt(user, message, articles);
        return callAndRecord(user, "LEAVE_CONSULT", prompt, articles);
    }

    public LeaveAiResult reviewLeaveDraft(
            UserAccount user,
            LeaveType leaveType,
            BigDecimal days,
            String reason
    ) {
        List<KnowledgeArticle> articles = searchKnowledge(user.getTenantId(), leaveType.getLabel() + " " + reason);
        List<LeaveBalance> balances = leaveBalanceRepository.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(
                user.getTenantId(),
                user.getId()
        );
        String prompt = """
                请根据员工信息、假期余额、知识库规则，对这份请假申请做辅助判断。
                你不能替代主管或 HR 的最终决策，只输出风险等级、判断摘要和依据。

                员工：%s，部门：%s，岗位：%s
                请假类型：%s
                申请天数：%s
                请假原因：%s

                假期余额：
                %s

                知识库摘录：
                %s

                请用以下格式输出：
                风险等级：低/中/高
                判断摘要：...
                依据：...
                """.formatted(
                user.getName(),
                user.getDepartment(),
                user.getTitle(),
                leaveType.getLabel(),
                days,
                reason,
                formatBalances(balances),
                formatArticles(articles)
        );

        AssistantDtos.ChatResponse response = callAndRecord(user, "LEAVE_DRAFT_REVIEW", prompt, articles);
        String text = response.answer();
        return new LeaveAiResult(extractRisk(text), text, String.join("；", response.evidenceTitles()));
    }

    public List<AiCallRecord> latestCalls(Long tenantId) {
        return aiCallRecordRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    private AssistantDtos.ChatResponse callAndRecord(
            UserAccount user,
            String scenario,
            String prompt,
            List<KnowledgeArticle> articles
    ) {
        AiCallRecord record = new AiCallRecord();
        record.setTenantId(user.getTenantId());
        record.setUserId(user.getId());
        record.setScenario(scenario);
        record.setProvider(deepSeekClient.providerName(user.getTenantId()));
        record.setPromptText(prompt);

        try {
            String answer = deepSeekClient.chat(user.getTenantId(), systemPrompt(), prompt);
            record.setResponseText(answer);
            record.setSuccess(true);
            aiCallRecordRepository.save(record);
            return new AssistantDtos.ChatResponse(answer, articles.stream().map(KnowledgeArticle::getTitle).toList(), record.getProvider());
        } catch (Exception exception) {
            String fallback = "智能体调用失败，已降级为人工审核提示：" + exception.getMessage();
            record.setResponseText(fallback);
            record.setSuccess(false);
            record.setErrorMessage(exception.getMessage());
            aiCallRecordRepository.save(record);
            return new AssistantDtos.ChatResponse(fallback, articles.stream().map(KnowledgeArticle::getTitle).toList(), record.getProvider());
        }
    }

    private String systemPrompt() {
        return """
                你是企业 HR 请假制度助手。你的职责是解释制度、追问缺失信息、提示材料要求、辅助判断请假申请风险。
                你必须说明最终审批以直属主管和 HR 为准，不得替代人事最终决策。
                涉及政策法规时，应提醒用户确认适用地区、发布时间和审核状态。
                """;
    }

    private String buildConsultPrompt(UserAccount user, String message, List<KnowledgeArticle> articles) {
        List<LeaveBalance> balances = leaveBalanceRepository.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(
                user.getTenantId(),
                user.getId()
        );
        return """
                员工信息：
                姓名：%s
                部门：%s
                岗位：%s

                假期余额：
                %s

                知识库摘录：
                %s

                用户问题：
                %s
                """.formatted(
                user.getName(),
                user.getDepartment(),
                user.getTitle(),
                formatBalances(balances),
                formatArticles(articles),
                message
        );
    }

    private List<KnowledgeArticle> searchKnowledge(Long tenantId, String keyword) {
        List<KnowledgeArticle> found = knowledgeArticleRepository.search(tenantId, keyword == null ? "" : keyword);
        if (found.isEmpty()) {
            found = knowledgeArticleRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
        }
        return found.stream().limit(5).toList();
    }

    private String formatBalances(List<LeaveBalance> balances) {
        List<String> rows = new ArrayList<>();
        for (LeaveBalance balance : balances) {
            rows.add("%s：总额 %s 天，已用 %s 天，剩余 %s 天".formatted(
                    balance.getLeaveType().getLabel(),
                    balance.getTotalDays(),
                    balance.getUsedDays(),
                    balance.remainingDays()
            ));
        }
        return String.join("\n", rows);
    }

    private String formatArticles(List<KnowledgeArticle> articles) {
        List<String> rows = new ArrayList<>();
        for (KnowledgeArticle article : articles) {
            String content = article.getContent();
            if (content.length() > 500) {
                content = content.substring(0, 500) + "...";
            }
            rows.add("《%s》[%s/%s/%s]：%s".formatted(
                    article.getTitle(),
                    article.getCategory(),
                    article.getRegion(),
                    article.getReviewStatus(),
                    content
            ));
        }
        return String.join("\n\n", rows);
    }

    private String extractRisk(String text) {
        if (text == null) {
            return "未知";
        }
        if (text.contains("风险等级：高") || text.contains("高风险")) {
            return "高";
        }
        if (text.contains("风险等级：中") || text.contains("中风险")) {
            return "中";
        }
        if (text.contains("风险等级：低") || text.contains("低风险")) {
            return "低";
        }
        return "待人工确认";
    }

    public record LeaveAiResult(String riskLevel, String summary, String evidence) {
    }
}
