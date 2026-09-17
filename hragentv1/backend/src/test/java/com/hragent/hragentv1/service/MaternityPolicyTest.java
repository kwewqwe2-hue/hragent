package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import com.hragent.hragentv1.repo.KnowledgeArticleRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class MaternityPolicyTest {
    private KnowledgeArticle article(long id,String title,String region,String content) {
        var a=new KnowledgeArticle();a.setId(id);a.setTenantId(1L);a.setTitle(title);a.setCategory(PolicyCopilotService.PUBLIC_LEAVE);
        a.setContent(content);a.setRegion(region);a.setReviewStatus("APPROVED");a.setEffectiveFrom(LocalDate.of(2021,11,25));
        a.setSource("官方政策");a.setSourceUrl("https://xzfg.moj.gov.cn/front/law/detail?LawID=343");return a;
    }
    private PolicyAnswer answer(String location,String question) {
        var repo=mock(KnowledgeArticleRepository.class);
        when(repo.findByTenantIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(
            article(1,"产假天数","全国","基础产假 98 天。"),article(2,"产假与生育假天数","上海","符合上海条件通常共 158 天。"),
            article(3,"产假工资待遇","全国","生育津贴由生育保险基金支付。"),article(4,"流产假天数","全国","未满4个月15天；满4个月42天。")));
        var user=new UserAccount();user.setTenantId(1L);
        var profile=new ProfileView(3L,"E003","张三",location,null,null,null,null,null,null,null,null,List.of("职级"));
        return new PolicyCopilotService(repo,mock(EmployeeServiceProfileService.class)).answer(user,question,profile);
    }
    @Test void broadMaternityQuestionAnswersDaysRatherThanPayroll() {
        assertThat(answer("上海","我想问问产假问题").answer()).contains("98 天","158 天","咨询 HR")
            .doesNotContain("生育津贴由","42天","档案待","手册","暂时没有");
    }
    @Test void missingRegionStillProvidesNationalBaseline() {
        assertThat(answer(null,"产假多少天").answer()).contains("98 天","工作地","咨询 HR").doesNotContain("158 天");
        assertThat(answer("北京","产假多少天").answer()).doesNotContain("158 天");
        assertThat(answer("上海","北京产假多少天").answer()).doesNotContain("158 天");
    }
    @Test void benefitsAndMiscarriageHaveSeparateAnswers() {
        assertThat(answer("上海","产假工资怎么发").answer()).contains("生育津贴").doesNotContain("158 天","98 天");
        assertThat(answer("上海","流产产假多少天").answer()).contains("42天").doesNotContain("158 天","98 天");
    }
}
