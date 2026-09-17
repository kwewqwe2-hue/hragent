package com.hragent.hragentv1.service;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LeaveDateParserTest {
 final LocalDate today=LocalDate.of(2026,9,10);
 @Test void compactDatesAndMixedRangesNormalizeWithoutLosingEitherEnd(){
  for(String text:new String[]{"915","0915","９１５"}){
   var r=LeaveDateParser.parse(text,today,null);assertThat(r.start()).as(text).isEqualTo("2026-09-15");assertThat(r.end()).isNull();assertThat(r.confirmation()).isTrue();
  }
  for(String text:new String[]{"915-917","0915-0917","915至917","915-9.17","9/15-917","915-17","我想请事假915-917"}){
   var r=LeaveDateParser.parse(text,today,null);assertThat(r.start()).as(text).isEqualTo("2026-09-15");assertThat(r.end()).as(text).isEqualTo("2026-09-17");assertThat(r.confirmation()).isTrue();assertThat(LeaveDateParser.mentionsDate(text)).isTrue();
  }
  assertThat(LeaveDateParser.parse("917",today,LocalDate.of(2027,9,15)).start()).isEqualTo("2027-09-17");
  assertThat(LeaveDateParser.parse("1230-0102",today,null).end()).isEqualTo("2027-01-02");
 }
 @Test void ambiguousCompactDatesAskForClarificationInsteadOfChoosing(){
  for(String text:new String[]{"111","111-113"})assertThatThrownBy(()->LeaveDateParser.parse(text,today,null)).hasMessageContaining("1月11日").hasMessageContaining("11月1日").hasMessageContaining("你指哪一天");
  for(String text:new String[]{"931","0000","999","915-914","915-917-919","123456"})assertThatThrownBy(()->LeaveDateParser.parse(text,today,null)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void twoDigitYearIsExpandedAndAlwaysRequiresConfirmation(){
  for(String text:new String[]{"26.9.15","26/9/15","26-9-15","26年9月15日","２６．９．１５","９／１５"}){
   var r=LeaveDateParser.parse(text,today,null);assertThat(r.start()).as(text).isEqualTo("2026-09-15");assertThat(r.end()).isNull();assertThat(r.confirmation()).isTrue();
   assertThat(LeaveDateParser.mentionsDate(text)).as(text).isTrue();
  }
  assertThat(LeaveDateParser.parse("28/2/29",today,null).start()).isEqualTo("2028-02-29");
  assertThatThrownBy(()->LeaveDateParser.parse("26/2/29",today,null)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void slashAndShortYearRangesKeepBothDatesAndExplicitYears(){
  for(String text:new String[]{"9/15-9/17","26.9.15-9.17","26.9.15-26.9.17","26/9/15-9/17","26/9/15至9/17","26-9-15到26-9-17","09.15-17","09/15-17"}){
   var r=LeaveDateParser.parse(text,today,null);assertThat(r.start()).as(text).isEqualTo("2026-09-15");assertThat(r.end()).as(text).isEqualTo("2026-09-17");assertThat(r.confirmation()).isTrue();
  }
  var r=LeaveDateParser.parse("26.12.30-27.1.2",today,null);assertThat(r.end()).isEqualTo("2027-01-02");
  assertThatThrownBy(()->LeaveDateParser.parse("26.12.30-26.1.2",today,null)).hasMessageContaining("结束日期在开始日期之前");
  assertThatThrownBy(()->LeaveDateParser.parse("26/1/2",today,LocalDate.of(2026,12,30))).hasMessageContaining("比已选的开始日期");
 }
 @Test void normalizesCommonSingleDatesAndConfirmsInferences(){
  for(String text:new String[]{"9.15","9/15","9-15","9月15日","9月15号","2026/9/15","2026.9.15","2026年9月15日","从 9．15"}){
   var r=LeaveDateParser.parse(text,today,null);assertThat(r.start()).as(text).isEqualTo("2026-09-15");assertThat(r.end()).isNull();assertThat(r.confirmation()).isTrue();
  }
  assertThat(LeaveDateParser.parse("2026-09-15",today,null).confirmation()).isFalse();
 }
 @Test void rangeIsNotTruncatedToItsFirstDate(){
  for(String text:new String[]{"9.15-9.17","9/15至9/17","9月15日至17日","9.15～17","2026-09-15-2026-09-17","9-15 - 9-17","我想请事假9.15-9.17"}){
   var r=LeaveDateParser.parse(text,today,null);assertThat(r.start()).as(text).isEqualTo("2026-09-15");assertThat(r.end()).as(text).isEqualTo("2026-09-17");assertThat(r.confirmation()).isTrue();
  }
 }
 @Test void relativeDatesUseEmployeeCalendarAndAlwaysConfirm(){
  assertThat(LeaveDateParser.parse("明天",today,null).start()).isEqualTo("2026-09-11");
  var r=LeaveDateParser.parse("下周一到下周三",today,null);assertThat(r.start()).isEqualTo("2026-09-14");assertThat(r.end()).isEqualTo("2026-09-16");
  assertThat(LeaveDateParser.parse("17日",today,LocalDate.of(2027,9,15)).start()).isEqualTo("2027-09-17");
 }
 @Test void crossYearIsExplicitInNormalizedProposal(){
  var r=LeaveDateParser.parse("12.30-1.2",today,null);assertThat(r.start()).isEqualTo("2026-12-30");assertThat(r.end()).isEqualTo("2027-01-02");
  assertThat(LeaveDateParser.parse("1.2",today,LocalDate.of(2026,12,30)).start()).isEqualTo("2027-01-02");
 }
 @Test void invalidOrAmbiguousDatesAreNotSilentlyCorrected(){
  for(String text:new String[]{"9.31","2.29","9.17-9.15","2026-12-30到2026-1-2","9.15或9.17","9.15-9.17-9.19","9.15半天","2026-09-15到"})assertThatThrownBy(()->LeaveDateParser.parse(text,today,null)).as(text).isInstanceOf(IllegalArgumentException.class);
  assertThat(LeaveDateParser.parse("2028年2月29日",today,null).start()).isEqualTo("2028-02-29");
 }
}
