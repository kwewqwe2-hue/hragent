package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import com.hragent.hragentv1.repo.KnowledgeArticleRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NationalPolicySearchTest {
    final LocalDate today=LocalDate.of(2026,9,11);
    final ProfileView profile=new ProfileView(1L,"E001","员工","北京",null,null,null,null,null,null,null,null,List.of());
    KnowledgeArticle article(String title,LocalDate from) {
        var a=new KnowledgeArticle();a.setId(1L);a.setTenantId(1L);a.setTitle(title);
        a.setSource("全国政策原文 · www.gov.cn");a.setSourceUrl("https://www.gov.cn/policy");
        a.setRegion("全国");a.setReviewStatus("APPROVED");a.setEffectiveFrom(from);
        a.setContent("第一条 为了规范劳动关系。\n第十九条 劳动合同期限三个月以上不满一年的，试用期不得超过一个月。\n第二十条 其他规定。");return a;
    }
    @Test void namedNationalLawWinsOverLooseCompanyContentInBothEntryPoints() {
        var repo=mock(KnowledgeArticleRepository.class);var profiles=mock(EmployeeServiceProfileService.class);
        var user=new UserAccount();user.setTenantId(1L);
        var a=article("中华人民共和国劳动合同法（2012年修正）",LocalDate.of(2013,7,1));
        when(repo.findByTenantIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(a));when(profiles.profile(user)).thenReturn(profile);
        var service=new PolicyCopilotService(repo,profiles);
        for(var result:List.of(service.answer(user,"国家劳动合同法试用期规定"),service.suppliedAnswer(user,"国家劳动合同法试用期规定").orElseThrow())) {
            assertThat(result.citations()).extracting(Citation::title).containsExactly(a.getTitle());
            assertThat(result.answer()).contains("第十九条","全国政策","多长时间").doesNotContain("适用的公司制度");
            assertThat(result.answer().length()).isLessThan(650);
        }
    }
    @Test void futureHousingRevisionNeverReplacesCurrentBeforeEffectiveDay() {
        var old=article("住房公积金管理条例（2019年修订）",LocalDate.of(2019,3,24));old.setEffectiveTo(LocalDate.of(2026,9,19));
        var next=article("住房公积金管理条例（2026年修订）",LocalDate.of(2026,9,20));next.setId(2L);
        var all=List.of(next,old);
        assertThat(NationalPolicySearch.answer(all,1L,"住房公积金管理条例",profile,today).orElseThrow().citations().getFirst().id()).isEqualTo(1L);
        assertThat(NationalPolicySearch.answer(all,1L,"2026年住房公积金管理条例",profile,today).orElseThrow().status()).isEqualTo("NOT_EFFECTIVE");
        assertThat(NationalPolicySearch.answer(all,1L,"住房公积金管理条例",profile,today.plusDays(9)).orElseThrow().citations().getFirst().id()).isEqualTo(2L);
    }
    @Test void excludesForeignDraftAndBusinessRequestsAndUnrelatedLocalQueries() {
        var a=article("中华人民共和国劳动合同法",today.minusDays(2));
        assertThat(NationalPolicySearch.answer(List.of(a),2L,"劳动合同法",profile,today)).isEmpty();
        assertThat(NationalPolicySearch.answer(List.of(a),1L,"我要申请年假",profile,today)).isEmpty();
        assertThat(NationalPolicySearch.answer(List.of(a),1L,"上海工资支付办法",profile,today)).isEmpty();
        a.setReviewStatus("DRAFT");assertThat(NationalPolicySearch.answer(List.of(a),1L,"劳动合同法",profile,today)).isEmpty();
    }
    @Test void colloquialHolidayAndTaxQueriesFindTheSpecificOfficialNotice() {
        var holiday=article("国务院办公厅关于2026年部分节假日安排的通知",LocalDate.of(2026,1,1));
        var tax=article("国务院关于提高个人所得税有关专项附加扣除标准的通知",LocalDate.of(2023,1,1));
        for(String q:List.of("2026年节假日安排","2026年放假安排"))
            assertThat(NationalPolicySearch.answer(List.of(holiday,tax),1L,q,profile,today).orElseThrow().citations().getFirst().title()).isEqualTo(holiday.getTitle());
        assertThat(NationalPolicySearch.answer(List.of(holiday,tax),1L,"个人所得税专项附加扣除",profile,today).orElseThrow().citations().getFirst().title()).isEqualTo(tax.getTitle());
    }
}
