package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.KnowledgeArticleRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HandbookKnowledgeServiceTest {
    private KnowledgeArticle reference(Long tenant, String title, String content) {
        var a = new KnowledgeArticle(); a.setTenantId(tenant); a.setCategory(HandbookKnowledgeService.CATEGORY);
        a.setTitle(title); a.setContent(content); a.setReviewStatus("APPROVED"); a.setSource("hrmanual.pdf · 2022年版 · PDF第18页"); return a;
    }
    @Test void retrievesAnnualLeaveSynonymWithoutInventingEffectiveDate() {
        var repo = mock(KnowledgeArticleRepository.class);
        var a = reference(1L, "职工带薪年休假条例", "职工累计工作已满1年不满10年的，年休假5天。");
        when(repo.findByTenantIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(a));
        var user = new UserAccount(); user.setTenantId(1L);
        assertThat(new HandbookKnowledgeService(repo).answer(user, "年假有多少天").orElseThrow())
                .contains("年假5天", "待核实", "累计工龄")
                .doesNotContain("根据你提供的手册", "条文如下", "文号：", "PDF第");
        assertThat(new HandbookKnowledgeService(repo).answer(user, "年假多少天，出处是什么").orElseThrow())
                .contains("PDF第18页", "2022年版");
        assertThat(a.getEffectiveFrom()).isNull();
    }
    @Test void ignoresForeignTenantAndUnapprovedReferences() {
        var repo = mock(KnowledgeArticleRepository.class);
        var draft = reference(1L, "试用期", "试用期的期限条件"); draft.setReviewStatus("DRAFT");
        when(repo.findByTenantIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(draft, reference(2L, "试用期", "试用期的期限条件")));
        assertThat(new HandbookKnowledgeService(repo).search(1L, "试用期多久")).isEmpty();
    }
    @Test void unrelatedQuestionsDoNotReturnRandomClauses() {
        var repo = mock(KnowledgeArticleRepository.class);
        when(repo.findByTenantIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(reference(1L, "年休假条例", "年休假5天")));
        assertThat(new HandbookKnowledgeService(repo).search(1L, "今晚吃火锅吗")).isEmpty();
    }
    @Test void preservesPersonalBalancesAndExplicitActionsForBusinessServices() {
        assertThat(HandbookKnowledgeService.skipReferenceLookup("我还有几天年假")).isTrue();
        assertThat(HandbookKnowledgeService.skipReferenceLookup("查询我的年假余额")).isTrue();
        assertThat(HandbookKnowledgeService.skipReferenceLookup("我要提交年假申请")).isTrue();
        assertThat(HandbookKnowledgeService.skipReferenceLookup("申请年假有什么规定")).isFalse();
    }
}
