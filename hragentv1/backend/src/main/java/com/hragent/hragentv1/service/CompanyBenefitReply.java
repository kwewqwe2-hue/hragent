package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import java.time.LocalDate;
import java.util.*;

/** Company benefit statements require an explicit, applicable HR confirmation, never absence of search results. */
final class CompanyBenefitReply {
    static Optional<PolicyAnswer> answer(UserAccount user,String question,ProfileView profile,List<KnowledgeArticle> articles,LocalDate today) {
        if(question==null || !question.contains("年金") || question.contains("职业年金")) return Optional.empty();
        if(question.matches(".*(仲裁|举报|投诉|维权|违法|克扣|欠缴|漏缴|补缴|自残|自杀|伤害自己).*")) return Optional.empty();
        if(question.matches(".*(国家|法律|政策|条例|原文|最新).*"))return Optional.empty();
        boolean general=question.matches(".*(是什么|什么意思).*") && !question.matches(".*(我们公司|本公司|咱们公司|公司有没有|我能|我可以).*");
        if(general) return Optional.of(new PolicyAnswer("企业年金可以理解为基本养老保险之外的一份补充养老安排，由单位和员工自主建立。具体公司是否设立、你能否参加，要看企业公布的方案哦。\n\n[了解人社部的说明](https://www.gov.cn/xinwen/2018-01/23/content_5259736.htm)","MATCHED",profile,List.of(),List.of()));
        var confirmed=articles.stream().filter(a->Objects.equals(a.getTenantId(),user.getTenantId()))
            .filter(a->"企业福利确认".equals(a.getCategory()) && a.getTitle()!=null && a.getTitle().contains("企业年金"))
            .filter(a->PolicyCopilotService.applicable(a,profile,today)).toList();
        Set<String> states=new HashSet<>();
        for(var article:confirmed) for(String line:article.getContent().split("\\R")) {
            if(line.trim().matches("设立状态[：:]\\s*未设立"))states.add("NO");
            if(line.trim().matches("设立状态[：:]\\s*已设立"))states.add("YES");
        }
        String answer;
        if(states.size()==1 && states.contains("NO")) answer="你关心的是退休后的保障，对吧。按公司目前已确认的福利安排，暂未设立企业年金哦。你也可以先了解现有的养老保险和其他福利，我陪你一起看。";
        else if(states.size()==1) answer="公司已确认设立企业年金啦。具体参加条件和缴费安排需要对照适用于你的企业方案。你想先了解参加条件，还是查询办理方式？";
        else answer="你关心这份保障，我理解呀。目前我还没有查到公司已确认的企业年金安排，不能直接说有或没有。可以请 HR 帮你确认；如果你愿意，我们也可以先了解现有的养老保险和福利。";
        var citations=states.size()==1 ? confirmed.stream().map(a->new Citation(a.getId(),a.getTitle(),a.getContent(),a.getSource(),a.getSourceUrl(),a.getPublishedAt(),a.getEffectiveFrom(),a.getEffectiveTo(),a.getRegion(),a.getJobGrades(),a.getWorkTypes(),a.getLegalEntities())).toList() : List.<Citation>of();
        if(!citations.isEmpty()) answer+="\n\n依据："+citations.getFirst().title()+"（生效："+citations.getFirst().effectiveFrom()+"）。";
        return Optional.of(new PolicyAnswer(answer,states.size()==1?"MATCHED":"NEEDS_HR",profile,citations,states.size()==1?List.of():List.of("企业年金安排待 HR 确认")));
    }
}
