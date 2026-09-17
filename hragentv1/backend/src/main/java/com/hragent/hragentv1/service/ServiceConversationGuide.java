package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.WebChatDtos.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Scoped hand-offs to existing authenticated operations. Stores only route/stage, never form answers. */
final class ServiceConversationGuide {
 record Route(String name,String command,String prompt,String progress,String path){}
 private record Key(Long tenant,Long employee,String conversation){}
 private record Context(Route route,boolean draft,long expires){}
 private final Map<Key,Context> contexts=new ConcurrentHashMap<>();
 private final Clock clock;
 ServiceConversationGuide(){this(Clock.systemUTC());}
 ServiceConversationGuide(Clock clock){this.clock=clock;}
 private static Route route(String name,String command,String prompt,String progress,String path){return new Route(name,command,prompt,progress,path);}
 private static Route leave(String name){return route(name,"我要申请"+name,"我陪你一步步填写计划日期和原因，核对后再提交审核。"+(name.equals("病假")?"病假填好后，还需要从专用入口上传病历或诊断证明，初检后交给人工核验。":name.equals("产假")?"产假安排和所需材料会由 HRSSC 核验。":""),name.equals("产假")?"查看我的服务申请":"查看我的请假",name.equals("产假")?null:"/my-leave");}
 private static Route service(String name,String command,String prompt,String path){return route(name,command,prompt,"查看我的服务申请",path);}
 static Route topic(String text){
  if(text.matches(".*(陪产假|育儿假|丧假).*"))return help(text.contains("陪产假")?"陪产假":text.contains("育儿假")?"育儿假":"丧假");
  if(text.contains("产假")||text.contains("生育假"))return leave("产假");
  if(text.contains("年假")||text.contains("年休假"))return leave("年假");
  if(text.contains("病假"))return leave("病假");if(text.contains("婚假"))return leave("婚假");if(text.contains("事假"))return leave("事假");
  if(text.contains("请假")||text.contains("休假")||text.contains("假期"))return leave("请假");
  if(text.contains("离职证明"))return service("离职证明","我要补发离职证明","先说明是首次申请、补发还是更正，我会逐项帮你填写。",null);
  if(text.contains("劳动关系证明")||text.contains("工作经历证明"))return service("劳动关系证明","我要申请劳动关系证明","先确认需要哪种证明，再填写用途和接收要求。",null);
  if(text.contains("收入证明"))return service("收入证明","我要开收入证明","先告诉我证明的用途，再补充抬头等要求，核对后交给 HR 审核。","/certificates");
  if(text.contains("签证")&&text.contains("证明"))return service("签证在职证明","我要开出国签证在职证明","先确认目的国家，再逐项整理证明用途、语言和抬头。","/certificates");
  if(text.contains("在职证明")||text.contains("工作证明"))return service("在职证明","我要开在职证明","先告诉我证明用来做什么，我再陪你填写语言和其他要求。","/certificates");
  if(text.contains("证明"))return route("证明",null,"你需要在职证明、收入证明，还是离职证明呢？","查看我的服务申请","/certificates");
  if(text.contains("社保")||text.contains("公积金"))return service("社保公积金","我要咨询社保公积金","先确认办理城市，再说一下转移、缴纳或其他需求，我帮你整理给 HRSSC。",null);
  if(text.contains("合同"))return service("合同事项","我要咨询合同签署","先确认首次签署、续签还是更正，再把需要协助的问题整理给 HRSSC。",null);
  if(text.contains("入职")&&!text.contains("离职"))return service("入职材料核验","我要办理入职材料核验","先填写预计报到日期，再一起检查材料准备情况。","/onboarding");
  if(text.contains("结算"))return service("离职结算核对","我要核对离职结算","先填写离职日期，再列出你希望核对的项目和疑问。",null);
  if(text.contains("档案"))return service("档案咨询","我要调取档案","先确认档案类型，再说明用途和接收单位。",null);
  if(text.contains("离职"))return service("离职申请","我要申请离职","如果你已经决定申请，我可以先帮你填写计划日期、说明和交接安排；最后由你确认是否提交。",null);
  if(text.matches(".*(人事变更|修改.*信息|联系方式|住址|个人资料|个人信息).*"))return service("人事变更","我要办理人事变更","先选择要变更的信息，再填写新内容和期望生效日期。","/personal-info");
  if(text.matches(".*(报销|借款|差旅|出差|住宿|加班|调休|福利).*"))return help(text.contains("借款")?"借款":text.contains("报销")?"报销":text.contains("差旅")||text.contains("出差")||text.contains("住宿")?"出差":text.contains("加班")||text.contains("调休")?"加班调休":"福利");
  if(text.matches(".*(申诉|举报|投诉).*"))return route("申诉",null,"可以打开申诉入口，选择实名或匿名，再按页面提示整理事实和材料。填好并核对后，由你提交。","查看我的工单","/employee-experience?section=compliance");
  if(text.matches(".*(工作计划|本周任务|每日任务|每周任务).*"))return route("工作计划","查看我的工作计划","我可以先查你的任务清单，再选择一项继续处理。","查看我的工作计划","/employee-experience?section=onboarding");
  if(text.contains("提醒")||text.contains("待办"))return route("提醒待办","查看我的提醒","我可以先查当前提醒，你再选择要处理的那一项。","查看我的提醒","/employee-experience?section=reminders");
  if(text.contains("工资条"))return route("工资条","查询我的工资条","我可以查询 HR 已发布的工资条。","查询我的工资条",null);
  return null;
 }
 private static Route help(String name){return service(name,"我要办理"+name+"协助","我可以先帮你整理"+name+"事项和需要协助的地方，确认后提交 HRSSC，由经办人员告知具体入口、材料和后续安排。",null);}
 private static String clean(String s){return s.replaceAll("[\\s，,。！？!?]","");}
 private static boolean safe(String s){return ErSafety.risk(s).equals("NONE")&&!CareConversationGuide.isCareTurn(s)&&!LifecycleService.cancellationRequested(s);}
 static boolean start(String s){return clean(s).matches("^(?:那|那就|现在)?(?:帮我|替我|我要|我想)?(?:开始|继续)?(?:申请|办理|填写|填一下|填表|办一下)(?:吧|呀|一下)?$")||Set.of("开始吧","开始申请吧","帮我填","帮我填写吧","下一步开始填写").contains(clean(s));}
 static boolean how(String s){String q=clean(s);return q.matches(".*(?:怎么|如何|怎样|哪里|哪儿).*(?:申请|办理|提交|填写|操作).*|.*(?:申请|办理|填写).*(?:流程|步骤|入口).*")||Set.of("下一步","下一步呢","然后呢","接下来呢","接下来怎么办","申请方式","申请流程","需要什么手续").contains(q);}
 static boolean progress(String s){return clean(s).matches("^(?:那|我的|这个|这份)?(?:查进度|查询进度|查看进度|审核进度|审批进度|审核结果|申请进度|进度呢|批了吗|审核到哪了|审批到哪了|办好了吗|有结果了吗|现在什么进度)$")||topic(s)!=null&&clean(s).matches(".*(?:申请进度|审批进度|审核进度|批了吗|有结果了吗|办好了吗|审核结果).*");}
 private static boolean neutral(String s){return clean(s).matches("^(?:请问|我|那我|这个|那|接下来)?(?:应该|该|要|可以)?(?:怎么|如何|怎样|在哪里|在哪儿)(?:申请|办理|提交|填写|操作)(?:呢|呀|啊)?$")||Set.of("下一步","下一步呢","然后呢","接下来呢","接下来怎么办","申请方式","申请流程","需要什么手续").contains(clean(s));}
 private Key key(UserAccount u,String cid){return cid==null||cid.isBlank()?null:new Key(u.getTenantId(),u.getId(),cid);}
 private Context get(UserAccount u,String cid){var k=key(u,cid);var c=k==null?null:contexts.get(k);if(c!=null&&c.expires<=clock.millis()){contexts.remove(k);return null;}return c;}
 void clear(UserAccount u,String cid){var k=key(u,cid);if(k!=null)contexts.remove(k);}
 Optional<String> command(UserAccount u,String raw,String cid){
  if(!safe(raw))return Optional.empty();var c=get(u,cid);var explicit=topic(raw);
  var intent=EmployeeIntentUnderstanding.analyze(raw);
  var direct=EmployeeIntentUnderstanding.command(intent);
  if(direct!=null)return Optional.of(direct);
  if(c!=null&&intent.action()==EmployeeIntentUnderstanding.Action.DOWNLOAD&&c.route.name.contains("证明"))return Optional.of("下载证明");
  if(c!=null&&intent.action()==EmployeeIntentUnderstanding.Action.BALANCE&&c.route.name.equals("年假"))return Optional.of("查询我的年假余额");
  if(c!=null&&intent.action()==EmployeeIntentUnderstanding.Action.PROGRESS&&intent.topics().isEmpty())return Optional.of(c.route.progress);
  if(progress(raw)&&explicit!=null)return Optional.of(explicit.progress);
  if(c==null)return Optional.empty();
  if(progress(raw))return Optional.of(c.route.progress);
  if(c.draft&&(start(raw)||how(raw)&&neutral(raw)))return Optional.of("继续办理");
  if(start(raw)&&c.route.command!=null)return Optional.of(c.route.command);
  return Optional.empty();
 }
 Optional<MessageResponse> guidance(UserAccount u,String raw,String cid){
  if(!safe(raw)||!(how(raw)||start(raw)||progress(raw)))return Optional.empty();
  var c=get(u,cid);var explicit=topic(raw);Route r=explicit!=null?explicit:c==null?null:c.route;
  if(c!=null&&c.draft&&clean(raw).equals("继续办理"))return Optional.empty();
  // A named, unrelated service must never inherit the previous leave/benefit topic.
  if(explicit==null&&!neutral(raw)&&!start(raw)&&!progress(raw))return Optional.empty();
  if(progress(raw)&&r==null)return Optional.of(response("我来帮你查最新情况。你想看请假审批，还是其他服务申请的进度呢？",List.of(say("请假审批","查看我的请假"),say("其他服务申请","查看我的服务申请"))));
  if(r==null)return Optional.of(response("可以呀，我陪你一步步来。你想办理哪件事呢？选好后，我们接着填写。",List.of(say("申请请假","我要申请请假"),say("开在职证明","我要开在职证明"),say("其他员工服务","查看所有员工服务"))));
  var actions=new ArrayList<ChatAction>();
  if(r.command!=null)actions.add(say(r.name.equals("产假")?"开始产假申请":"继续办理"+r.name,r.command));
  if(r.name.equals("证明")){actions.add(say("在职证明","我要开在职证明"));actions.add(say("收入证明","我要开收入证明"));actions.add(say("离职证明","我要补发离职证明"));}
  if(r.path!=null)actions.add(LifecycleNavigation.page("打开"+r.name+"页面",r.path));
  if(r.path==null)actions.add(LifecycleNavigation.records());
  save(u,cid,r,false);
  return Optional.of(response("好呀，我们接着看"+r.name+" 🌷\n\n"+r.prompt+(r.command==null?"":"点下方按钮就能继续，也可以直接说‘帮我填写’。"),actions));
 }
 void observe(UserAccount u,String raw,String cid,MessageResponse response){
  if(!safe(raw)||response.provider()!=null&&(response.provider().equals("er-human-support")||response.provider().equals("workplace-support"))){clear(u,cid);return;}
  var previous=get(u,cid);var route=topic(raw);var actions=response.actions()==null?List.<ChatAction>of():response.actions();
  if(previous!=null&&previous.draft&&"hrssc-lifecycle".equals(response.provider()))route=previous.route;
  if(route==null&&previous!=null&&(how(raw)||start(raw)||progress(raw)||"hrssc-lifecycle".equals(response.provider())||response.provider()!=null&&response.provider().matches(".*(policy|knowledge|maternity|annual|conversation-guide).*")))route=previous.route;
  if(route==null&&previous!=null&&EmployeeIntentUnderstanding.question(raw))route=previous.route;
  if(route==null){clear(u,cid);return;}
  boolean draft="hrssc-lifecycle".equals(response.provider())&&actions.stream().anyMatch(a->a.value().equals("取消办理"))
      ||previous!=null&&previous.draft&&previous.route.equals(route)&&!"hrssc-lifecycle".equals(response.provider());
  save(u,cid,route,draft);
 }
 private void save(UserAccount u,String cid,Route route,boolean draft){var k=key(u,cid);if(k==null)return;contexts.entrySet().removeIf(e->e.getValue().expires<=clock.millis());if(contexts.size()>=2000)contexts.clear();contexts.put(k,new Context(route,draft,clock.millis()+900000));}
 private static ChatAction say(String label,String value){return LifecycleNavigation.say(label,value);}
 private static MessageResponse response(String text,List<ChatAction> actions){return new MessageResponse(text,"service-conversation-guide",UUID.randomUUID().toString(),actions);}
}
