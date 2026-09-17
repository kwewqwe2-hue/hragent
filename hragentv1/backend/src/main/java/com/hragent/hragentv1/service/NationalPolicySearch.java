package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.KnowledgeArticle;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Named national documents must not fall through to loose matches in company handbooks. */
final class NationalPolicySearch {
    static boolean curated(KnowledgeArticle a) {
        return a.getSource()!=null && a.getSource().startsWith("全国政策原文 · ");
    }
    private static String name(String text) {
        return text.replaceAll("[（(][^）)]*[）)]", "").replace("中华人民共和国", "")
            .replaceAll("\\s+|[《》]", "");
    }
    private static int score(KnowledgeArticle a,String q) {
        String title=name(a.getTitle());
        if(q.contains(title))return 100+title.length();
        if(q.contains("2026") && q.matches(".*(节假日|放假安排).*"))
            return title.contains("2026") && (title.contains("节假日安排") || title.contains("放假安排")) ? 90:0;
        if(q.contains("专项附加扣除"))return title.contains("提高个人所得税有关专项附加扣除标准")?85:0;
        return 0;
    }
    static Optional<PolicyAnswer> answer(List<KnowledgeArticle> all,Long tenant,String question,ProfileView profile,LocalDate today) {
        if(question==null || HandbookKnowledgeService.skipReferenceLookup(question))return Optional.empty();
        String q=name(question);
        var selected=all.stream().filter(NationalPolicySearch::curated)
            .filter(a->Objects.equals(tenant,a.getTenantId()))
            .filter(a->PolicyCopilotService.applicable(a,profile,today))
            .filter(a->score(a,q)>0)
            .sorted(Comparator.<KnowledgeArticle>comparingInt(a->score(a,q)).reversed()
                .thenComparing(KnowledgeArticle::getEffectiveFrom,Comparator.reverseOrder())).findFirst();
        if(selected.isEmpty())return Optional.empty();
        var a=selected.get();
        // An explicitly requested future revision is reference material, never today's execution rule.
        var future=all.stream().filter(NationalPolicySearch::curated)
            .filter(f->Objects.equals(tenant,f.getTenantId()) && "APPROVED".equalsIgnoreCase(f.getReviewStatus()))
            .filter(f->f.getEffectiveFrom()!=null && f.getEffectiveFrom().isAfter(today))
            .filter(f->name(f.getTitle()).equals(name(a.getTitle())))
            .filter(f->Pattern.compile("20\\d{2}").matcher(question).results().anyMatch(m->f.getTitle().contains(m.group())))
            .findFirst();
        if(future.isPresent()) {
            var f=future.get();
            return Optional.of(new PolicyAnswer("找到这版啦。《"+f.getTitle()+"》将于 "+f.getEffectiveFrom()+" 生效，目前仍应查看《"+a.getTitle()+"》。\n\n[查看待生效原文]("+f.getSourceUrl()+")\n你想先看现在的规定，还是了解新旧变化？","NOT_EFFECTIVE",profile,List.of(PolicyCopilotService.citation(f)),List.of()));
        }
        String remainder=q.replace(name(a.getTitle()),"").replaceAll("国家|全国|请问|我想了解|了解一下|规定|政策|查询","");
        var terms=Arrays.stream("试用期|解除|补偿|缴存|提取|贷款|转移|退休|申领|材料|期限|扣除|工伤|鉴定|春节|国庆|中秋|元旦|清明|劳动节|端午".split("\\|"))
            .filter(remainder::contains).toList();
        var excerpts=Arrays.stream(a.getContent().split("(?=第[一二三四五六七八九十百零]+条)|\\n"))
            .map(String::trim).filter(s->s.length()>12 && !s.startsWith("【"))
            .filter(s->terms.stream().anyMatch(s::contains)).limit(1).toList();
        String answer="找到啦，这里对应的是《"+a.getTitle()+"》。";
        if(!excerpts.isEmpty()) {
            String excerpt=excerpts.getFirst();
            if(excerpt.length()<=380)answer+="\n\n相关原文：\n"+excerpt;
            else answer+="\n\n这一条涉及多种情形。我们可以按你的情况一步一步看，完整条文放在下方原文里。";
        }
        answer+="\n\n这份是全国政策，当前采用的版本自 "+a.getEffectiveFrom()+" 生效。";
        if(a.getEffectiveTo()!=null)answer+="这一版适用至 "+a.getEffectiveTo()+"。";
        if(a.getContent().contains("【配套"))answer+="涉及金额和配套规则时，还需一起核对后续文件。";
        answer+="\n[查看官方原文]("+a.getSourceUrl()+")\n\n"+(q.contains("试用期")?"你签的是多长时间的劳动合同呀？":"你最想了解哪一项：适用条件、办理步骤，还是待遇标准？");
        return Optional.of(new PolicyAnswer(answer,"MATCHED",profile,List.of(PolicyCopilotService.citation(a)),List.of()));
    }
}
