package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.KnowledgeArticle;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.repo.KnowledgeArticleRepository;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

/** Local-only retrieval of supplied reference documents; no external model or network calls. */
@Service
public class HandbookKnowledgeService {
    public static final String CATEGORY = "员工手册参考";
    private static final List<List<String>> SYNONYMS = List.of(
            List.of("年假", "年休假", "带薪休假"), List.of("试用期", "转正"),
            List.of("产假", "生育假", "生育"), List.of("病假", "医疗期"),
            List.of("工资", "薪资", "薪酬"), List.of("离职", "解除劳动合同", "终止劳动合同"),
            List.of("赔偿", "经济补偿", "辞退", "裁员"), List.of("社保", "社会保险"),
            List.of("落户", "户口", "居转户", "常住户口"), List.of("加班费", "加班工资"),
            List.of("双倍工资", "两倍工资"), List.of("签合同", "订立劳动合同"));
    private static final List<String> TOPICS = List.of("年休假", "试用期", "产假", "病假", "医疗期", "婚假", "丧假",
            "探亲假", "加班", "工资", "劳动合同", "经济补偿", "社会保险", "养老", "失业", "工伤", "公积金",
            "落户", "居住证", "退休", "仲裁", "解除", "辞退", "裁员", "保密", "竞业", "个税", "所得税", "工作时间");
    private final KnowledgeArticleRepository repository;
    public HandbookKnowledgeService(KnowledgeArticleRepository repository) { this.repository = repository; }
    public Optional<String> answer(UserAccount employee, String message) {
        if (skipReferenceLookup(message)) return Optional.empty();
        var matches = search(employee.getTenantId(), message);
        if (matches.isEmpty()) return Optional.empty();
        String summary = summarize(matches, message);
        if (summary.isBlank()) return Optional.empty();
        String followUp = message.matches(".*(年假|年休假).*") && !message.matches(".*(补偿|折算|未休).*")
                ? "要确认你的年假额度，还需要累计工龄；剩余天数可以直接查你的余额。"
                : message.contains("试用期") ? "要判断你的情况，需要知道劳动合同签了多久。" : "具体能否适用，还要核对你的公司制度。";
        String answer = "参考规则（当前适用版本待核实）：\n" + summary + "\n\n" + followUp;
        if (message.matches(".*(来源|出处|原文|第几页|依据).*") )
            answer += "\n\n参考出处：" + String.join("；", matches.stream().map(KnowledgeArticle::getSource).toList());
        return Optional.of(answer);
    }
    /** Extract complete relevant sentences locally; never send reference documents to a remote model. */
    private String summarize(List<KnowledgeArticle> matches, String question) {
        var terms = grams(normalize(question));
        record Sentence(String text, int score) { }
        var sentences = new ArrayList<Sentence>();
        for (var article : matches) {
            String body = article.getContent().replaceAll("(?m)^(条文标题|文号|手册记载执行时间)：[^\\n]*\\n?", "");
            for (String part : body.split("[。\\n]")) {
                String sentence = part.strip().replaceFirst("^[●•\\s]+", "");
                if (sentence.length() < 10 || sentence.length() > 420 || sentence.endsWith("：")) continue;
                String normalized = normalize(sentence);
                int score = (int) terms.stream().filter(normalized::contains).count();
                if (question.matches(".*(多久|几天|多少天|期限).*") && sentence.matches(".*[0-9一二三四五六七八九十]+[天月年].*")) score += 8;
                if (score > 1) sentences.add(new Sentence(sentence, score));
            }
        }
        sentences.sort(Comparator.comparingInt(Sentence::score).reversed());
        return String.join("\n", sentences.stream().map(Sentence::text).distinct().limit(2)
                .map(s -> "- " + s.replace("职工累计工作", "累计工龄").replace("年休假", "年假") + "。").toList());
    }
    public List<KnowledgeArticle> search(Long tenantId, String question) {
        if (question == null || question.isBlank()) return List.of();
        String normalized = normalize(question);
        Set<String> queryTerms = grams(normalized);
        if (queryTerms.isEmpty()) return List.of();
        var documents = repository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .filter(a -> tenantId.equals(a.getTenantId()) && CATEGORY.equals(a.getCategory()))
                .filter(a -> "APPROVED".equals(a.getReviewStatus()) && a.getContent() != null).toList();
        Map<String, Integer> frequencies = new HashMap<>();
        for (var article : documents) {
            var terms = grams(normalize(article.getTitle() + article.getContent()));
            for (String term : queryTerms) if (terms.contains(term)) frequencies.merge(term, 1, Integer::sum);
        }
        var scored = new ArrayList<Scored>();
        for (var article : documents) {
            String content = normalize(article.getTitle() + " " + article.getContent());
            String title = normalize(article.getTitle());
            int hits = 0;
            double score = 0;
            for (String term : queryTerms) {
                if (!content.contains(term)) continue;
                hits++;
                double rarity = Math.log(1.0 + (documents.size() + 1.0) / (frequencies.getOrDefault(term, 0) + 1.0));
                score += rarity * (title.contains(term) ? 3.0 : 1.0);
            }
            boolean topicHit = false;
            for (String topic : TOPICS) if (normalized.contains(topic) && content.contains(topic)) {
                topicHit = true; score += title.contains(topic) ? 12 : 5;
            }
            if (hits >= 2 || topicHit) scored.add(new Scored(article, score / Math.pow(Math.max(1, content.length() / 700.0), 0.25)));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        if (scored.isEmpty()) return List.of();
        double threshold = Math.max(5.0, scored.getFirst().score() * 0.65);
        return scored.stream().filter(s -> s.score() >= threshold).limit(2).map(Scored::article).toList();
    }
    public static boolean skipReferenceLookup(String message) {
        if (message == null || message.isBlank() || message.startsWith("【系统已完成网页附件解析")) return true;
        if (Pattern.compile("余额|还剩|剩余|我还有|我的年假|我的.*(进度|提醒|待办)|审批状态|下载证明").matcher(message).find()) return true;
        return Pattern.compile("^(我要|帮我|请帮我).*(提交|申请|撤回|取消)").matcher(message).find()
                && !Pattern.compile("怎么|如何|什么|规定|制度|政策|条件|材料|吗|？|\\?").matcher(message).find();
    }
    private static String normalize(String value) {
        String text = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        for (var group : SYNONYMS) {
            boolean found = false;
            for (String term : group) if (text.contains(term)) { found = true; break; }
            if (found) text += String.join("", group);
        }
        return text.replaceAll("请问|请告诉我|帮我查一下|帮我查询|根据手册|手册中|手册里|手册|什么|怎么|如何|多少|可以|是否|需要|规定|一下|请帮我|请查询", "");
    }
    private static Set<String> grams(String text) {
        Set<String> terms = new HashSet<>();
        var matcher = Pattern.compile("[\\p{IsHan}a-z0-9]+").matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            for (int i = 0; i < word.length() - 1; i++) terms.add(word.substring(i, i + 2));
        }
        return terms;
    }
    private record Scored(KnowledgeArticle article, double score) { }
}
