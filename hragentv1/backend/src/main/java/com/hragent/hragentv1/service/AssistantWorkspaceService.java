package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.WebChatDtos.*;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.TaskAction;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import java.util.*;

/** Employee operations reuse the same authorized services as the workbench. */
@Service
public class AssistantWorkspaceService {
 private final EmployeeRelationsService relations;
 private final EmployeeReminderService reminders;
 private final EmployeePersonalProfileService profiles;
 private final LeaveService leaves;
 public AssistantWorkspaceService(EmployeeRelationsService relations,EmployeeReminderService reminders,EmployeePersonalProfileService profiles,LeaveService leaves){this.relations=relations;this.reminders=reminders;this.profiles=profiles;this.leaves=leaves;}
 private static ChatAction panel(String title,String path){return new ChatAction(title,"panel",path);}
 private static ChatAction say(String title,String value){return new ChatAction(title,"message",value);}
 private static Optional<MessageResponse> answer(String text,List<ChatAction> actions){return Optional.of(new MessageResponse(text,"assistant-workspace",UUID.randomUUID().toString(),actions));}
 private static boolean is(String t,String... values){return Arrays.asList(values).contains(t);}
 public Optional<MessageResponse> reply(UserAccount u,String raw){
  String t=raw.trim().replaceAll("[。！？!?]$","");
  t=t.replaceFirst("^请?(帮我|给我)","");
  if(is(t,"查看所有员工服务","所有功能","工作台功能","你能做什么","能做什么","我要办事","员工服务")){
   if(LifecycleService.former(u))return answer(LifecycleNavigation.guide("离职后"),LifecycleNavigation.choices("离职后"));
   if(u.getRole()==Role.NEW_HIRE)return answer("我可以陪你完成入职登记、材料核验和合同咨询。点击下方选项即可在这里继续办理。",LifecycleNavigation.choices("入职"));
   var options=new ArrayList<>(List.of(say("申请请假","我要申请请假"),say("请假记录与余额","查看我的请假"),say("开具证明","我要开在职证明"),panel("证明管理与下载","/certificates"),say("我的个人信息","查看我的个人信息"),say("人事变更申请","我要办理人事变更"),panel("组织通讯录","/directory"),say("查询工资条","查询我的工资条"),panel("制度查询","/employee-experience?section=policy"),say("我的工作计划","查看我的工作计划"),say("提醒与待办","查看我的提醒"),panel("办事材料清单","/employee-experience?section=guides"),panel("入职登记","/onboarding"),panel("支持与关怀","/employee-experience?section=support"),panel("合规申诉与回复","/employee-experience?section=compliance"),say("离职及离职后服务","离职"),panel("账号设置","/account"),panel("企业空间","/workspace"),new ChatAction("我的服务单","requests","")));
   if(u.getRole()==Role.MANAGER)options.add(panel("主管审批","/manager-approval"));
   if(u.getRole()==Role.HR){options.add(panel("HR 服务处理","/hrssc"));options.add(panel("HR 备案","/hr-record"));options.add(panel("员工信息管理","/employees"));options.add(panel("知识库维护","/knowledge"));}
   return answer("直接告诉我你想办什么。我能逐项帮你填写申请、查询记录和进度，也能在当前页面打开资料、附件或业务表单。\n\n请假和证明等申请由你确认后提交；每周任务和提醒也能在对话中处理。所有操作沿用你的账号权限，审批结果与工作台同步。",options);
  }
  if(u.getRole()==Role.NEW_HIRE && is(t,"填写入职登记","入职登记","我要填写入职登记"))return answer("在这里打开入职登记，填写后核对并提交给 HR。",List.of(panel("填写入职登记","/onboarding")));
  if(LifecycleService.former(u)||u.getRole()==Role.NEW_HIRE)return Optional.empty();
  if(t.matches(".*(年假|年休假).*(余额|剩余|还剩|可用|总额|核定额度).*" )||t.matches(".*(余额|剩余|还剩|可用|总额|核定额度).*(年假|年休假).*")){
   var b=leaves.agentBalances(u).stream().filter(v->v.leaveType().equals("ANNUAL")).findFirst();
   if(b.isEmpty())return answer("暂时没查到系统给你核定的年假额度，需要 HR 补充后才能确认。我先不把它当作零天。",List.of(panel("打开请假记录","/my-leave")));
   var v=b.get();return answer("系统目前给你核定的年假总额度是 "+v.totalDays().stripTrailingZeros().toPlainString()+" 天，已休 "+v.usedDays().stripTrailingZeros().toPlainString()+" 天。"+(v.reservedDays().signum()>0?"还有 "+v.reservedDays().stripTrailingZeros().toPlainString()+" 天正在审批中。":"")+"现在可申请 **"+v.availableDays().max(java.math.BigDecimal.ZERO).stripTrailingZeros().toPlainString()+" 天**。\n\n需要我陪你把请假申请填好吗？",List.of(say("开始请假","我要休年假"),panel("查看请假记录","/my-leave")));
  }
  if(t.matches(".*(查看|查询|看).*(个人信息|个人资料|个人档案).*"))t="查看我的个人信息";
  if(t.matches(".*请假.*(记录|状态|进度|审核|批了吗|批准|结果).*"))t="查看我的请假";
  if(is(t,"审核结果","查看审核结果","查询审核结果","我的请假批了吗","请假审核完了吗","查进度","查询进度","查看进度","审核进度","审批进度","批了吗","审核到哪了","审批到哪了"))t="查看我的请假";
  if(t.matches(".*(查看|打开|处理).*(工作计划|本周任务|每周任务).*"))t="查看我的工作计划";
  if(!t.matches(".*[0-9].*") && t.matches(".*(查看|打开|处理).*提醒.*"))t="查看我的提醒";
  if(t.matches(".*(通讯录|找同事).*"))t="通讯录";
  if(is(t,"查看我的个人信息","我的个人信息","查看个人档案","我的档案")){
   var p=profiles.mine(u);return answer("你的员工信息：\n- 姓名："+p.displayName()+"\n- 工号："+p.employeeNo()+"\n- 部门："+p.department()+"\n- 岗位："+p.title()+"\n- 入职日期："+p.entryDate()+"\n\n完整资料可在下方查看。需要修改人事信息时，我可以先帮你填写变更申请。",List.of(panel("查看个人资料","/personal-info"),say("申请修改信息","我要办理人事变更")));
  }
  if(is(t,"查看我的请假","我的请假","请假记录","查看请假记录","查看我的假期")){
var rows=leaves.mine(u);return answer(rows.isEmpty()?"你还没有请假申请。可以告诉我假别和日期，或点击下方开始填写。":"我查到的最新请假进度：\n"+String.join("\n",rows.stream().limit(5).map(r->"- #"+r.id()+" · "+r.leaveTypeLabel()+" · "+r.startDate()+" 至 "+r.endDate()+" · "+r.statusLabel()+(r.hrOpinion()!=null?"\n  HR 回复："+r.hrOpinion():r.managerOpinion()!=null?"\n  主管回复："+r.managerOpinion():"")).toList())+"\n\n有新进度时，Kaka 会给你消息提醒。你也可以随时问我‘请假批了吗’，我会重新查询。",List.of(say("对话申请请假","我要申请请假"),say("查询假期余额","查询我的年假余额"),panel("查看记录与日历","/my-leave")));
  }
  if(is(t,"查看我的工作计划","我的工作计划","每周任务","本周任务","工作计划","入职任务"))return journey(u);
  if(t.matches("^(标记|确认完成|重新打开)任务 [a-z0-9-]{1,60}$")){
   String key=t.substring(t.indexOf(' ')+1);var task=relations.journey(u).stream().filter(r->r.key().equals(key)).findFirst().orElseThrow(()->AppException.badRequest("该任务不属于当前清单，请刷新工作计划"));
   if(t.startsWith("标记"))return answer("确认将“"+task.title()+"”标记为已完成吗？这只更新你的个人计划进度。",List.of(say("确认完成","确认完成任务 "+key),say("暂不修改","查看我的工作计划")));
   relations.setTask(u,key,!t.startsWith("重新打开"));return answer("已更新“"+task.title()+"”的进度，与工作计划同步。",List.of(say("查看工作计划","查看我的工作计划"),say(t.startsWith("重新打开")?"标记完成":"撤回完成标记",(t.startsWith("重新打开")?"标记任务 ":"重新打开任务 ")+key)));
  }
  if(is(t,"查看我的提醒","我的提醒","提醒与待办","处理提醒"))return reminderList(u);
  if(t.matches("^(处理|确认完成)提醒 [0-9]{1,15}$")){
   Long id=Long.valueOf(t.substring(t.indexOf(' ')+1));var row=reminders.mine(u).stream().filter(r->r.getId().equals(id)).findFirst().orElseThrow(()->AppException.notFound("提醒不存在"));
   if(t.startsWith("处理"))return answer("将“"+row.getTitle()+"”标记为已处理吗？这不会替你完成预约、续签或其他实际业务。",List.of(say("确认已处理","确认完成提醒 "+id),say("返回提醒","查看我的提醒")));
   reminders.update(u,id,new TaskAction("DONE",null));return answer("已将提醒标记为已处理。",List.of(say("查看其他提醒","查看我的提醒")));
  }
  Map<String,String[]> panels=Map.ofEntries(
   Map.entry("通讯录",new String[]{"组织通讯录","/directory"}),Map.entry("查找同事",new String[]{"组织通讯录","/directory"}),
   Map.entry("修改我的个人资料",new String[]{"个人资料","/personal-info"}),Map.entry("下载证明",new String[]{"证明管理与下载","/certificates"}),Map.entry("上传证明模板",new String[]{"证明模板与附件","/certificates"}),
   Map.entry("填写入职登记",new String[]{"入职登记","/onboarding"}),Map.entry("我要申诉",new String[]{"合规申诉","/employee-experience?section=compliance"}),Map.entry("我要举报",new String[]{"合规申诉","/employee-experience?section=compliance"}),Map.entry("查看我的工单",new String[]{"我的工单与回复","/employee-experience?section=compliance"}),Map.entry("填写员工反馈",new String[]{"员工体验反馈","/employee-experience?section=support"}),Map.entry("查看关怀资源",new String[]{"支持与关怀","/employee-experience?section=support"}),Map.entry("生成办事材料清单",new String[]{"办事材料清单","/employee-experience?section=guides"}),Map.entry("修改密码",new String[]{"账号设置","/account"}),Map.entry("切换企业空间",new String[]{"企业空间","/workspace"}));
  if(panels.containsKey(t)){var p=panels.get(t);return answer("可以在当前对话旁打开“"+p[0]+"”。填写、上传和确认操作都在这里完成，结果保存在同一系统中。",List.of(panel(p[0],p[1])));}
  return Optional.empty();
 }
 private Optional<MessageResponse> journey(UserAccount u){var tasks=relations.journey(u).stream().filter(t->!t.phase().equals("离职准备")).toList();var actions=new ArrayList<ChatAction>();for(var t:tasks)if(!t.done())actions.add(say("完成："+t.title(),"标记任务 "+t.key()));actions.add(panel("打开完整工作计划","/employee-experience?section=onboarding"));return answer("你的工作计划：\n"+String.join("\n",tasks.stream().map(t->"- "+(t.done()?"已完成":"待完成")+" · "+t.title()).toList())+"\n\n做完一项后，可以选择下方按钮记录进度。",actions);}
 private Optional<MessageResponse> reminderList(UserAccount u){var rows=reminders.mine(u).stream().filter(r->r.getStatus().equals("OPEN")).toList();var actions=new ArrayList<ChatAction>();rows.stream().limit(10).forEach(r->actions.add(say("处理："+r.getTitle(),"处理提醒 "+r.getId())));actions.add(panel("查看及调整提醒","/employee-experience?section=reminders"));return answer(rows.isEmpty()?"当前没有待处理的提醒。":"待处理提醒：\n"+String.join("\n",rows.stream().limit(10).map(r->"- "+r.getTitle()+" · "+r.getDueDate()+" · "+r.getDescription()).toList()),actions);}
}
