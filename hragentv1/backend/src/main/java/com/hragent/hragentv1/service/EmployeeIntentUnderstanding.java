package com.hragent.hragentv1.service;

import com.hragent.hragentv1.dto.WebChatDtos.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Pure interpretation: keywords select a topic, verbs select an operation. Never submits data. */
public final class EmployeeIntentUnderstanding {
 private EmployeeIntentUnderstanding() {}
 public enum Action { CANCEL, CARE, PROGRESS, DOWNLOAD, BALANCE, POLICY, HOW, START, LOOKUP, TOPIC, UNKNOWN }
 public record Understanding(String text,Action action,List<String> topics,List<String> keywords,boolean negated) {}
 private record Term(String keyword,String topic) {}
 private static final List<Term> TERMS=new ArrayList<>();
 static {
  terms("年假","年休假","带薪年假","年假");terms("病假","病假");terms("事假","事假");terms("婚假","婚假");
  terms("陪产假","陪产假","护理假");terms("产假","生育假","产假");terms("育儿假","育儿假");terms("丧假","丧假");terms("请假","请假","休假","假期");
  terms("离职证明","离职证明");terms("劳动关系证明","劳动关系证明","工作经历证明");terms("收入证明","收入证明","薪资证明");terms("在职证明","在职证明","工作证明","任职证明");terms("证明","证明");
  terms("报销","费用报销","报账","报销");terms("出差","出差","差旅","住宿");terms("借款","借款");terms("加班调休","加班","调休");
  terms("社保公积金","住房公积金","公积金","社保","五险一金");terms("合同","劳动合同","合同","续签");terms("离职结算","离职结算","结算");terms("离职","离职","辞职");terms("入职","入职","报到");
  terms("个人信息","个人信息","个人资料","联系方式","手机号","手机号码","住址");terms("档案","档案");terms("工资条","工资条","薪资明细","工资明细");terms("提醒","待办","提醒");terms("工作计划","工作计划","本周任务","每周任务","每日任务");terms("申诉","举报","投诉","申诉");terms("福利","年金","福利");
  TERMS.sort(Comparator.comparingInt((Term t)->t.keyword.length()).reversed());
 }
 private static void terms(String topic,String... words){for(String word:words)TERMS.add(new Term(word,topic));}
 public static Understanding analyze(String raw){
  String text=Normalizer.normalize(raw==null?"":raw,Normalizer.Form.NFKC).trim();
  String compact=text.replaceAll("\\s+","");
  boolean[] used=new boolean[text.length()];var topics=new LinkedHashSet<String>();var keywords=new LinkedHashSet<String>();
  for(var term:TERMS){int from=0,index;while((index=text.indexOf(term.keyword,from))>=0){boolean free=true;for(int i=index;i<index+term.keyword.length();i++)if(used[i])free=false;
   if(free){topics.add(term.topic);keywords.add(term.keyword);Arrays.fill(used,index,index+term.keyword.length(),true);}from=index+term.keyword.length();}}
  if(topics.size()>1){if(topics.stream().anyMatch(t->t.endsWith("假")&&!t.equals("请假")))topics.remove("请假");if(topics.stream().anyMatch(t->t.endsWith("证明")&&!t.equals("证明"))||text.matches(".*(?:病历|诊断|医疗)证明.*"))topics.remove("证明");if(topics.contains("报销"))topics.remove("出差");}
  boolean negative=compact.matches(".*(?:不想|不要|不用|不需要|暂不|先不|不是要|不打算)(?:再|帮我|替我)?(?:申请|办理|开具|开|请|休|提交|修改|变更).*" );
  Action action;
  if(LifecycleService.cancellationRequested(text))action=Action.CANCEL;
  else if(!ErSafety.risk(text).equals("NONE")||CareConversationGuide.isCareTurn(text))action=Action.CARE;
  else if(matches(compact,"进度|状态|批了吗|批了没|通过了吗|审核完|审批完|审核结果|审批结果|办好了吗|办好了没|办到哪|到哪一步|处理到哪|有结果了吗"))action=Action.PROGRESS;
  else if(matches(compact,"下载|打不开|无法打开|没法打开|无法查看|看不了|预览"))action=Action.DOWNLOAD;
  else if(matches(compact,"余额|剩余|还剩|还可以休|还能休"))action=Action.BALANCE;
  else if(matches(compact,"政策|制度|规定|条例|标准|条件|规则|原文|多少天|几天|天数|工龄|有效期|期限|材料|了解|什么要求|能不能|可不可以|可以吗|能否"))action=Action.POLICY;
  else if(ServiceConversationGuide.how(text))action=Action.HOW;
  else if(matches(compact,"查看|查询|查一下|查查|看看|看一下|打开|在哪"))action=Action.LOOKUP;
  else if(!negative&&(ServiceConversationGuide.start(text)||matches(compact,"(?:我要|我想|我需要|帮我|替我|给我|麻烦|想|需要).*(?:申请|办理|开具|开一|开个|开在职|开工作|开收入|开签证|开出国|开任职|开证明|办一|办个|请|休|报销|报账|补发|补办|续签|变更|修改|更新|离职|辞职|转移|调取)|^(?:申请|办理|开具|补发|补办|续签|报销|报账)")))action=Action.START;
  else action=topics.isEmpty()?Action.UNKNOWN:Action.TOPIC;
  return new Understanding(text,action,List.copyOf(topics),List.copyOf(keywords),negative);
 }
 private static boolean matches(String text,String expression){return Pattern.compile(expression).matcher(text).find();}
 public static boolean question(String text){
  var a=analyze(text).action;
  if(a==Action.POLICY)return matches(text,"政策|制度|规定|条例|标准|条件|规则|原文|多少|几天|天数|工龄|有效期|期限|了解|什么|哪些|能不能|可不可以|可以吗|能否|[?？]");
  return Set.of(Action.HOW,Action.PROGRESS,Action.DOWNLOAD,Action.BALANCE,Action.LOOKUP).contains(a)||text.matches(".*[?？]$");
 }
 static ServiceConversationGuide.Route route(String topic){return ServiceConversationGuide.topic(topic);}
 public static Optional<MessageResponse> clarification(String raw){
  var u=analyze(raw);if(u.action==Action.CARE||u.action==Action.CANCEL)return Optional.empty();
  if(u.topics.size()>1){
   var actions=new ArrayList<ChatAction>();for(var topic:u.topics.stream().limit(5).toList()){
    var r=route(topic);if(r==null)continue;
    String command=switch(u.action){case PROGRESS->r.progress();case DOWNLOAD->topic.contains("证明")?"下载证明":"我想了解"+topic;case START->r.command();default->"我想了解"+topic;};
    if(command!=null)actions.add(LifecycleNavigation.say(topic,command));
   }
   if(!actions.isEmpty())return Optional.of(response("我听到你提到了"+String.join("、",u.topics)+"。我们一件件来呀，你想先处理哪一件？",actions));
  }
  if(u.negated&&!question(raw))return Optional.of(response("好呀，先不发起申请。你可以先了解相关要求，想办理时再告诉我。",u.topics.stream().limit(3).map(t->LifecycleNavigation.say("了解"+t,"我想了解"+t)).toList()));
  return Optional.empty();
 }
 static String command(Understanding u){
  if(u.topics.size()!=1||u.action==Action.CARE)return null;String topic=u.topics.getFirst();var r=route(u.text);if(r==null)r=route(topic);if(r==null)return null;
  return switch(u.action){
   case PROGRESS->r.progress();
   case DOWNLOAD->topic.contains("证明")?"下载证明":null;
   case BALANCE->topic.equals("年假")?"查询我的年假余额":null;
   case LOOKUP->topic.contains("证明")?"下载证明":topic.endsWith("假")?r.progress():switch(topic){case "个人信息"->"查看我的个人信息";case "工资条"->"查询我的工资条"+(u.text.matches(".*[0-9].*")?"，"+u.text:"");case "工作计划"->"查看我的工作计划";case "提醒"->"查看我的提醒";default->null;};
   case START->{
    if(u.negated)yield null;
    // Leave dates stay in the utterance so the existing date parser can propose them for confirmation.
    if(r.command()!=null&&topic.endsWith("假")&&!Set.of("育儿假","陪产假","丧假").contains(topic))yield r.command()+"，"+u.text;
    yield r.command();
   }
   default->null;
  };
 }
 private static MessageResponse response(String text,List<ChatAction> actions){return new MessageResponse(text,"intent-guidance",UUID.randomUUID().toString(),actions);}
 public static MessageResponse nextStep(String message){
  var u=analyze(message);var actions=new ArrayList<ChatAction>();
  if(u.topics.size()==1){String topic=u.topics.getFirst();var r=route(topic);
   actions.add(LifecycleNavigation.say("了解办理要求",topic+"需要什么材料"));
   if(r!=null&&r.command()!=null)actions.add(LifecycleNavigation.say("开始办理",r.command()));
   if(r!=null&&r.path()!=null)actions.add(LifecycleNavigation.page("打开办理页面",r.path()));
   return response("我听到你想聊"+topic+"，不过还没确定你最需要哪一步。是先了解要求，还是现在开始办理呢？",actions);
  }
  return response("我在呀。你可以告诉我想办的事，比如‘帮我请假’‘开在职证明’，也可以说说遇到的具体问题。我们一步步来。",List.of(LifecycleNavigation.say("办理员工服务","查看所有员工服务"),LifecycleNavigation.say("查询申请进度","查进度"),LifecycleNavigation.say("聊聊最近的状态","我想找人聊聊")));
 }
}
