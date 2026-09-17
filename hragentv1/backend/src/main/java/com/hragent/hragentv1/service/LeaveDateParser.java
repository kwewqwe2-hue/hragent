package com.hragent.hragentv1.service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Pattern;

/** Deterministic calendar interpretation; inferred dates are always confirmed by the employee. */
final class LeaveDateParser {
 private LeaveDateParser() {}
 private static final String YEAR="(?:20\\d{2}|\\d{2})";
 private static final String TOKEN="(?:(?:"+YEAR+"[-/.])?\\d{1,2}[-/.]\\d{1,2}|(?:"+YEAR+"年)?\\d{1,2}月\\d{1,2}[日号]?|今天|明天|后天|(?:本周|这周|下周|下星期|本星期)[一二三四五六日天]|(?<!\\d)\\d{3,4}(?!\\d))";
 private static final Pattern RANGE=Pattern.compile("^("+TOKEN+")(?:到|至|~|～|—|–|-)("+TOKEN+"|\\d{1,2}[日号]?)$");
 private static final Pattern SINGLE=Pattern.compile("^"+TOKEN+"$");
 private static final Pattern NUMERIC=Pattern.compile("^(?:("+YEAR+")[-/.])?(\\d{1,2})[-/.](\\d{1,2})$");
 private static final Pattern CHINESE=Pattern.compile("^(?:("+YEAR+")年)?(\\d{1,2})月(\\d{1,2})[日号]?$");
 record Selection(LocalDate start,LocalDate end,boolean confirmation) {}
 static boolean mentionsDate(String text){return Pattern.compile(TOKEN).matcher(normalize(text)).find();}
 private static String normalize(String text){return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC).replaceAll("\\s+","");}
 static Selection parse(String input,LocalDate today,LocalDate startContext){
  String text=normalize(input);
  text=text.replaceFirst("^(?:(?:我想|我要|我需要|帮我)?(?:申请)?(?:请|休)?(?:年休假|年假|病假|事假|休假|请假)|(?:改成|改为|(?:不是|不对)[，,]?是|(?:不是|不对)[，,]?|应该是|是|从))","");
  text=text.replaceFirst("^从","").replaceFirst("(?:期间|请假|休假)?[。！!，,]?$","");
  var range=RANGE.matcher(text);
  try {
   // A three-part date such as 26-9-15 is a single date, not 26-9 through the 15th.
   if(SINGLE.matcher(text).matches() || startContext!=null&&text.matches("\\d{1,2}[日号]")){
    LocalDate value=null;
    try{value=one(text,today,startContext==null?today:startContext);}
    catch(DateTimeException e){if(!range.matches())throw e;} // e.g. 09.15-17 remains September 15–17.
    if(value!=null){
    if(startContext!=null&&value.isBefore(startContext)&&!explicitYear(text)&&startContext.getMonthValue()==12&&value.getMonthValue()==1)value=value.plusYears(1);
    if(startContext!=null&&value.isBefore(startContext))throw new IllegalArgumentException("这个结束日期比已选的开始日期 "+startContext+" 早。你想休到哪一天呢？");
    return new Selection(value,null,!text.matches("20\\d{2}-\\d{2}-\\d{2}"));
    }
   }
   if(range.matches()){
    LocalDate start=one(range.group(1),today,startContext==null?today:startContext);
    String endText=range.group(2);LocalDate end=one(endText,today,start);
    // Only a month wrapping from December into January implies the following year.
    if(end.isBefore(start)&&!explicitYear(endText)&&start.getMonthValue()==12&&end.getMonthValue()==1)end=end.plusYears(1);
    if(end.isBefore(start))throw new IllegalArgumentException("这段时间的结束日期在开始日期之前。你原本想从哪天休到哪天？");
    return new Selection(start,end,true);
   }
  }catch(DateTimeException e){throw new IllegalArgumentException("这个月没有你提到的那一天。再告诉我一下想休的日期好吗？比如‘9月15日’，或‘9.15-9.17’。");}
  throw new IllegalArgumentException("我还没确定你想休的时间。可以说‘9月15日’、‘明天’，也可以一次告诉我‘9.15-9.17’。");
 }
 private static boolean explicitYear(String text){var m=NUMERIC.matcher(text);if(!m.matches())m=CHINESE.matcher(text);return m.matches()&&m.group(1)!=null;}
 private static LocalDate one(String text,LocalDate today,LocalDate context){
  if(text.matches("\\d{3,4}"))return compact(text,context.getYear());
  if(text.equals("今天"))return today;if(text.equals("明天"))return today.plusDays(1);if(text.equals("后天"))return today.plusDays(2);
  if(text.matches("(?:本周|这周|下周|下星期|本星期)[一二三四五六日天]")){
   int day="一二三四五六日天".indexOf(text.charAt(text.length()-1))+1;if(day==8)day=7;
   return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(text.startsWith("下")?1:0).plusDays(day-1);
  }
  if(text.matches("\\d{1,2}[日号]?"))return context.withDayOfMonth(Integer.parseInt(text.replaceAll("[日号]","")));
  var m=NUMERIC.matcher(text);if(!m.matches())m=CHINESE.matcher(text);
  if(!m.matches())throw new IllegalArgumentException("再告诉我一下具体日期好吗？");
  String yearText=m.group(1);int year=yearText==null?context.getYear():Integer.parseInt(yearText);
  // Two-digit years refer to 2000–2099; the full interpretation is never applied without confirmation.
  if(yearText!=null&&yearText.length()==2)year+=2000;
  return LocalDate.of(year,Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));
 }
 private static LocalDate compact(String text,int year){
  var candidates=new java.util.LinkedHashSet<LocalDate>();
  for(int split=1;split<=2;split++){
   if(text.length()-split>2)continue;
   try{candidates.add(LocalDate.of(year,Integer.parseInt(text.substring(0,split)),Integer.parseInt(text.substring(split))));}
   catch(DateTimeException ignored){}
  }
  if(candidates.isEmpty())throw new DateTimeException("No valid month/day split");
  if(candidates.size()>1)throw new IllegalArgumentException("‘"+text+"’可能是 "+String.join("，也可能是 ",candidates.stream().map(d->d.getMonthValue()+"月"+d.getDayOfMonth()+"日").toList())+"。你指哪一天呢？请带上‘月’和‘日’，如果是一段时间，也可以重新告诉我完整区间。");
  return candidates.iterator().next();
 }
}
