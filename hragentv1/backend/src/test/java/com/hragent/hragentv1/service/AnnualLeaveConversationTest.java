package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.UserAccount;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AnnualLeaveConversationTest {
 @Test void naturalConfirmationContinuesTheInterview(){var g=new AnnualLeaveConversation();var u=user(1,1);g.reply(u,"年假有多少天","natural");g.reply(u,"三年","natural");assertThat(g.reply(u,"我已经连续工作满12个月了","natural").orElseThrow().answer()).contains("**5 天**");}
 UserAccount user(long tenant,long employee){var u=mock(UserAccount.class);when(u.getTenantId()).thenReturn(tenant);when(u.getId()).thenReturn(employee);return u;}
 @Test void explainsOnlyPersonalBandAfterTwoShortQuestions(){
  var g=new AnnualLeaveConversation();var u=user(1,1);
  var first=g.reply(u,"我的年假总共多少天","a").orElseThrow();assertThat(first.answer()).contains("累计工作多久").doesNotContain("5 天","10 天","15 天");
  assertThat(g.reply(u,"三年","a").orElseThrow().answer()).contains("连续工作满12个月");
  var result=g.reply(u,"是的","a").orElseThrow();assertThat(result.answer()).contains("**5 天**","不是现在的剩余").doesNotContain("10 天","15 天");assertThat(result.details()).contains("rsj.sh.gov.cn","企业更优");verify(u,never()).setEntryDate(any());
 }
 @Test void boundariesAndChineseTenures(){
  for(String years:new String[]{"1年","九年","10年","十九年","二十年","25年"}){
   var g=new AnnualLeaveConversation();var u=user(1,1);g.reply(u,"年假有几天","a");g.reply(u,years,"a");int months=AnnualLeaveConversation.months(years);assertThat(g.reply(u,"已经连续满12个月","a").orElseThrow().answer()).contains("**"+(months>=240?15:months>=120?10:5)+" 天**");
  }
  assertThat(AnnualLeaveConversation.months("半年")).isEqualTo(6);assertThat(AnnualLeaveConversation.months("一年半")).isEqualTo(18);assertThat(AnnualLeaveConversation.months("3年6个月")).isEqualTo(42);
 }
 @Test void knownCumulativeTenureIsNotAskedAgain(){var g=new AnnualLeaveConversation();assertThat(g.reply(user(1,1),"我累计工龄三年，年假有几天","a").orElseThrow().answer()).contains("连续工作满12个月").doesNotContain("累计工作多久");}
 @Test void companyTenureIsNotTotalTenure(){var g=new AnnualLeaveConversation();var u=user(1,1);g.reply(u,"年假有几天","a");assertThat(g.reply(u,"我在这家公司3年","a").orElseThrow().answer()).contains("以前的工作");assertThat(AnnualLeaveConversation.months("大概九到十年")).isNull();}
 @Test void unknownOrInterruptedExperienceDoesNotInventCurrentEntitlement(){
  var g=new AnnualLeaveConversation();var u=user(1,1);g.reply(u,"年假有几天","a");assertThat(g.reply(u,"不知道","a").orElseThrow().answer()).contains("不用猜");
  g.reply(u,"年假有几天","a");g.reply(u,"3年","a");assertThat(g.reply(u,"中间断过","a").orElseThrow().answer()).contains("不会因此清零").doesNotContain("**5 天**");
 }
 @Test void isolationExpiryAndTopicSwitches(){
  var clock=mock(Clock.class);when(clock.millis()).thenReturn(1000L);var g=new AnnualLeaveConversation(clock);var u=user(1,1);g.reply(u,"年假有几天","a");
  assertThat(g.reply(user(2,1),"3年","a")).isEmpty();assertThat(g.reply(user(1,2),"3年","a")).isEmpty();assertThat(g.reply(u,"3年","b")).isEmpty();
  assertThat(g.reply(u,"我要休假","a")).isEmpty();assertThat(g.reply(u,"3年","a")).isEmpty();
  g.reply(u,"年假有几天","a");when(clock.millis()).thenReturn(1000000L);assertThat(g.reply(u,"3年","a")).isEmpty();
 }
 @Test void liveBalancesAndCareAreNotTurnedIntoTenureInterview(){var g=new AnnualLeaveConversation();var u=user(1,1);assertThat(g.reply(u,"查询我的年假余额","a")).isEmpty();g.reply(u,"年假有几天","a");assertThat(g.reply(u,"我现在心情很差","a")).isEmpty();assertThat(g.reply(u,"3年","a")).isEmpty();}
}
