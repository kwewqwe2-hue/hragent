package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.dto.DemoPolicyDtos;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OfficialPolicyIngestServiceTest {
 @Test void firstDocumentIsPendingAndDuplicateIsNotReopened(){
  var repo=mock(PolicyMonitorCandidateRepository.class);var service=new OfficialPolicyIngestService(repo);
  when(repo.findByTenantIdAndSourceIdAndContentHash(any(),any(),any())).thenReturn(Optional.empty(),Optional.of(new PolicyMonitorCandidate()));
  assertThat(service.ingest(1L,"official","https://example.gov.cn/a","上海","政策","条文",null,null)).isTrue();
  assertThat(service.ingest(1L,"official","https://example.gov.cn/a","上海","政策","条文",null,null)).isFalse();
  verify(repo,times(1)).save(argThat(c->c.getReviewStatus()==PolicyReviewStatus.PENDING_REVIEW&&c.getEffectiveAt()==null&&c.getTenantId()==1L));
 }
 @Test void officialApprovalRequiresMetadataAndRetiresSameUrlVersionLocally(){
  var repo=mock(PolicyMonitorCandidateRepository.class);var articles=mock(KnowledgeArticleRepository.class);var index=mock(KnowledgeIndexClient.class);
  var service=new PolicyMonitorService(null,repo,null,null,articles,index,mock(AuditService.class));
  var c=new PolicyMonitorCandidate();c.setId(8L);c.setTenantId(1L);c.setSourceId("official-abc");c.setReviewStatus(PolicyReviewStatus.PENDING_REVIEW);c.setSourceName("官方");c.setSourceUrl("https://example.gov.cn/a");c.setTitle("政策");c.setContent("内容");c.setVersion("abc");
  var actor=new UserAccount();actor.setId(1L);actor.setTenantId(1L);
  when(repo.findById(8L)).thenReturn(Optional.of(c));
  assertThatThrownBy(()->service.review(actor,8L,new DemoPolicyDtos.ReviewRequest(PolicyReviewStatus.APPROVED,"已核验"))).hasMessageContaining("生效日期");
  verifyNoInteractions(index,articles);
  var old=new KnowledgeArticle();old.setSourceUrl(c.getSourceUrl());old.setReviewStatus("APPROVED");old.setEffectiveFrom(LocalDate.of(2025,1,1));
  when(articles.findByTenantIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(old));when(articles.save(any())).thenAnswer(i->i.getArgument(0));when(repo.save(any())).thenAnswer(i->i.getArgument(0));
  assertThatThrownBy(()->service.review(actor,8L,new DemoPolicyDtos.ReviewRequest(PolicyReviewStatus.APPROVED,"已核验",LocalDate.of(2026,10,1),"上海",LocalDate.of(2025,1,1),null))).hasMessageContaining("有效截止日期");
  service.review(actor,8L,new DemoPolicyDtos.ReviewRequest(PolicyReviewStatus.APPROVED,"已核验全文和废止关系",LocalDate.of(2026,10,1),"上海",LocalDate.of(2030,12,31),LocalDate.of(2026,9,1)));
  verify(articles).save(argThat(a->a!=old&&LocalDate.of(2030,12,31).equals(a.getEffectiveTo())&&LocalDate.of(2026,9,1).equals(a.getPublishedAt())));
  assertThat(old.getEffectiveTo()).isEqualTo(LocalDate.of(2026,9,30));assertThat(c.getReviewStatus()).isEqualTo(PolicyReviewStatus.APPROVED);verifyNoInteractions(index);
 }
}
