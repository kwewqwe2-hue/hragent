package com.hragent.hragentv1.service;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
class OfficialPolicyCrawlerTest {
 @Test void discoversReimbursementUpdatesAndRejectsDrafts(){
  assertThat(OfficialPolicyCrawler.SOURCES).anyMatch(s->s.id().equals("finance")&&s.url().equals("https://kjs.mof.gov.cn/zhengcefabu/"));
  for(String t:java.util.List.of("关于规范电子会计凭证报销入账归档的通知","关于推广应用电子凭证会计数据标准的通知","关于印发《管理会计应用指引第804号——财务共享服务》的通知","关于铁路客运推广使用全面数字化的电子发票的公告"))assertThat(OfficialPolicyCrawler.relevant(t)).isTrue();
  assertThat(OfficialPolicyCrawler.relevant("关于征求《财务共享服务（征求意见稿）》意见的函")).isFalse();
 }
 @Test void onlyAcceptsVerifiedMofMissingRobotsRedirect(){
  assertThat(OfficialPolicyCrawler.mofMissingRobotsRedirect("https://kjs.mof.gov.cn/zhengcefabu/",302,"http://www.mof.gov.cn/404.htm")).isTrue();
  assertThat(OfficialPolicyCrawler.mofMissingRobotsRedirect("https://example.com/",302,"http://www.mof.gov.cn/404.htm")).isFalse();
  assertThat(OfficialPolicyCrawler.mofMissingRobotsRedirect("https://kjs.mof.gov.cn/",302,"https://example.com/404.htm")).isFalse();
  assertThat(OfficialPolicyCrawler.mofMissingRobotsRedirect("https://kjs.mof.gov.cn/",403,"http://www.mof.gov.cn/404.htm")).isFalse();
 }
 @Test void respectsRobotsGroupsWildcardsAndEndAnchors(){
  String rules="User-agent: other\nUser-agent: *\nDisallow: /private/*\nAllow: /private/public$\nUser-agent: irrelevant\nDisallow: /";
  assertThat(OfficialPolicyCrawler.permitted(rules,"/private/secret")).isFalse();
  assertThat(OfficialPolicyCrawler.permitted(rules,"/private/public")).isTrue();
  assertThat(OfficialPolicyCrawler.permitted(rules,"/private/public/more")).isFalse();
  assertThat(OfficialPolicyCrawler.permitted(rules,"/policy")).isTrue();
 }
 @Test void refusesCrossHostAndUnsafeAddresses(){
  String base="https://rsj.sh.gov.cn/";
  assertThat(OfficialPolicyCrawler.sameHost(base,"https://rsj.sh.gov.cn/test.html")).isTrue();
  assertThat(OfficialPolicyCrawler.sameHost(base,"http://rsj.sh.gov.cn/test.html")).isFalse();
  assertThat(OfficialPolicyCrawler.sameHost(base,"https://rsj.sh.gov.cn.evil.test/test.html")).isFalse();
  assertThat(OfficialPolicyCrawler.sameHost(base,"https://user@rsj.sh.gov.cn/test.html")).isFalse();
 }
 @Test void neverInventsAnEffectiveDate(){
  assertThat(OfficialPolicyCrawler.effectiveDate("发布日期2026年9月9日")).isNull();
  assertThat(OfficialPolicyCrawler.effectiveDate("自2026年10月1日起施行")).isEqualTo(LocalDate.of(2026,10,1));
  assertThat(OfficialPolicyCrawler.effectiveDate("自2026年10月1日起施行，旧文自2025年1月1日起实施")).isNull();
 }
}
