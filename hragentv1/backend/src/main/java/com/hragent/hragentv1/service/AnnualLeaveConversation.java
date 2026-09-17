package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.WebChatDtos.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Voluntary policy explanation only. Never edits HR seniority or leave balances. */
final class AnnualLeaveConversation {
 private record Key(Long tenant,Long employee,String conversation){}
 private record State(Integer months,boolean answered,long expires){}
 private final Map<Key,State> states=new ConcurrentHashMap<>();
 private final Clock clock;
 AnnualLeaveConversation(){this(Clock.systemUTC());}
 AnnualLeaveConversation(Clock clock){this.clock=clock;}
 static final String SOURCES="计算口径（核对日期：2026-09-10）：\n"
  +"[《职工带薪年休假条例》第二至四条](https://rsj.sh.gov.cn/tgwyxzfgwj_17255/20200617/t0035_1388259.html)，2008-01-01 起施行。累计工龄决定法定全年基础档位；特殊不享受情形需另核对。\n"
  +"[《企业职工带薪年休假实施办法》第三至五条、第十三条](https://rsj.sh.gov.cn/trlzyhshbzbgz_17256/20200617/t0035_1388390.html)，2008-09-18 起施行。连续工作条件、当年新入职折算及企业更优约定分别核对。\n"
  +"[人社厅函〔2009〕149号](https://www.mohrss.gov.cn/wap/zc/zcwj/200904/t20090415_91149.html)：以前单位的工作时间可以计入累计工龄，不能只按本公司入职日期计算。\n"
  +"对话中的工龄是员工自述，仅用于说明规则；不是系统已核定额度或实时余额。年中达到新工龄档位、入离职折算、特殊不享受情形及企业更优福利需进一步核对。";
 private static ChatAction say(String label,String value){return new ChatAction(label,"message",value);}
 private MessageResponse response(String text,List<ChatAction> actions,boolean evidence){return new MessageResponse(text,"annual-leave-guide",UUID.randomUUID().toString(),actions,evidence?SOURCES:null);}
 private Key key(UserAccount u,String cid){return cid==null||cid.isBlank()?null:new Key(u.getTenantId(),u.getId(),cid);}
 boolean relevant(UserAccount u,String message,String cid){var k=key(u,cid);return (k!=null&&states.containsKey(k))||question(message);}
 void clear(UserAccount u,String cid){var k=key(u,cid);if(k!=null)states.remove(k);}
 private void save(Key k,Integer months,boolean answered){if(k==null)return;states.entrySet().removeIf(e->e.getValue().expires<clock.millis());if(states.size()>2000)states.clear();states.put(k,new State(months,answered,clock.millis()+15*60*1000));}
 static boolean question(String t){return (t.contains("年假")||t.contains("年休假"))&&t.matches(".*(几天|多少天|多少年假|多少年休假|天数|怎么算|怎么计算|工作多久|工龄|总共|一共).*" )&&!t.matches(".*(余额|还剩|剩余|剩几天|原文|全文|查询我的|查我的|系统|条例全文).*" );}
 Optional<MessageResponse> reply(UserAccount u,String raw,String cid){
  String t=raw.replaceAll("[\\s。！!?？]","");var k=key(u,cid);var state=k==null?null:states.get(k);
  if(state!=null&&state.expires<clock.millis()){states.remove(k);state=null;}
  boolean start=question(t);
  if(start){save(k,null,false);state=new State(null,false,clock.millis()+900000);}
  if(state==null)return Optional.empty();
  if(state.answered&&!start){clear(u,cid);return Optional.empty();}
  if(!start && (CareConversationGuide.isCareTurn(raw)||!ErSafety.risk(raw).equals("NONE")||LifecycleService.intent(raw)!=null||t.matches(".*(余额|还剩|剩余|期限|原文|全文|取消|换个话题|出差|报销|病假|工资|不聊了|查我的|查询我的).*"))){clear(u,cid);return Optional.empty();}
  if(t.matches(".*(不清楚|不确定|不知道|记不清).*")){save(k,null,true);return Optional.of(response("没关系，先不用猜。累计工龄可以结合以前的劳动合同、社保记录等核对。我也可以先帮你查系统已经核定的年假额度。",List.of(say("查系统核定额度","查询我的年假总额")),true));}
  Integer months=months(t);
  if(start&&months==null&&!t.matches(".*(本公司|这家公司|入职|这里).*")){
   var given=Pattern.compile("(?:累计工作|累计工龄|工龄|一共工作|总共工作|我工作)(?:了|有|是)?([0-9零一二两三四五六七八九十]+年(?:半|[0-9一二两三四五六七八九十]+个月)?)").matcher(t);
   if(given.find())months=months(given.group(1));
  }
  if(state.months==null){
   if(months==null){
    if(!start&&!t.matches(".*(年|月|工龄|多久|以前|本公司|这里|这家|公司).*")){clear(u,cid);return Optional.empty();}
    save(k,null,false);
    return Optional.of(response(t.matches(".*(这家|本公司|这里|入职).*" )?"这里要看的是累计工龄，不只是进这家公司的时间。把以前的工作也算上，你一共工作多久了？":"可以，我帮你按自己的情况看。把以前公司的工作也算上，你累计工作多久了？比如可以说‘3年’。",List.of(say("还不到1年","还不到1年"),say("1年到9年","1年到9年"),say("10年到19年","10年到19年"),say("20年以上","20年以上")),false));
   }
   if(months<12){save(k,months,true);return Optional.of(response("按你说的累计工龄，目前还没满一年，通常还没达到法定年假的享受条件。公司额外给的福利假另算。要先看看账户里有没有可用假期吗？",List.of(say("查我的可用假期","查看我的假期")),true));}
   save(k,months,false);
   if(t.matches(".*(一直连续|一直没断|连续工作|连续上班|连续[一二三四五六七八九十0-9]).*"))return Optional.of(result(k,months));
   return Optional.of(response("明白了。再确认一点：你是否已经连续工作满12个月？这段连续工作的时间可以包含以前的公司。",List.of(say("已经连续满12个月","已经连续满12个月"),say("还没满或有中断","连续工作时间还没满12个月"),say("不太确定","不太确定")),false));
  }
  if(months!=null&&!t.matches(".*(连续|12个月).*")){save(k,months,false);return Optional.of(response("好的，我按你更正的累计工龄来看。你是否已经连续工作满12个月？",List.of(say("已经满了","已经连续满12个月"),say("不太确定","不太确定")),false));}
  if(t.matches(".*(还没|未满|不满|中断|断过|没有).*" )||Set.of("没","否","不是").contains(t)){save(k,state.months,true);return Optional.of(response("明白，之前的累计工龄不会因此清零。不过连续工作的条件还要结合经历核对，我先不把基础档位当成你今年能休的天数。要看看系统已经核定的额度吗？",List.of(say("查系统核定额度","查询我的年假总额")),true));}
  if(t.matches("^(是|是的|对|对的|有|已经满了|满了|已经连续满12个月|连续工作满12个月|一直没断过|一直在工作|连续的|已满12个月)$")||t.matches("^(?:我)?(?:已经|已)?连续(?:工作)?满(?:12个月|一年)(?:了)?$"))return Optional.of(result(k,state.months));
  clear(u,cid);return Optional.empty();
 }
 private MessageResponse result(Key k,int months){save(k,months,true);int days=months>=240?15:months>=120?10:5;
  return response("按你说的工龄和连续工作情况，完整年度的法定基础年假是 **"+days+" 天**。这是全年基础，不是现在的剩余天数；今年入职折算、特殊情形和公司额外福利还要另核对。\n\n要我查一下系统里今年核定了多少、现在还能休几天吗？",List.of(say("查今年核定额度","查询我的年假总额"),say("查现在可用天数","查询我的年假余额"),say("开始请假","我要休年假")),true);
 }
 static Integer months(String input){
  String t=input.replaceAll("[\\s。！!?？]","");if(t.matches(".*(本公司|这家公司|这家|入职|这里).*"))return null;
  if(t.equals("还不到1年")||t.equals("不到一年")||t.equals("不满一年"))return 6;
  if(t.equals("1年到9年"))return 12;if(t.equals("10年到19年"))return 120;if(t.equals("20年以上"))return 240;
  if(t.matches(".*(到|至|大概|差不多|左右|多|不到|不满|超过|以上|以下|将近|快满).*"))return null;
  t=t.replace("连续","").replace("半年","6个月").replace("一年半","1年6个月").replace("年半","年6个月");
  var chinese=Pattern.compile("([零一二两三四五六七八九十]{1,3})(?=年|个月|月)").matcher(t);var b=new StringBuilder();while(chinese.find()){int n=chineseNumber(chinese.group(1));if(n<0)return null;chinese.appendReplacement(b,String.valueOf(n));}chinese.appendTail(b);t=b.toString();
  var m=Pattern.compile("^(?:(?:我)?(?:累计|一共|总共)?(?:工作|工龄|上班)?(?:了|是|有)?)?(?:(\\d{1,2})(?:年))?(?:(\\d{1,2})(?:个月|月))?(?:了)?$").matcher(t);
  if(!m.matches()||(m.group(1)==null&&m.group(2)==null))return null;
  int y=m.group(1)==null?0:Integer.parseInt(m.group(1)),mo=m.group(2)==null?0:Integer.parseInt(m.group(2));if(y>70||mo>120||y>0&&mo>=12)return null;return y*12+mo;
 }
 private static int chineseNumber(String t){String digits="零一二三四五六七八九";t=t.replace('两','二');if(t.equals("十"))return 10;int at=t.indexOf('十');if(at>=0){int a=at==0?1:digits.indexOf(t.charAt(0)),b=at==t.length()-1?0:digits.indexOf(t.charAt(at+1));return a<0||b<0?-1:a*10+b;}return t.length()==1?digits.indexOf(t.charAt(0)):-1;}
}
