package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.ProfileView;
import com.hragent.hragentv1.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class OfficialPolicyAnswerTest {
 @Test void namedCityCanBeReadWithoutChangingEmployeeProfileAndExpiryIsEnforced(){
  var articles=mock(KnowledgeArticleRepository.class);var profiles=mock(EmployeeServiceProfileService.class);var candidates=mock(PolicyMonitorCandidateRepository.class);
  var service=new PolicyCopilotService(articles,profiles);ReflectionTestUtils.setField(service,"officialCandidates",candidates);
  var user=new UserAccount();user.setTenantId(1L);var profile=new ProfileView(3L,"E003","员工","北京","P3","研发","公司",null,null,null,null,null,List.of());
  var c=new PolicyMonitorCandidate();c.setSourceId("official-current");c.setTitle("上海工资支付办法");c.setRegion("上海");c.setSourceUrl("https://rsj.sh.gov.cn/current");c.setReviewStatus(PolicyReviewStatus.APPROVED);c.setKnowledgeArticleId(1L);c.setEffectiveAt(LocalDate.now().minusDays(2));c.setPublishedAt(LocalDate.now().minusDays(3));
  var a=new KnowledgeArticle();a.setId(1L);a.setTenantId(1L);a.setTitle(c.getTitle());a.setSource("上海人社");a.setSourceUrl(c.getSourceUrl());a.setContent("工资应当每月至少支付一次。");a.setEffectiveFrom(c.getEffectiveAt());a.setPublishedAt(c.getPublishedAt());a.setRegion("上海");
  var unrelated=new PolicyMonitorCandidate();unrelated.setSourceId("official-other");unrelated.setTitle("农民工工资保证金办法");unrelated.setRegion("上海");unrelated.setReviewStatus(PolicyReviewStatus.PENDING_REVIEW);
  when(candidates.findByTenantIdOrderByDetectedAtDesc(1L)).thenReturn(List.of(unrelated,c));when(articles.findByTenantIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(a));
  var answer=service.answer(user,"上海工资支付政策",profile);assertThat(answer.status()).isEqualTo("MATCHED");assertThat(answer.citations()).hasSize(1);assertThat(answer.profile().location()).isEqualTo("北京");
  assertThat(service.answer(user,"工资支付政策",profile).status()).isEqualTo("NEEDS_HR");
  unrelated.setTitle(c.getTitle());unrelated.setPublishedAt(LocalDate.now());assertThat(service.answer(user,"上海工资支付政策",profile).status()).isEqualTo("NEEDS_REVIEW");
  unrelated.setPublishedAt(LocalDate.now().minusYears(1));assertThat(service.answer(user,"上海工资支付政策",profile).status()).isEqualTo("MATCHED");
  when(candidates.findByTenantIdOrderByDetectedAtDesc(1L)).thenReturn(List.of(c));a.setEffectiveTo(LocalDate.now().minusDays(1));assertThat(service.answer(user,"上海工资支付政策",profile).status()).isEqualTo("NEEDS_HR");
 }
 @Test void pendingPoliciesNeverBecomeConfirmedAdviceAndFuturePoliciesAreExcluded(){
  var articles=mock(KnowledgeArticleRepository.class);var profiles=mock(EmployeeServiceProfileService.class);var candidates=mock(PolicyMonitorCandidateRepository.class);
  var service=new PolicyCopilotService(articles,profiles);ReflectionTestUtils.setField(service,"officialCandidates",candidates);
  var user=new UserAccount();user.setTenantId(1L);var profile=new ProfileView(3L,"E003","员工","上海市浦东新区","P3","研发","公司",null,null,null,null,null,List.of());
  var c=new PolicyMonitorCandidate();c.setTenantId(1L);c.setSourceId("official-x");c.setTitle("工资支付规定");c.setRegion("上海");c.setSourceUrl("https://example.gov.cn/policy");c.setReviewStatus(PolicyReviewStatus.PENDING_REVIEW);
  when(candidates.findByTenantIdOrderByDetectedAtDesc(1L)).thenReturn(List.of(c));when(articles.findByTenantIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
  assertThat(service.answer(user,"工资支付政策",profile).status()).isEqualTo("NEEDS_REVIEW");
  assertThat(service.answer(user,"北京工资支付政策",profile).status()).isEqualTo("NEEDS_HR");
  c.setEffectiveAt(LocalDate.now().plusDays(10));assertThat(service.answer(user,"工资支付政策",profile).status()).isEqualTo("NEEDS_HR");
 }
}
