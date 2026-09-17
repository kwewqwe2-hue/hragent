package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import com.hragent.hragentv1.repo.KnowledgeArticleRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import static com.hragent.hragentv1.service.EmployeeServiceSupport.*;

@Service
public class PolicyCopilotService {
    public static final String PUBLIC_LEAVE = "法定休假政策";
    private static final List<List<String>> TOPICS = List.of(
            List.of("考勤", "打卡", "迟到", "工时"), List.of("加班", "调休", "加班补偿"),
            List.of("年假", "带薪休假"), List.of("差旅", "出差"), List.of("报销", "福利"),
            List.of("社保", "养老保险", "社会保险", "缴费基数"), List.of("失业保险", "失业保险金"), List.of("就业援助"), List.of("企业年金", "年金"), List.of("公积金"), List.of("落户", "户口"),
            List.of("产假", "生育"), List.of("病假", "医疗期"), List.of("婚假"),
            List.of("合同", "续签"), List.of("转正", "试用期"), List.of("体检"), List.of("证明"),
            List.of("竞业", "保密协议"), List.of("工伤", "工作受伤"), List.of("调岗", "调薪", "降薪"),
            List.of("离职", "辞职", "交接"), List.of("赔偿", "补偿", "裁员"), List.of("工资", "薪资"),
            List.of("入职", "新人", "权限"), List.of("劳动法"), List.of("退休"));
    private final KnowledgeArticleRepository articles;
    private final EmployeeServiceProfileService profiles;
    public PolicyCopilotService(KnowledgeArticleRepository articles, EmployeeServiceProfileService profiles) {
        this.articles = articles; this.profiles = profiles;
    }
    @org.springframework.beans.factory.annotation.Autowired
    private com.hragent.hragentv1.repo.PolicyMonitorCandidateRepository officialCandidates;
    private Optional<PolicyAnswer> officialAnswer(UserAccount employee,String question,ProfileView profile,LocalDate today) {
        if(officialCandidates==null || blank(question))return Optional.empty();
        var topics=TOPICS.stream().filter(group->group.stream().anyMatch(question::contains)).toList();
        if(topics.isEmpty())return Optional.empty();
        var latest=new LinkedHashMap<String,PolicyMonitorCandidate>();
        String requestedCity=Arrays.stream("北京|上海|天津|重庆|江苏|浙江|广东|山东|四川|湖北|湖南|福建|河北|河南|安徽|陕西|辽宁".split("\\|")).filter(question::contains).findFirst().orElse(null);
        var lookupProfile=requestedCity==null?profile:new ProfileView(profile.employeeId(),profile.employeeNo(),profile.name(),requestedCity,profile.jobGrade(),profile.workType(),profile.legalEntity(),profile.probationEndDate(),profile.contractEndDate(),profile.medicalCheckDeadline(),profile.annualLeaveExpiresAt(),profile.medicalBookingUrl(),profile.missingAttributes());
        officialCandidates.findByTenantIdOrderByDetectedAtDesc(employee.getTenantId()).forEach(c->latest.putIfAbsent(c.getSourceId(),c));
        var candidates=latest.values().stream()
            .filter(c->c.getSourceId().startsWith("official-"))
            .filter(c->scopeMatches(c.getRegion(),lookupProfile.location(),true))
            .filter(c->Arrays.stream("北京|上海|天津|重庆|江苏|浙江|广东|山东|四川|湖北|湖南|福建|河北|河南|安徽|陕西|辽宁".split("\\|")).filter(question::contains).allMatch(r->scopeMatches(c.getRegion(),r,true)))
            .filter(c->topics.stream().anyMatch(g->g.stream().anyMatch(c.getTitle()::contains)))
            .filter(c->focusedOfficialTopic(question,c.getTitle()))
            .filter(c->c.getEffectiveAt()==null||!c.getEffectiveAt().isAfter(today)).toList();
        var ids=candidates.stream().filter(c->c.getReviewStatus()==PolicyReviewStatus.APPROVED).map(PolicyMonitorCandidate::getKnowledgeArticleId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        var verified=articles.findByTenantIdOrderByUpdatedAtDesc(employee.getTenantId()).stream()
            .filter(a->ids.contains(a.getId())&&applicable(a,lookupProfile,today)&&requestedRegionMatches(a,question))
            .sorted(Comparator.comparing(KnowledgeArticle::getEffectiveFrom).reversed()).limit(2).toList();
        var pending=candidates.stream().filter(c->c.getReviewStatus()==PolicyReviewStatus.PENDING_REVIEW)
            .filter(c->verified.isEmpty()||verified.stream().anyMatch(a->(Objects.equals(a.getSourceUrl(),c.getSourceUrl())||officialFamily(a.getTitle()).equals(officialFamily(c.getTitle())))
                && (c.getPublishedAt()==null||a.getPublishedAt()==null||!c.getPublishedAt().isBefore(a.getPublishedAt())))).findFirst();
        if(pending.isPresent()) {
            var c=pending.get();String notice="发现了与你的问题相关的官方文件《"+c.getTitle()+"》，还需要 HR 核验适用范围和生效日期，暂不能把它当作已确认的执行标准。\n[查看官方原文]("+c.getSourceUrl()+")\n具体适用情况可以咨询 HR。";
            return Optional.of(new PolicyAnswer(notice,"NEEDS_REVIEW",profile,List.of(),List.of("官方文件待核验")));
        }
        if(verified.isEmpty())return Optional.empty();
        String answer=String.join("\n\n",verified.stream().map(a->{
            var paragraphs=Arrays.stream(a.getContent().split("\\n+"))
                .filter(line->topics.stream().anyMatch(g->g.stream().anyMatch(line::contains))).limit(3).toList();
            String excerpt=String.join("\n",paragraphs);if(excerpt.length()>1000)excerpt=excerpt.substring(0,1000)+"…（完整条文见原文）";
            return "《"+a.getTitle().replaceFirst("-[a-f0-9]{12}\\.txt$","")+"》相关条款：\n"+excerpt+"\n适用地区："+a.getRegion()+"；生效日期："+a.getEffectiveFrom()+"\n[官方原文]("+a.getSourceUrl()+")";
        }).toList())+"\n具体适用情况和办理材料，可以咨询 HR。";
        return Optional.of(new PolicyAnswer(answer,"MATCHED",profile,verified.stream().map(PolicyCopilotService::citation).toList(),List.of()));
    }
    private static String officialFamily(String title){return title.replaceFirst("-[a-f0-9]{12}\\.txt$","").replaceAll("20\\d{2}年(?:度)?","").replaceAll("\\s+","");}
    private static boolean focusedOfficialTopic(String question,String title){
        String q=question.replace("社保基数","缴费基数").replace("缴费工资基数","缴费基数"), t=title.replace("缴费工资基数","缴费基数");
        return Arrays.stream("工资支付|工资保证金|缴费基数|城乡居民|工伤康复|浮动费率|失业保险金|基本养老金|就业援助|转移接续".split("\\|")).filter(q::contains).allMatch(t::contains);
    }
    public PolicyAnswer answer(UserAccount employee, String question) {
        return answer(employee, question, profiles.profile(employee));
    }
    public PolicyAnswer answer(UserAccount employee, String question, ProfileView profile) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var benefit = CompanyBenefitReply.answer(employee,question,profile,articles.findByTenantIdOrderByUpdatedAtDesc(employee.getTenantId()),today);
        if(benefit.isPresent())return benefit.get();
        var national = NationalPolicySearch.answer(articles.findByTenantIdOrderByUpdatedAtDesc(employee.getTenantId()),employee.getTenantId(),question,profile,today);
        if(national.isPresent())return national.get();
        var official = officialAnswer(employee,question,profile,today);
        if(official.isPresent())return official.get();
        var supplied = SuppliedPolicySearch.answer(articles.findByTenantIdOrderByUpdatedAtDesc(employee.getTenantId()), employee.getTenantId(), question, profile, today);
        if (supplied.isPresent()) return supplied.get();
        List<KnowledgeArticle> relevant = articles.findByTenantIdOrderByUpdatedAtDesc(employee.getTenantId()).stream()
                .filter(a -> !SuppliedPolicySearch.CATEGORY.equals(a.getCategory()))
                .filter(a -> employee.getTenantId().equals(a.getTenantId()))
                .filter(a -> "APPROVED".equalsIgnoreCase(a.getReviewStatus()))
                .filter(a -> !PUBLIC_LEAVE.equals(a.getCategory()) || matchesLeaveIntent(a, question))
                .filter(a -> relevant(a, question)).toList();
        var matches = relevant.stream().filter(a -> applicable(a, profile, today))
                .filter(a -> !PUBLIC_LEAVE.equals(a.getCategory()) || requestedRegionMatches(a, question))
                .sorted(Comparator.<KnowledgeArticle>comparingInt(PolicyCopilotService::specificity).reversed()
                        .thenComparing(KnowledgeArticle::getEffectiveFrom, Comparator.reverseOrder()))
                .limit(5).map(PolicyCopilotService::citation).toList();
        var gaps = new ArrayList<String>();
        boolean conflicting = matches.stream().anyMatch(a -> matches.stream().anyMatch(b ->
                !a.id().equals(b.id()) && a.title().equals(b.title()) && !a.excerpt().equals(b.excerpt())));
        if (conflicting) gaps.add("同名制度存在多个不同的有效版本，暂不判断应执行哪一版，请 HR 核对版本和失效日期。");
        if (!profile.missingAttributes().isEmpty()) gaps.add("档案待 HR 补充：" + String.join("、", profile.missingAttributes()));
        if (matches.isEmpty()) gaps.add("未找到同时满足适用范围、审核状态、原文出处和生效日期的制度，请 HR 在知识库补充或核实。");
        String context = blank(profile.location()) ? "按与你档案匹配的公司制度：" : "按你在" + profile.location() + "适用的公司制度：";
        String answer = matches.isEmpty()
                ? "暂时没有可确认适用于你的制度依据。" + (profile.missingAttributes().isEmpty() ? "需要 HR 核对公司现行规定。" : "请先让 HR 补充" + String.join("、", profile.missingAttributes()) + "。")
                : context + "\n\n" + String.join("\n\n", matches.stream().map(c -> "**《" + c.title()
                        + "》**\n" + c.excerpt() + "\n\n出处：" + c.source() + "；生效日期：" + c.effectiveFrom()
                        + (c.effectiveTo() == null ? "" : "；有效至：" + c.effectiveTo())
                        + (blank(c.sourceUrl()) ? "；原文见服务中心制度详情" : "\n[查看原文](" + c.sourceUrl() + ")")).toList());
        if (!matches.isEmpty() && !gaps.isEmpty()) answer += "\n\n" + String.join("\n", gaps) + "。当前只展示已确认范围的制度。";
        if (conflicting) answer = "需要先核实制度版本，暂不能给出确定执行标准。\n\n" + answer;
        if (!conflicting && !matches.isEmpty() && matches.stream().allMatch(c -> relevant.stream()
                .anyMatch(a -> PUBLIC_LEAVE.equals(a.getCategory()) && Objects.equals(a.getId(), c.id())))) {
            answer = String.join("\n\n", matches.stream().map(Citation::excerpt).toList());
            if (matches.stream().allMatch(c -> scopeMatches(c.region(), null, true)))
                answer += "\n\n地方可能另有增加假期，具体要结合工作地确认。";
            answer += "\n\n" + String.join(" · ", matches.stream().map(c -> "[" + c.source() + "](" + c.sourceUrl() + ")").toList());
            answer += "\n具体可休天数、申请材料和办理流程，可以咨询 HR。";
        }
        return new PolicyAnswer(answer, conflicting ? "NEEDS_REVIEW" : matches.isEmpty() ? "NEEDS_HR" : "MATCHED", profile, matches, gaps);
    }
    public Optional<PolicyAnswer> suppliedAnswer(UserAccount employee, String question) {
        var benefit=benefitAnswer(employee,question);
        if(benefit.isPresent())return benefit;
        if (HandbookKnowledgeService.skipReferenceLookup(question)) return Optional.empty();
        var national=NationalPolicySearch.answer(articles.findByTenantIdOrderByUpdatedAtDesc(employee.getTenantId()),employee.getTenantId(),question,profiles.profile(employee),LocalDate.now(ZoneId.of("Asia/Shanghai")));
        if(national.isPresent())return national;
        var official=officialAnswer(employee,question,profiles.profile(employee),LocalDate.now(ZoneId.of("Asia/Shanghai")));
        if(official.isPresent())return official;
        return SuppliedPolicySearch.answer(articles.findByTenantIdOrderByUpdatedAtDesc(employee.getTenantId()), employee.getTenantId(), question,
                profiles.profile(employee), LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }
    public Optional<PolicyAnswer> benefitAnswer(UserAccount employee,String question) {
        if(question==null || !question.contains("年金"))return Optional.empty();
        return CompanyBenefitReply.answer(employee,question,profiles.profile(employee),articles.findByTenantIdOrderByUpdatedAtDesc(employee.getTenantId()),LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }
    public List<Map<String, Object>> suppliedDocuments(UserAccount employee) {
        var groups = new LinkedHashMap<String, List<KnowledgeArticle>>();
        articles.findByTenantIdOrderByUpdatedAtDesc(employee.getTenantId()).stream()
                .filter(a -> SuppliedPolicySearch.eligible(a, employee.getTenantId(), LocalDate.now(ZoneId.of("Asia/Shanghai")))).forEach(a ->
                        groups.computeIfAbsent(a.getSource().split(" · ")[0], key -> new ArrayList<>()).add(a));
        return groups.entrySet().stream().map(e -> {
            Map<String,Object> result = new LinkedHashMap<>(); var first=e.getValue().getFirst();
            result.put("name",e.getKey()); result.put("sections",e.getValue().size()); result.put("effectiveFrom", first.getEffectiveFrom());
            result.put("scope",SuppliedPolicySearch.field(first.getContent(),"适用范围"));return result;
        }).toList();
    }
    private static boolean matchesLeaveIntent(KnowledgeArticle article, String question) {
        if (question == null || !(question.contains("产假") || question.contains("生育假") || question.contains("生育津贴") || question.contains("流产"))) return false;
        if (question.contains("流产")) return article.getTitle().contains("流产");
        if (article.getTitle().contains("流产")) return false;
        boolean benefits = question.matches(".*(工资|津贴|待遇|发钱|发放|报销|缴费).*");
        return benefits == article.getTitle().contains("待遇");
    }
    private static boolean requestedRegionMatches(KnowledgeArticle article, String question) {
        // Never apply a profile's regional extension when the question explicitly names another region.
        return Arrays.stream("北京|天津|河北|山西|内蒙古|辽宁|吉林|黑龙江|上海|江苏|浙江|安徽|福建|江西|山东|河南|湖北|湖南|广东|广西|海南|重庆|四川|贵州|云南|西藏|陕西|甘肃|青海|宁夏|新疆|深圳|广州|杭州|南京|苏州|成都|武汉".split("\\|"))
                .filter(question::contains).allMatch(region -> scopeMatches(article.getRegion(), region, true));
    }
    public static boolean applicable(KnowledgeArticle article, ProfileView profile, LocalDate today) {
        return "APPROVED".equalsIgnoreCase(article.getReviewStatus())
                && !blank(article.getSource()) && !blank(article.getContent())
                && article.getEffectiveFrom() != null && !article.getEffectiveFrom().isAfter(today)
                && (article.getEffectiveTo() == null || !article.getEffectiveTo().isBefore(today))
                && scopeMatches(article.getRegion(), profile.location(), true)
                && scopeMatches(article.getJobGrades(), profile.jobGrade(), false)
                && scopeMatches(article.getWorkTypes(), profile.workType(), false)
                && scopeMatches(article.getLegalEntities(), profile.legalEntity(), false);
    }
    public static boolean scopeMatches(String scope, String attribute, boolean region) {
        if (blank(scope) || Set.of("全国", "全部", "不限", "全员", "*", "ALL").contains(scope.trim())) return true;
        if (blank(attribute)) return false;
        return Arrays.stream(scope.split("[,，;；\\n]"))
                .anyMatch(part -> normalize(part, region).equalsIgnoreCase(normalize(attribute, region))
                        || (region && (attribute.trim().startsWith(normalize(part, true) + "市")
                        || attribute.trim().startsWith(normalize(part, true) + "省"))));
    }
    private static String normalize(String value, boolean region) {
        String normalized = value.trim();
        return region ? normalized.replaceAll("(市|省)$", "") : normalized;
    }
    public static boolean relevant(KnowledgeArticle article, String question) {
        if (blank(question)) return false;
        String content = article.getTitle() + " " + article.getCategory() + " " + article.getContent();
        var queryTopics = TOPICS.stream().filter(group -> group.stream().anyMatch(question::contains)).toList();
        if (!queryTopics.isEmpty()) return queryTopics.stream().anyMatch(group -> group.stream().anyMatch(content::contains));
        return question.length() >= 2 && content.contains(question.trim());
    }
    private static int specificity(KnowledgeArticle a) {
        return (blank(a.getRegion()) || scopeMatches(a.getRegion(), null, true) ? 0 : 1)
                + (blank(a.getJobGrades()) ? 0 : 1) + (blank(a.getWorkTypes()) ? 0 : 1) + (blank(a.getLegalEntities()) ? 0 : 1);
    }
    public static Citation citation(KnowledgeArticle a) {
        String content = a.getContent();
        if (content.length() > 1600) content = content.substring(0, 1600) + "…（完整条文见原文）";
        return new Citation(a.getId(), a.getTitle(), content, a.getSource(), a.getSourceUrl(), a.getPublishedAt(),
                a.getEffectiveFrom(), a.getEffectiveTo(), a.getRegion(), a.getJobGrades(), a.getWorkTypes(), a.getLegalEntities());
    }
}
