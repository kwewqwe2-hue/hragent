package com.hragent.hragentv1.service;
import com.hragent.hragentv1.dto.WebChatDtos.ChatAction;
import java.util.*;

/** Explicit navigation choices, never generated URLs or executable model output. */
final class LifecycleNavigation {
 private LifecycleNavigation(){}
 static ChatAction say(String label,String message){return new ChatAction(label,"message",message);}
 static ChatAction page(String label,String path){return new ChatAction(label,"workbench",path);}
 static ChatAction records(){return new ChatAction("查看我的服务单","requests","");}
 static String stage(String text){return switch(text.replaceAll("[\\s。！!？?]","")){
  case "入职","新员工","我是新员工","入职服务","入职办理"->"入职";
  case "在职","我在职","我是在职员工","在职服务","在职办理"->"在职";
  case "离职","离职服务","离职办理"->"离职";
  case "离职后","已离职","我已离职","离职后服务"->"离职后";
  default->null;};}
 static List<ChatAction> stages(boolean former){return former?List.of(say("离职后服务","离职后"),records()):List.of(say("入职服务","入职"),say("在职服务","在职"),say("离职服务","离职"),say("离职后服务","离职后"),records());}
 static String guide(String stage){return switch(stage){
  case "入职"->"我们先从入职事项开始。\n\n1. 确认报到安排并准备材料。\n2. 前往入职登记填写资料，或让我逐项整理材料准备情况。\n3. 核对后提交，等待 HR 核验；合同问题可以另行发起咨询。\n\n选择下方事项即可继续。";
  case "在职"->"请假、开证明、查工资条或修改人事信息，我都可以帮你。\n\n你这次想先办哪件事？选好后，我们一步步填写，最后由你核对并确认提交。";
  case "离职"->"离职相关事项可以按这个顺序准备：\n\n1. 如已决定申请离职，我可以整理预计日期、说明及交接安排。\n2. 按交接清单核对工作、设备与资料。\n3. 向 HR 核对结算并申请所需证明。\n\n查看这些指引不会提交辞职，也不会停用账号。你想先了解还是开始填写？";
  default->"离职后仍可以办理证明补发、档案咨询、劳动关系证明及结算核对。\n\n1. 选择事项，我会逐项询问用途和要求。\n2. 你核对并确认提交后，HR 再核验资料。\n3. 在“我的服务单”查看回复、补充材料和下载交付文件。\n\n请选择下方事项。";};}
 static List<ChatAction> choices(String stage){return switch(stage){
  case "入职"->List.of(page("前往入职登记","/onboarding"),say("对话填写材料核验","我要办理入职材料核验"),say("合同签署咨询","我要咨询合同签署"));
  case "在职"->List.of(say("对话申请请假","我要申请请假"),page("前往请假填写","/my-leave"),say("开在职证明","我要开在职证明"),say("开收入证明","我要开收入证明"),say("签证在职证明","我要开出国签证在职证明"),page("前往证明填写","/certificates"),say("人事信息变更","我要办理人事变更"),say("查询工资条","查询我的工资条"),say("社保公积金咨询","我要咨询社保公积金"));
  case "离职"->List.of(say("对话填写离职申请","我要申请离职"),say("查看交接清单","查看交接清单"),say("核对离职结算","我要核对离职结算"),say("申请离职证明","我要申请离职证明"));
  default->List.of(say("补发离职证明","补发离职证明"),say("档案调取咨询","我要调取档案"),say("劳动关系证明","我要申请劳动关系证明"),say("核对结算","我要核对离职结算"),say("社保公积金咨询","我要咨询社保公积金"),records());};}
 static String shortIntent(String text){return switch(text){case "请假","申请请假"->"LEAVE";case "人事变更","人事信息变更"->"CHANGE";case "社保公积金","社保公积金咨询"->"SOCIAL";case "入职材料核验"->"ONBOARDING";case "离职申请"->"EXIT";case "离职结算核对"->"SETTLEMENT";case "离职证明"->"EXIT_CERT";case "档案调取"->"ARCHIVE";case "劳动关系证明"->"RELATION_CERT";default->null;};}
 static Optional<ChatAction> formPage(String kind){return switch(kind){case "LEAVE"->Optional.of(page("也可前往请假页面填写","/my-leave"));case "EMPLOYMENT_CERT","INCOME_CERT","VISA_CERT"->Optional.of(page("也可前往证明页面填写","/certificates"));case "ONBOARDING"->Optional.of(page("前往入职登记","/onboarding"));default->Optional.empty();};}
}
