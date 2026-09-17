package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.KnowledgeArticle;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.ProfileView;
import com.hragent.hragentv1.repo.KnowledgeArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class KnowledgeReferenceVisibilityTest {
    @Test void localHandbookDeleteDoesNotDependOnAnExternalIndexAndChecksTenant() {
        var a=new KnowledgeArticle();a.setId(8L);a.setTenantId(1L);a.setTitle("手册第4页｜政策");a.setCategory("员工手册参考");a.setSource("hrmanual.pdf · 2022年版 · PDF第4页");
        var repo=mock(KnowledgeArticleRepository.class);when(repo.findById(8L)).thenReturn(java.util.Optional.of(a));
        var rest=mock(org.springframework.web.client.RestClient.class);var audit=mock(AuditService.class);
        var service=mock(AdminService.class,CALLS_REAL_METHODS);ReflectionTestUtils.setField(service,"knowledgeArticleRepository",repo);ReflectionTestUtils.setField(service,"n8nClient",rest);ReflectionTestUtils.setField(service,"auditService",audit);
        var user=new com.hragent.hragentv1.domain.UserAccount();user.setTenantId(2L);
        assertThatThrownBy(()->service.deleteKnowledge(user,8L)).isInstanceOf(com.hragent.hragentv1.web.AppException.class);verify(repo,never()).delete(any());
        user.setTenantId(1L);service.deleteKnowledge(user,8L);verify(repo).delete(a);verifyNoInteractions(rest);
    }
    @Test void classifiedReferenceIsReadableButNeverAnApplicableRule() {
        var reference=new KnowledgeArticle();reference.setId(1L);reference.setTenantId(1L);
        reference.setSource("hrmanual.pdf · 政策主题摘编（2022资料）");reference.setCategory("休假与考勤");reference.setReviewStatus("REFERENCE");reference.setContent("已整理的历史手册摘编");reference.setEffectiveFrom(LocalDate.of(2020,1,1));
        var draft=new KnowledgeArticle();draft.setId(2L);draft.setReviewStatus("DRAFT");
        var unrelated=new KnowledgeArticle();unrelated.setId(3L);unrelated.setReviewStatus("REFERENCE");unrelated.setSource("其他未审批文件");
        var old=new KnowledgeArticle();old.setId(4L);old.setReviewStatus("SUPERSEDED");
        var repo=mock(KnowledgeArticleRepository.class);when(repo.findByTenantIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(reference,draft,unrelated,old));
        var service=mock(AdminService.class,CALLS_REAL_METHODS);ReflectionTestUtils.setField(service,"knowledgeArticleRepository",repo);
        assertThat(service.knowledge(1L,false)).extracting(KnowledgeArticle::getId).containsExactly(1L);
        assertThat(service.knowledge(1L,true)).hasSize(4);
        var p=new ProfileView(1L,"E1","员工","上海",null,null,null,null,null,null,null,null,List.of());
        assertThat(PolicyCopilotService.applicable(reference,p,LocalDate.now())).isFalse();
        assertThat(new HandbookKnowledgeService(repo).search(1L,"休假")).isEmpty();
    }
}
