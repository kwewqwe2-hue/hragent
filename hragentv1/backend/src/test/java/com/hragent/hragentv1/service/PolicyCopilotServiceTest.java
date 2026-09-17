package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import com.hragent.hragentv1.repo.KnowledgeArticleRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PolicyCopilotServiceTest {
    @Test void differentActiveVersionsOfSamePolicyRequireHrReview() {
        var repo=mock(KnowledgeArticleRepository.class); var profiles=mock(EmployeeServiceProfileService.class);
        var user=new UserAccount(); user.setTenantId(1L); var first=article(); var second=article(); second.setId(2L); second.setContent("另一版不同的住宿标准，等待核实。");
        when(repo.findByTenantIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(first,second));
        var result=new PolicyCopilotService(repo,profiles).answer(user,"出差住宿标准",profile("上海","P3","研发","上海示例公司"));
        assertThat(result.status()).isEqualTo("NEEDS_REVIEW"); assertThat(result.answer()).contains("暂不能给出确定执行标准");
    }
    private final LocalDate today = EmployeeReminderService.today();
    private ProfileView profile(String city, String grade, String type, String entity) {
        return new ProfileView(3L, "E003", "员工", city, grade, type, entity, null, null, null, null, null, List.of());
    }
    private KnowledgeArticle article() {
        var a = new KnowledgeArticle(); a.setId(1L); a.setTenantId(1L); a.setCategory("差旅");
        a.setTitle("差旅报销制度"); a.setContent("上海研发 P3 员工住宿标准以差旅制度第三条执行。");
        a.setSource("HR-2026-01 第三条"); a.setEffectiveFrom(today.minusDays(1));
        a.setRegion("上海"); a.setJobGrades("P3,P4"); a.setWorkTypes("研发"); a.setLegalEntities("上海示例公司");
        return a;
    }
    @Test void matchesEveryEmployeeDimensionAndCitySuffix() {
        assertThat(PolicyCopilotService.applicable(article(), profile("上海市", "P3", "研发", "上海示例公司"), today)).isTrue();
        assertThat(PolicyCopilotService.applicable(article(), profile("北京", "P3", "研发", "上海示例公司"), today)).isFalse();
        assertThat(PolicyCopilotService.applicable(article(), profile("上海", "P30", "研发", "上海示例公司"), today)).isFalse();
        assertThat(PolicyCopilotService.applicable(article(), profile("上海", "P3", "销售", "上海示例公司"), today)).isFalse();
        assertThat(PolicyCopilotService.applicable(article(), profile("上海", "P3", "研发", "上海示例公司分公司"), today)).isFalse();
    }
    @Test void missingAttributesCannotMatchRestrictedPolicy() {
        assertThat(PolicyCopilotService.applicable(article(), profile(null, "P3", "研发", "上海示例公司"), today)).isFalse();
        assertThat(PolicyCopilotService.applicable(article(), profile("上海", null, "研发", "上海示例公司"), today)).isFalse();
    }
    @Test void publishedDateNeverSubstitutesForEffectiveDate() {
        var a = article(); a.setPublishedAt(today); a.setEffectiveFrom(null);
        assertThat(PolicyCopilotService.applicable(a, profile("上海", "P3", "研发", "上海示例公司"), today)).isFalse();
    }
    @Test void excludesDraftFutureExpiredAndUnattributedPolicies() {
        var p = profile("上海", "P3", "研发", "上海示例公司");
        var a = article(); a.setReviewStatus("DRAFT"); assertThat(PolicyCopilotService.applicable(a, p, today)).isFalse();
        a = article(); a.setEffectiveFrom(today.plusDays(1)); assertThat(PolicyCopilotService.applicable(a, p, today)).isFalse();
        a = article(); a.setEffectiveTo(today.minusDays(1)); assertThat(PolicyCopilotService.applicable(a, p, today)).isFalse();
        a = article(); a.setSource(" "); assertThat(PolicyCopilotService.applicable(a, p, today)).isFalse();
        a = article(); a.setEffectiveFrom(today); a.setEffectiveTo(today); assertThat(PolicyCopilotService.applicable(a, p, today)).isTrue();
    }
    @Test void universalPolicyCanMatchWithoutGrade() {
        var a = article(); a.setJobGrades(null); a.setWorkTypes(null); a.setLegalEntities(null); a.setRegion("全国");
        assertThat(PolicyCopilotService.applicable(a, profile(null, null, null, null), today)).isTrue();
    }
    @Test void citationsExcludeOtherTenantsEvenIfRepositoryReturnsOne() {
        var repository = mock(KnowledgeArticleRepository.class);
        var profiles = mock(EmployeeServiceProfileService.class);
        var user = new UserAccount(); user.setId(3L); user.setTenantId(1L);
        when(profiles.profile(user)).thenReturn(profile("上海", "P3", "研发", "上海示例公司"));
        var foreign = article(); foreign.setTenantId(2L); foreign.setId(2L);
        when(repository.findByTenantIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(article(), foreign));
        var result = new PolicyCopilotService(repository, profiles).answer(user, "出差住宿标准");
        assertThat(result.citations()).extracting(Citation::id).containsExactly(1L);
        assertThat(result.answer()).contains("HR-2026-01 第三条", "生效日期：" + today.minusDays(1));
    }
    @Test void rejectUnsafeSourceLinks() {
        assertThatThrownBy(() -> EmployeeServiceSupport.requireHttpUrl("javascript:alert(1)")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> EmployeeServiceSupport.requireHttpUrl("https://user:pass@example.com")).isInstanceOf(RuntimeException.class);
        assertThatCode(() -> EmployeeServiceSupport.requireHttpUrl("https://example.com/policy")).doesNotThrowAnyException();
    }
}
