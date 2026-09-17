package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.WebChatDtos.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Verified public guidance, not a personal entitlement or a change to the employee's profile. */
final class MaternityLeaveConversation {
 private record Key(Long tenant,Long employee,String cid){}
 private record State(String city,String focus,long expires){}
 private final Map<Key,State> states=new ConcurrentHashMap<>();
 private final Clock clock;
 MaternityLeaveConversation(){this(Clock.systemUTC());}
 MaternityLeaveConversation(Clock clock){this.clock=clock;}
 static final String SOURCES="官方依据（核对日期：2026-09-10）：\n\n"
  +"[《女职工劳动保护特别规定》第七条](https://www.shanghai.gov.cn/gwk/search/content/6e04ce0a62054be3a50040f3763fdeb8)，国务院令第619号，2012-04-28起施行。国家基础产假为98天，含可在产前安排的15天；难产及多胞胎按条款分别增加，流产另有15天或42天规则。\n\n"
  +"[《上海市人口与计划生育条例》第三十一条（2021-11-25修正）](https://www.shanghai.gov.cn/jcsfbrkcqjhfzzh/20230621/c440fb200a9b48da87aed931e369792f.html)：符合法律法规规定生育的夫妻，女方在国家产假之外享受60天生育假。\n\n"
  +"[《上海市计划生育奖励与补助若干规定》第二条](https://www.shanghai.gov.cn/nw12344/20221110/87151565cd6246c99854c129797d178c.html)：生育假一般与产假合并连续使用，遇法定节假日顺延。\n\n"
  +"这里说明公共规则；对话提供的工作地仅用于本次咨询，不修改员工档案。个人适用范围、特殊情形、单位额外福利和具体起止日期由HR结合材料核对。";
 private Key key(UserAccount u,String cid){return cid==null||cid.isBlank()?null:new Key(u.getTenantId(),u.getId(),cid);}
 private State state(Key k){if(k==null)return null;var s=states.get(k);if(s!=null&&s.expires<=clock.millis()){states.remove(k);return null;}return s;}
 void clear(UserAccount u,String cid){var k=key(u,cid);if(k!=null)states.remove(k);}
 private static String city(String text){return Arrays.stream("上海,北京,天津,重庆,广州,深圳,杭州,南京,苏州,成都,武汉,西安,郑州,长沙,济南,青岛,厦门,福州,合肥,昆明,南宁,南昌,贵阳,海口,沈阳,大连,长春,哈尔滨,太原,石家庄,兰州,西宁,银川,呼和浩特,乌鲁木齐,拉萨,宁波,无锡,东莞,佛山".split(",")).filter(text::contains).findFirst().orElse("");}
 static boolean question(String text){return (text.contains("产假")&&!text.contains("陪产假")||text.contains("生育假"))&&!text.matches(".*(申请进度|审批进度|批了吗|查看我的|查询我的|取消).*" );}
 boolean relevant(UserAccount u,String message,String cid){return question(message)||state(key(u,cid))!=null;}
 Optional<MessageResponse> reply(UserAccount u,String raw,String cid){
  String text=raw.replaceAll("[\\s。！？!?]","");var k=key(u,cid);var previous=state(k);
  if(!ErSafety.risk(raw).equals("NONE")||CareConversationGuide.isCareTurn(raw)||LifecycleService.cancellationRequested(raw)||LifecycleService.intent(raw)!=null){clear(u,cid);return Optional.empty();}
  boolean start=question(text);
  boolean follow=previous!=null&&(!city(text).isBlank()||applicationQuestion(text)||text.matches(".*(天数|几天|怎么计算|怎么算|材料|手续|原文|依据|规定|难产|双胞胎|多胞胎|流产|工资|津贴|待遇|国家标准|国家规定).*"));
  if(!start&&!follow){clear(u,cid);return Optional.empty();}
  // Other explicit policies and business requests must not be consumed as a maternity follow-up.
  if(!start&&text.matches(".*(年假|病假|报销|出差|工资条|陪产假|育儿假|婚假|查进度|证明申请).*")){clear(u,cid);return Optional.empty();}
  String place=city(text);if(place.isBlank()&&previous!=null)place=previous.city;
  if(text.matches(".*(先看|只看|只想了解)国家.*"))place="";
  String focus=focus(text);
  if(!start&&focus.isBlank()&&previous!=null)focus=previous.focus;
  if(k!=null){states.entrySet().removeIf(e->e.getValue().expires<=clock.millis());if(states.size()>=2000)states.clear();states.put(k,new State(place,focus,clock.millis()+900000));}
  if(applicationQuestion(text))return Optional.of(response("可以呀，我陪你把产假申请一步步填写好 🌷\n\n点“开始产假申请”，我们先填计划休假的日期，再写一段简要说明。最后一起核对，你确认提交后，再交给 HRSSC 核验假期安排和所需材料。",List.of(say("开始产假申请","我要申请产假"),LifecycleNavigation.records()),false));
  if(place.isBlank()&&!text.contains("国家"))return Optional.of(response("好呀，我们先看你工作地的产假安排。你在哪个城市工作呢？",List.of(say("上海","上海"),say("先看国家规定","先看国家规定")),false));
  String subject=focus.isBlank()?text:focus;
  String answer;
  if(subject.matches(".*(原文|依据|全文).*"))return Optional.of(response("可以呀，官方条文放在下方，展开就能查看。",List.of(),true));
  if(subject.matches(".*(工资|津贴|待遇).*"))answer="产假天数和生育津贴是两件事，不能把休假天数直接换算成到账金额。津贴还涉及参保和待遇核定，可以请 HR 帮你对接经办渠道。";
  else if(subject.matches(".*(材料|手续|怎么办理).*"))return Optional.of(response("我们先整理计划休假的日期和简要说明就好。点“开始产假申请”，我会一项项陪你填写；核对并确认提交后，HRSSC 会核验安排并告知需要补充的材料。\n\n材料可在“我的服务单”安全补充，与申请无关的医疗隐私不用放进聊天里。",List.of(say("开始产假申请","我要申请产假"),LifecycleNavigation.records()),false));
  else if(subject.contains("流产"))answer="这类情况单独计算：国家规定，怀孕未满4个月流产为15天产假，满4个月为42天。先照顾好身体，不需要在这里透露更多医疗细节。\n\n具体起止日期和所需证明，再请 HR 结合医疗材料核对。";
  else if(subject.matches(".*(难产|双胞胎|多胞胎).*"))answer="国家规定，难产增加15天；多胞胎每多生育1个婴儿增加15天。特殊情况下，我们再把增加的天数单独算上。\n\n是否符合增加条件及最终起止日期，再请 HR 结合医疗材料核对。";
  else if(place.equals("上海"))answer="好呀，按上海的规定，符合法律法规规定生育、且不涉及难产或多胞胎等增加情形时，通常是 **158天**：国家产假98天，加上海生育假60天。\n\n生育假遇法定节假日还会顺延。个人适用情况、实际起止日期和申请材料，再请 HR 帮你核对一下就好。";
  else answer=(place.isBlank()?"先看国家规定呀：":"你说的工作地是"+place+"，先看国家规定：")+"基础产假是 **98天**，其中产前可休15天。地方增加的假期另算，98天不一定是最终总天数。\n\n当地增加天数、个人适用情况和申请材料，再结合现行地方规定与 HR 核对。";
  return Optional.of(response(answer,List.of(say("开始产假申请","我要申请产假"),say("特殊情形怎么算","产假难产或多胞胎怎么计算"),say("了解申请准备","产假需要什么材料")),true));
 }
 static boolean applicationQuestion(String text){return text.matches(".*(?:怎么|如何|怎样|哪里|哪儿).*(?:申请|办理|提交).*|.*(?:申请|办理).*(?:流程|步骤|入口).*")||Set.of("帮我申请","帮我办理","开始申请","开始办理","那就申请吧").contains(text);}
 private static String focus(String text){
  if(text.matches(".*(原文|依据|全文).*"))return "原文";
  if(text.matches(".*(工资|津贴|待遇).*"))return "待遇";
  if(text.matches(".*(材料|手续|怎么办理).*"))return "材料";
  if(text.contains("流产"))return "流产";
  if(text.matches(".*(难产|双胞胎|多胞胎).*"))return "难产或多胞胎";
  if(text.matches(".*(天数|几天|国家标准|国家规定).*"))return "天数";
  return "";
 }
 private static ChatAction say(String label,String value){return new ChatAction(label,"message",value);}
 private static MessageResponse response(String answer,List<ChatAction> actions,boolean evidence){return new MessageResponse(answer,"maternity-leave-guide",UUID.randomUUID().toString(),actions,evidence?SOURCES:null);}
}
