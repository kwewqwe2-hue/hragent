package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class CompanyBenefitReplyTest {
 private final LocalDate today=LocalDate.of(2026,9,11);
 private UserAccount user(){var u=new UserAccount();u.setTenantId(1L);return u;}
 private ProfileView profile(){return new ProfileView(1L,"E1","员工","上海",null,null,"上海公司",null,null,null,null,null,List.of());}
 private KnowledgeArticle article(String state){var a=new KnowledgeArticle();a.setId(1L);a.setTenantId(1L);a.setTitle("企业年金福利安排");a.setCategory("企业福利确认");a.setSource("HR 已确认");a.setContent("设立状态："+state);a.setEffectiveFrom(today.minusDays(1));a.setLegalEntities("上海公司");return a;}
 private PolicyAnswer answer(List<KnowledgeArticle> rows){return CompanyBenefitReply.answer(user(),"公司有年金吗",profile(),rows,today).orElseThrow();}
 @Test void absenceIsNotDenial(){assertThat(answer(List.of()).answer()).contains("不能直接说有或没有").doesNotContain("暂未设立");}
 @Test void explicitCurrentConfirmationCanBeUsed(){assertThat(answer(List.of(article("未设立"))).answer()).contains("暂未设立");assertThat(answer(List.of(article("已设立"))).answer()).contains("已确认设立");}
 @Test void conflictsAreNotResolvedByGuessing(){assertThat(answer(List.of(article("已设立"),article("未设立"))).status()).isEqualTo("NEEDS_HR");}
 @Test void wrongTenantExpiredFutureAndWrongEntityCannotBeUsed(){
  var a=article("未设立");a.setTenantId(2L);assertThat(answer(List.of(a)).status()).isEqualTo("NEEDS_HR");
  a.setTenantId(1L);a.setEffectiveTo(today.minusDays(1));assertThat(answer(List.of(a)).status()).isEqualTo("NEEDS_HR");
  a.setEffectiveTo(null);a.setEffectiveFrom(today.plusDays(1));assertThat(answer(List.of(a)).status()).isEqualTo("NEEDS_HR");
  a.setEffectiveFrom(today.minusDays(1));a.setLegalEntities("另一家公司");assertThat(answer(List.of(a)).status()).isEqualTo("NEEDS_HR");
 }
 @Test void publicRulesAreNotCompanyStatements(){var a=article("未设立");a.setCategory("政策法规");assertThat(answer(List.of(a)).status()).isEqualTo("NEEDS_HR");}
 @Test void disputesAndSafetyAreNotDeflected(){for(var q:List.of("公司年金漏缴怎么办","我要投诉企业年金","因为年金我想伤害自己"))assertThat(CompanyBenefitReply.answer(user(),q,profile(),List.of(),today)).isEmpty();}
 @Test void generalQuestionStillGetsBriefExplanation(){assertThat(CompanyBenefitReply.answer(user(),"企业年金是什么",profile(),List.of(),today).orElseThrow().answer()).contains("补充养老").doesNotContain("暂未设立");}
}
