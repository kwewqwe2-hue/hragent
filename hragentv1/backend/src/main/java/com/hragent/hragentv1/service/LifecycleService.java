package com.hragent.hragentv1.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.dto.LeaveDtos;
import com.hragent.hragentv1.dto.WebChatDtos;
import com.hragent.hragentv1.dto.WebChatDtos.ChatAction;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Authenticated, local conversation forms. No model output can approve a request. */
@Service
public class LifecycleService {
 public record Form(String label, List<String> fields, List<String> questions) {}
 static final Map<String,Form> FORMS = new LinkedHashMap<>();
 static {
  form("ONBOARDING","入职材料核验","报到日期|材料准备情况|需要协助的事项","预计哪天报到？请用 2026-09-15 这样的日期。|身份证明、学历证明、照片及银行卡信息分别准备好了吗？只说明准备情况，不必发送完整证件号码。|还需要 HR 协助什么？没有可以回复‘无’。");
  form("CONTRACT","合同签署咨询","合同事项|具体问题|期望处理方式","你想咨询首次签署、续签还是合同更正？|哪一项条款或签署步骤需要 HR 核实？|希望收到文字答复、电子签署指引，还是更正后的合同？");
  form("LEAVE","假期申请","假别|开始日期|结束日期|请假原因","你要请哪种假？例如年假、病假、事假。|从哪天开始？可以说‘9月15日’或‘明天’，也可以一次告诉我‘9.15-9.17’。|到哪天结束呢？可以说‘9月17日’，也可以直接告诉我整段时间。|请简要说明请假原因。");
  form("MATERNITY_LEAVE","产假申请","假别|开始日期|结束日期|请假原因","这份申请用于产假。|你计划从哪天开始休产假呢？可以说‘9月15日’，也可以一次告诉我整段日期，我会再和你确认。|计划休到哪天呢？先填你的计划，具体安排由 HRSSC 结合材料核验。|简单说一下这次申请的安排就好，不需要在聊天里填写详细病历。");
  form("SERVICE_HELP","员工事项办理协助","办理事项|具体需求|期望处理方式","你希望办理什么事项呢？|请简单说明你要办的事和目前卡住的地方，我会整理给 HRSSC。|希望收到办理入口、材料清单，还是经办人员的答复？");
  form("CHANGE","人事变更","变更项目|变更内容|生效日期","你要变更联系方式、住址、部门、岗位，还是其他信息？|请说明需要修改的内容。银行账户、证件等敏感资料请先说明变更事项，由 HR 指引安全补充。|希望从哪天生效？请填写完整日期。");
  form("SOCIAL","社保公积金咨询","办理城市|咨询事项|具体情况","要咨询哪个城市的社保或公积金？|想查缴纳情况、转移、补缴，还是其他事项？|请简要说明目前情况和需要 HR 核实的问题。");
  form("EXIT","离职申请","预计离职日期|离职说明|交接安排","你计划哪天离职？请填写完整日期，最终时间由 HR 与你确认。|请简要说明离职事项。|目前有哪些工作、设备或资料需要交接？可以说明接手人，暂未确定也可以。");
  form("SETTLEMENT","离职结算核对","离职日期|核对项目|具体疑问","你的离职日期是哪天？请填写完整日期。|要核对工资、未休年假、报销、社保，还是其他结算项目？|请说明你对结算的疑问；金额以 HR 核实结果为准。");
  form("EXIT_CERT","离职证明申请或补发","申请方式|证明用途|接收要求","你是首次申请离职证明，还是补发、更正？|这份证明用于什么场景？|需要中文、英文，或有指定抬头吗？审核后可在线下载，无特殊要求可回复‘中文，无指定抬头’。");
  form("ARCHIVE","档案调取咨询","档案类型|调取用途|接收单位","要查询人事档案存放地、调取材料副本，还是咨询档案转递？|调取或查询用于什么事项？|由哪个单位接收？暂未确定可以说明，由 HR 核对办理要求。");
  form("RELATION_CERT","劳动关系证明","证明类型|证明用途|接收要求","需要劳动关系存续、解除劳动关系、工作经历，还是其他证明？|这份证明用于什么事项？|有指定抬头、语言或内容要求吗？没有可回复‘无特殊要求’。");
  form("EMPLOYMENT_CERT","在职证明","证明用途|文件语言|是否包含薪资|补充要求|模板选择","这份在职证明用于买房贷款、资格审核，还是其他事项？|需要中文、英文还是中英双语？|证明里需要写明薪资吗？请回复‘需要’或‘不需要’。|有指定抬头或内容要求吗？没有可回复‘无’。|是否需要使用你自己提供的模板？可以选择公司模板，或自行上传 DOCX 模板。");
  form("INCOME_CERT","收入证明","证明用途|补充要求|模板选择","收入证明用于买房贷款还是其他事项？|有指定银行抬头或其他要求吗？没有可回复‘无’。|是否需要使用你自己提供的模板？可以选择公司模板，或自行上传 DOCX 模板。");
  form("VISA_CERT","出国签证在职证明","目的国家|受理机构|证明用途|文件语言|是否包含薪资|补充要求","准备办理哪个国家或地区的签证？|由哪个领事馆或签证受理机构接收？|此次出国用于旅游、商务还是其他事项？|需要中文、英文还是中英双语？|证明里需要写明薪资吗？请回复‘需要’或‘不需要’。|有指定抬头或其他要求吗？没有可回复‘无’。|是否需要使用你自己提供的模板？可以选择公司模板，或自行上传 DOCX 模板。");
 }
 private static void form(String key,String name,String fields,String prompts){FORMS.put(key,new Form(name,List.of(fields.split("\\|")),List.of(prompts.split("\\|"))));}
 @org.springframework.beans.factory.annotation.Autowired private EmploymentCertificateTemplateService certificateTemplates;
 @org.springframework.beans.factory.annotation.Autowired private CertificateTemplatePreparationService certificatePreparation;
 @org.springframework.beans.factory.annotation.Autowired private CertificateSignJobRepository signJobs;
 private final LifecycleRequestRepository cases;
 private final EmployeePayslipRepository payslips;
 private final UserAccountRepository users;
 private final LeaveRequestRepository leaveRequests;
 private final LeaveService leaves;
 private final EmployeeRelationsService relations;
 private final SecretCryptoService crypto;
 private final ObjectMapper json;
 private final AuditService audit;
 private final EmploymentCertificateService certificates;
 private final EmploymentCertificateRequestRepository certificateRequests;
 private final LeaveMedicalService medical;
 public LifecycleService(LifecycleRequestRepository cases,EmployeePayslipRepository payslips,UserAccountRepository users,
   LeaveRequestRepository leaveRequests,LeaveService leaves,EmployeeRelationsService relations,SecretCryptoService crypto,ObjectMapper json,AuditService audit,
   EmploymentCertificateService certificates,EmploymentCertificateRequestRepository certificateRequests,LeaveMedicalService medical){
  this.cases=cases;this.payslips=payslips;this.users=users;this.leaveRequests=leaveRequests;this.leaves=leaves;this.relations=relations;this.crypto=crypto;this.json=json;this.audit=audit;
  this.certificates=certificates;this.certificateRequests=certificateRequests;
  this.medical=medical;
 }
 public static boolean former(UserAccount u){return u.getEmployeeStatus()==EmployeeStatus.LEFT;}
 /** Whole-utterance commands only: never confuse '结束日期' or '不取消' with stopping a draft. */
 public static boolean cancellationRequested(String message){
  if(message==null)return false;
  String text=java.text.Normalizer.normalize(message,java.text.Normalizer.Form.NFKC).replaceAll("[\\s，,。!！?？]","");
  return text.matches("^(?:算了)?(?:请|帮我)?(?:我)?(?:先|暂时)?(?:取消办理|取消申请草稿|取消申请|放弃办理|取消|结束办理|结束申请|结束|停止办理|停止申请|停止|终止办理|退出办理|退出|不办理了|不办了|不用办理了|不想办理了|不想办了|不想申请了|不申请了|不请假了|不用了)(?:吧|了)?$")||text.equals("算了");
 }
 private boolean allowed(UserAccount u,String kind){return !former(u)||Set.of("EXIT_CERT","ARCHIVE","RELATION_CERT","SETTLEMENT","SOCIAL").contains(kind);}
 private static boolean leaveForm(String kind){return Set.of("LEAVE","MATERNITY_LEAVE").contains(kind);}
 public static String catalog(){return "我可以帮你办理这些事：\n\n- **入职**：材料核验、流程指引、合同签署咨询。\n- **在职**：申请假期、查询工资条、社保公积金咨询、人事信息变更、各类在职及收入证明（买房、出国签证等用途）。\n- **离职**：提交离职申请、查看交接清单、核对结算。\n- **离职后**：离职证明申请或补发、档案调取咨询、劳动关系证明。\n\n直接说‘我要申请请假’或‘补发离职证明’即可。我会逐项帮你填写，确认后交给 HRSSC 人工审核。可以随时说‘查看我的服务申请’查询进度。";}
 @Transactional
 public Optional<String> reply(UserAccount u,String message,String conversationId){
  String text=message==null?"":message.trim();
  if(text.startsWith("【系统已完成网页附件解析"))return Optional.empty();
  if (!former(u)) { var safety=relations.triage(u,text); if(safety.isPresent())return safety; }
  // Emotional disclosures interrupt form filling; never store them as an application field.
  if(CareConversationGuide.isCareTurn(text)&&!"LEAVE".equals(intent(text))&&!cancellationRequested(text))return Optional.empty();
  String cid=conversationId==null?"default":conversationId;
  if(!cid.matches("[A-Za-z0-9_-]{1,80}"))throw AppException.badRequest("对话标识不正确");
  if(has(text,"全周期","全生命周期","能办理什么","能办理哪些","人事服务种类"))return Optional.of(catalog());
  String stage=LifecycleNavigation.stage(text);
  if(stage!=null)return Optional.of(LifecycleNavigation.guide(former(u)?"离职后":stage));
  if(has(text,"工资条","薪资明细","工资明细","薪酬明细","实发工资"))return Optional.of(payroll(u,text));
  if(has(text,"我的服务申请","服务单进度","服务申请进度"))return Optional.of(mineSummary(u));
  if(text.matches(".*(查看|查询)服务单\\s*#?\\d+.*")) {
   var m=Pattern.compile("\\d+").matcher(text);m.find();return Optional.of(summary(own(u,Long.valueOf(m.group()))));
  }
  if(has(text,"交接清单","交接提醒")&&!former(u)){
   var tasks=relations.journey(u).stream().filter(t->"离职准备".equals(t.phase())).toList();
   return Optional.of("你的交接清单：\n"+String.join("\n",tasks.stream().map(t->"- "+(t.done()?"已完成：":"待完成：")+t.title()+"："+t.detail()).toList())+"\n\n在[交接任务页](http://localhost:5173/employee-experience?section=onboarding)更新进度。待完成项会持续保留，可随时问我‘查看交接提醒’。");
  }
  var last=cases.findFirstByTenantIdAndEmployeeIdAndConversationIdOrderByIdDesc(u.getTenantId(),u.getId(),cid).orElse(null);
  if(cancellationRequested(text)){
   if(last==null||!Set.of("DRAFT","NEEDS_INFO").contains(last.status))return Optional.of("当前没有待填写的申请。已提交的申请请在服务记录中查看处理状态。");
   last.status="CANCELLED";last.encryptedDateProposal=null;save(last);return Optional.of("已取消这次填写，我们先停在这里。需要办理时，再告诉我就好。");
  }
  // A side question must never become a purpose, date, recipient or HR supplement.
  if(last!=null&&Set.of("DRAFT","NEEDS_INFO").contains(last.status)&&EmployeeIntentUnderstanding.question(text)
    &&!Set.of("下一步","下一步呢","查看申请草稿","查看草稿").contains(text)&&intent(text)==null)return Optional.empty();
  if(last!=null&&"DRAFT".equals(last.status)&&isEmploymentCertificate(last.kind)&&last.certificateTemplateId!=null&&(!last.certificateTemplateConfirmed||fields(last).size()<FORMS.get(last.kind).fields.size()||!text.matches("^确认提交[。！!]?$"))){
   if(text.matches("^(确认提交|提交审核|确认模板字段)[。！!]?$") )return Optional.of(confirmCertificateTemplate(u,last.id,Map.of()).answer());
   if(!Set.of("继续办理","查看申请草稿","查看草稿","下一步呢","下一步").contains(text)&&!text.contains("怎么")&&!text.contains("如何")&&!text.startsWith("修改")&&!text.equals("上一步")&&intent(text)==null){
    var state=preparedCertificate(u,last);var pending=state.missingFields().stream().filter(f->!state.values().containsKey(f.key())).toList();
    if(!pending.isEmpty()){String key=pending.getFirst().key();CertificateTemplatePreparationService.validateValue(key,text);var input=new LinkedHashMap<>(certificateValues(last));input.put(key,text);last.encryptedCertificateValues=encode(input);save(last);}
   }
   return Optional.of(preparedCertificateReply(u,last).answer());
  }
  if(text.matches("^确认提交[。！!]?$")&&last!=null){
   if(!allowed(u,last.kind))return Optional.of("当前身份不能提交这类业务，可办理离职后服务。");
   if(!Set.of("DRAFT","NEEDS_INFO").contains(last.status))return Optional.of("这份申请已经提交，无需重复提交。\n"+summary(last));
   if(last.encryptedDateProposal!=null)return Optional.of(next(last));
   var fields=fields(last);var form=FORMS.get(last.kind);
   if(fields.size()<form.fields.size())return Optional.of(next(last));
   if(last.status.equals("NEEDS_INFO")&&!fields.containsKey("补充说明"))return Optional.of("请先补充 HR 要求的信息："+crypto.decrypt(last.encryptedOpinion));
   if(last.kind.equals("LEAVE")){
    if(leaveType(fields.get("假别"))==LeaveType.SICK&&last.medicalRecordId==null)return Optional.of(next(last));
    var input=leaveInput(fields,last.medicalRecordId);leaves.previewForAgent(u,input);
    last.leaveRequestId=leaves.createForAgent(u,input).id();
   }
   if(isEmploymentCertificate(last.kind)){
    if (("自行上传".equals(fields.get("模板选择")) || last.certificateTemplateId!=null) && (last.certificateTemplateId==null || !last.certificateTemplateConfirmed)) return Optional.of(next(last));
    var base=certificateInput(last.kind,fields);
    if(last.certificateTemplateId!=null)certificateTemplates.prepareProposal(u,last.certificateTemplateId,base.language(),base.destinationCountry(),base.consulateName());
    var input=new com.hragent.hragentv1.dto.EmploymentCertificateDtos.CreateRequest(base.certificateType(),base.language(),base.purpose(),base.destinationCountry(),base.consulateName(),base.includeSalary(),base.remarks(),last.certificateTemplateId,certificateValues(last));
    last.certificateRequestId=certificates.create(u,input).id();
   }
   if(last.kind.equals("EXIT"))last.dueDate=LocalDate.parse(fields.get("预计离职日期"));
   last.status="SUBMITTED";save(last);audit.log(u,"LIFECYCLE_SUBMITTED","lifecycle_request",last.id,last.kind);
   return Optional.of("已提交，服务单 #"+last.id+"。"+(last.kind.equals("LEAVE")?"请假进入现有主管审批及 HR 备案流程。":last.kind.equals("MATERNITY_LEAVE")?"产假申请已进入 HRSSC 人工审核队列，休假起止日期和材料由 HRSSC 核验。":last.certificateRequestId!=null?"已进入 HR 审核队列，审核通过后生成证明并进入电子签章。":"已进入 HRSSC 人工审核队列。")+"\n可继续说‘查看我的服务申请’查进度。"+(last.certificateRequestId!=null?"也可在[在职证明](http://localhost:5173/certificates)查看申请、补全所需资料并下载文件。":""));
  }
  String kind=intent(text);
  if(kind==null)kind=LifecycleNavigation.shortIntent(text);
  if(last!=null&&Set.of("DRAFT","NEEDS_INFO").contains(last.status)){
   if(!allowed(u,last.kind))return Optional.of("当前身份已变更，请说‘取消办理’结束原草稿，再发起离职后服务。");
   if(kind!=null)return Optional.of("你还有一份待填写的"+FORMS.get(last.kind).label+"。回复‘继续办理’继续，或‘取消办理’后再发起新业务。");
   if(text.equals("继续办理"))return Optional.of(next(last));
   if(text.equals("查看申请草稿")||text.equals("查看草稿"))return Optional.of("当前草稿（未提交）：\n"+String.join("\n",fields(last).entrySet().stream().map(e->"- "+e.getKey()+"："+e.getValue()).toList())+"\n\n"+next(last));
   if(last.status.equals("DRAFT") && (text.equals("上一步") || text.startsWith("修改"))){
    if(text.equals("上一步")&&last.encryptedDateProposal!=null){last.encryptedDateProposal=null;save(last);return Optional.of(next(last));}
    var values=fields(last);var form=FORMS.get(last.kind);int index=text.equals("上一步")?values.size()-1:form.fields.indexOf(text.substring(2).trim());
    if(last.kind.equals("MATERNITY_LEAVE")&&index==0)return Optional.of("这份是产假申请。如果想换其他假别，可以先取消这份草稿。\n\n"+next(last));
    if(index<0||index>=values.size())return Optional.of("请选择已填写的字段修改，或继续回答当前问题。\n\n"+next(last));
    for(int i=index;i<form.fields.size();i++)values.remove(form.fields.get(i));
    last.encryptedDateProposal=null;
    if(isEmploymentCertificate(last.kind)){last.certificateTemplateId=null;last.certificateTemplateConfirmed=false;last.encryptedCertificateValues=null;}
    if(last.kind.equals("LEAVE")&&index<=2)last.medicalRecordId=null;
    last.encryptedFields=encode(values);save(last);return Optional.of("已回到该步骤，后续内容需要重新核对。\n\n"+next(last));
   }
   // Questions may interrupt a form without being silently recorded as application data.
   if(has(text,"查询我的","查看我的","多少","几天","工龄","工作多久","年假余额","有哪些政策","今日人事提醒","有哪些待办","怎么","如何","政策","规定","制度","使用期限","有效期"))return Optional.empty();
   var values=fields(last);var f=FORMS.get(last.kind);
   if(last.status.equals("DRAFT")&&leaveForm(last.kind)){
    var pending=dateProposal(last);
    if(pending!=null&&!pending.confirmed()){
     if(text.matches("^(确认日期|确认这段时间|确认|是|是的|对|对的|没错|正确|好的|好|可以|嗯)[。！!，,]?$")){
      last.encryptedDateProposal=encode(new DateProposal(pending.field(),pending.start(),pending.end(),true));
      applyConfirmedDates(last,values);save(last);return Optional.of("好，日期记下了。\n\n"+next(last));
     }
     if(text.matches("^(重新输入日期|重新输入|不对|不是|否)[。！!，,]?$")){
      last.encryptedDateProposal=null;save(last);return Optional.of("好的，我们重新确定一下时间。\n\n"+next(last));
     }
     if(!LeaveDateParser.mentionsDate(text))return Optional.of(next(last));
    }
    String field=pending!=null?pending.field():values.containsKey("开始日期")?"结束日期":"开始日期";
    boolean expectingDate=values.size()>0&&values.size()<3;
    if((pending!=null&&!pending.confirmed())||expectingDate||values.isEmpty()&&LeaveDateParser.mentionsDate(text)){
     // When dates were confirmed before the leave type, keep asking for the type.
     if(!(pending!=null&&pending.confirmed()&&values.isEmpty()))return Optional.of(acceptLeaveDates(last,values,text,field));
    }
   }
   if(last.status.equals("NEEDS_INFO"))values.put("补充说明",limited(text,1000));
   else if(values.size()<f.fields.size()){
    String field=f.fields.get(values.size());validate(field,text);
    if(field.equals("假别")&&leaveType(text)==LeaveType.ANNUAL){String unavailable=annualUnavailable(u);if(unavailable!=null)return Optional.of(unavailable);}
    if(field.equals("结束日期")&&date(text).isBefore(LocalDate.parse(values.get("开始日期"))))throw AppException.badRequest("结束日期不能早于开始日期，请重新填写结束日期。");
    values.put(field,field.contains("日期")?date(text).toString():field.equals("假别")?leaveType(text).getLabel():text);
   }else return Optional.of(next(last));
   // A template uploaded at the start is retained as the choice, without inserting
   // sparse fields (the conversation advances through fields in order).
   if(isEmploymentCertificate(last.kind)&&last.certificateTemplateId!=null&&f.fields.contains("模板选择")&&values.size()==f.fields.size()-1&&!values.containsKey("模板选择"))values.put("模板选择","自行上传");
   if(isEmploymentCertificate(last.kind)&&"公司模板".equals(values.get("模板选择"))&&certificateTemplates!=null){
    var input=certificateInput(last.kind,values);var company=certificateTemplates.companyDefault(u,input.language(),input.destinationCountry(),input.consulateName());
    last.certificateTemplateId=company.map(EmploymentCertificateTemplate::getId).orElse(null);last.encryptedCertificateValues=null;last.certificateTemplateConfirmed=company.isEmpty();
   }
   last.encryptedFields=encode(values);applyConfirmedDates(last,values);save(last);return Optional.of(next(last));
  }
  if(kind!=null){
   if(!allowed(u,kind))return Optional.of("你当前是离职员工，可以申请离职证明、档案咨询、劳动关系证明或核对结算。");
   if(leaveForm(kind)&&(u.getRole()==Role.NEW_HIRE||u.getEmployeeStatus()!=EmployeeStatus.ACTIVE))return Optional.of("请先完成入职建档，再申请假期。");
   if(isEmploymentCertificate(kind)&&(u.getRole()==Role.NEW_HIRE||u.getEmployeeStatus()!=EmployeeStatus.ACTIVE))return Optional.of("在职证明需要先完成入职建档；离职后的证明请使用离职证明申请。");
   var initial=new LinkedHashMap<String,String>();String intro="";
   if(kind.equals("SERVICE_HELP")){String item=text.replaceFirst("^.*?办理","").replaceFirst("协助.*$","");initial.put("办理事项",limited(item,80));intro="我先帮你整理协助需求，核对并确认后交给 HRSSC 对接经办人员。\n\n";}
   if(kind.equals("MATERNITY_LEAVE")){initial.put("假别","产假");intro="我会陪你整理计划日期和申请说明。填好后由你核对，确认提交后交给 HRSSC 核验；需要的证明材料也可以在‘我的服务单’补充。\n\n";}
   if(kind.equals("LEAVE")){
    var named=Arrays.stream(LeaveType.values()).filter(v->text.replace("年休假","年假").contains(v.getLabel())).findFirst();
    if(named.isPresent()){
     if(named.get()==LeaveType.ANNUAL){String unavailable=annualUnavailable(u);if(unavailable!=null)return Optional.of(unavailable);var balance=leaves.agentBalances(u).stream().filter(b->b.leaveType().equals("ANNUAL")).findFirst().orElseThrow();intro="我查到你当前可申请的年假是 "+balance.availableDays().stripTrailingZeros().toPlainString()+" 天（已扣除审批中占用）。安排好休息，也有助于恢复工作状态。\n\n";}
     if(named.get()==LeaveType.SICK)intro="身体不舒服，先照顾好自己。我来帮你整理申请；填好日期后，再上传病历或诊断证明。\n\n";
     initial.put("假别",named.get().getLabel());
    }
   }
   var c=new LifecycleRequest();c.tenantId=u.getTenantId();c.employeeId=u.getId();c.conversationId=cid;c.kind=kind;c.encryptedFields=encode(initial);save(c);
   if(leaveForm(kind)&&LeaveDateParser.mentionsDate(text))return Optional.of("好，我们开始填写"+FORMS.get(kind).label+"。\n\n"+intro+acceptLeaveDates(c,initial,text,"开始日期"));
   return Optional.of("好，我们开始填写"+FORMS.get(kind).label+"。\n\n"+intro+next(c));
  }
  if(has(text,"入职材料","入职流程","入职需要准备"))return Optional.of("入职通常先确认报到安排，再准备身份证明、学历证明、照片及薪资账户信息，完成材料核验和合同签署。具体清单按你的岗位和公司要求确认。\n\n可以说‘我要办理入职材料核验’，我会逐项收集准备情况交给 HRSSC；完整证件资料按 HR 提供的安全渠道补充。");
  if(has(text,"离职流程","离职怎么办"))return Optional.of("可以在线发起离职申请，HR 确认时间后安排工作、设备和资料交接，再核对结算并交付证明。\n\n说‘我要申请离职’开始办理；也可以先问‘查看交接清单’。");
  if(has(text,"离职结算","离职工资"))return Optional.of("可以核对离职当月工资、未结报销、假期及社保处理情况。当前没有你的结算明细，我不会估算应得金额。说‘我要核对离职结算’，我会把问题交给 HRSSC 核实。");
  if(has(text,"合同签署","签合同"))return Optional.of("签署前先核对合同主体、岗位、期限、报酬及签署信息，再按公司电子签署渠道完成。条款或签署入口不清楚，可以说‘我要咨询合同签署’，交给 HRSSC 核实。");
  if(has(text,"档案调取","档案在哪"))return Optional.of("需要先确认档案类型、保管机构、用途和接收单位。说‘我要调取档案’，可在线提交咨询，由 HRSSC 核对后告知办理路径。");
  return Optional.empty();
 }
 static String intent(String t){
  var understood=EmployeeIntentUnderstanding.analyze(t);
  if(understood.negated()||Set.of(EmployeeIntentUnderstanding.Action.LOOKUP,EmployeeIntentUnderstanding.Action.PROGRESS,EmployeeIntentUnderstanding.Action.DOWNLOAD).contains(understood.action())
    ||understood.action()==EmployeeIntentUnderstanding.Action.POLICY&&EmployeeIntentUnderstanding.question(t))return null;
  if(has(t,"在职证明","工作证明","收入证明","签证证明","购房证明","买房证明")
    &&!has(t,"怎么","如何","怎样","流程","进度","状态","下载","规定","政策","制度","哪些","什么材料","了解","咨询","有效期")){
   if(has(t,"签证","出国","出境"))return "VISA_CERT";
   return t.contains("收入证明")?"INCOME_CERT":"EMPLOYMENT_CERT";
  }
  boolean start=has(t,"我要","我想","我需要","帮我","想请","想休","需要请","申请","发起","办理","补发","更正","补办","咨询","核对");
  if(!start||has(t,"怎么","如何","怎样","流程","需要什么","需要哪些","能否","可以吗","请假前","申请前","制度","政策","规定","标准","条件","规则","我想了解","了解一下"))return null;
  if(t.matches("^我要办理(报销|借款|出差|加班调休|福利|育儿假|陪产假|丧假)协助[。！!]?$"))return "SERVICE_HELP";
  if(has(t,"产假","生育假")&&!has(t,"陪产假","不想请","不想休","不需要请","不请假","不休假","取消")&&has(t,"申请","我要请","帮我请","想请","需要请","想休","我要休"))return "MATERNITY_LEAVE";
  if(has(t,"离职证明"))return "EXIT_CERT";
  if(has(t,"劳动关系证明","工作经历证明","解除劳动关系证明"))return "RELATION_CERT";
  if(has(t,"档案"))return "ARCHIVE";
  if(has(t,"结算"))return "SETTLEMENT";
  if(has(t,"申请离职","办理离职","发起离职","我要离职","我想离职"))return "EXIT";
  if(has(t,"人事变更","变更","修改我的","更新我的")&&has(t,"人事","信息","手机","电话","地址","住址","岗位","部门","联系"))return "CHANGE";
  if(has(t,"合同签署","签合同","合同更正","合同续签"))return "CONTRACT";
  if(has(t,"入职材料","材料核验","办理入职"))return "ONBOARDING";
  if(has(t,"社保","公积金")&&has(t,"咨询","办理","核对","申请"))return "SOCIAL";
  if(has(t,"请假","休假","年假","年休假","病假","事假","婚假")&&!has(t,"不想请","不想休","不需要请","不请假","不休假","取消")&&has(t,"申请","我要请","帮我请","想请","需要请","想休","我要休"))return "LEAVE";
  return null;
 }
 private void validate(String field,String text){
  limited(text,600);
  if(field.contains("日期"))date(text);
  if(field.equals("假别"))leaveType(text);
  if(field.equals("模板选择")&&!Set.of("公司模板","自行上传").contains(text))throw AppException.badRequest("请选择‘公司模板’或‘自行上传’，选择后可在对话框上传模板。");
  if(field.equals("文件语言"))certificateLanguage(text);
  if(field.equals("是否包含薪资"))includeSalary(text);
  if(field.equals("证明用途")&&text.length()>200)throw AppException.badRequest("证明用途请概括到 200 字以内");
  if(field.equals("目的国家")&&text.length()>100)throw AppException.badRequest("国家或地区请控制在 100 字以内");
  if(field.equals("受理机构")&&text.length()>160)throw AppException.badRequest("受理机构请控制在 160 字以内");
 }
 private static boolean isEmploymentCertificate(String kind){return Set.of("EMPLOYMENT_CERT","INCOME_CERT","VISA_CERT").contains(kind);}
 private CertificateLanguage certificateLanguage(String text){if(has(text,"双语","中英"))return CertificateLanguage.BILINGUAL;if(has(text,"中文","汉语"))return CertificateLanguage.CHINESE;if(has(text,"英文","英语"))return CertificateLanguage.ENGLISH;throw AppException.badRequest("请选择中文、英文或中英双语。");}
 private boolean includeSalary(String text){if(text.matches("^(不需要|不包含|不要|否|不含薪资|不写薪资)[。！!]?$") )return false;if(text.matches("^(需要|包含|要|是|包含薪资|写明薪资)[。！!]?$") )return true;throw AppException.badRequest("请明确回复‘需要’或‘不需要’包含薪资。");}
 private com.hragent.hragentv1.dto.EmploymentCertificateDtos.CreateRequest certificateInput(String kind,Map<String,String> fields){
  var type=kind.equals("VISA_CERT")?EmploymentCertificateType.VISA:kind.equals("INCOME_CERT")?EmploymentCertificateType.INCOME:EmploymentCertificateType.STANDARD;
  return new com.hragent.hragentv1.dto.EmploymentCertificateDtos.CreateRequest(type,type==EmploymentCertificateType.INCOME?CertificateLanguage.CHINESE:certificateLanguage(fields.get("文件语言")),fields.get("证明用途"),fields.get("目的国家"),fields.get("受理机构"),type==EmploymentCertificateType.INCOME||includeSalary(fields.get("是否包含薪资")),fields.get("补充要求"));
 }
 private Optional<EmploymentCertificateRequest> originalCertificate(LifecycleRequest c){return c.certificateRequestId==null?Optional.empty():certificateRequests.findById(c.certificateRequestId).filter(r->r.getTenantId().equals(c.tenantId)&&r.getEmployeeId().equals(c.employeeId));}
 private LocalDate date(String text){
  if(text.matches("^(今天|明天|后天)[。！!]?$") )return EmployeeReminderService.today().plusDays(text.startsWith("明天")?1:text.startsWith("后天")?2:0);
  var match=Pattern.compile("(20\\d{2})[-年/](\\d{1,2})[-月/](\\d{1,2})日?").matcher(text);
  if(match.find())try{return LocalDate.of(Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2)),Integer.parseInt(match.group(3)));}catch(Exception ignored){}
  throw AppException.badRequest("请填写有效日期，例如 2026-09-15，也可以说‘明天’。");
 }
 private LeaveType leaveType(String t){return Arrays.stream(LeaveType.values()).filter(v->t.contains(v.getLabel())).findFirst().orElseThrow(()->AppException.badRequest("请选择系统支持的假别："+String.join("、",Arrays.stream(LeaveType.values()).map(LeaveType::getLabel).toList())));}
 private LeaveDtos.CreateLeaveRequest leaveInput(Map<String,String> f,Long medicalId){return new LeaveDtos.CreateLeaveRequest(leaveType(f.get("假别")),LocalDate.parse(f.get("开始日期")),LocalDate.parse(f.get("结束日期")),BigDecimal.ONE,f.get("请假原因"),medicalId);}
 private String annualUnavailable(UserAccount u){
  var balance=leaves.agentBalances(u).stream().filter(b->b.leaveType().equals("ANNUAL")).findFirst();
  if(balance.isEmpty())return "暂时没查到你的年假余额，我先不替你判断能休几天。可以联系 HR 核对，或打开请假页面查看。";
  var b=balance.get();if(b.availableDays().signum()>0)return null;
  return "我查了一下，你当前可申请的年假是 0 天。"+(b.reservedDays().signum()>0?"其中有 "+b.reservedDays().stripTrailingZeros().toPlainString()+" 天已被审批中的申请占用。":"这期年假已经用完了。")+"\n\n想休息的感受我理解。可以先和主管商量工作轻重、安排短暂休整，慢慢找回节奏；如果确有其他请假需要，我也能帮你继续办理。你想先看看申请记录，还是聊聊怎么和主管开口？";
 }
 private String next(LifecycleRequest c){var f=FORMS.get(c.kind);var values=fields(c);
  var proposal=dateProposal(c);if(proposal!=null&&!proposal.confirmed())return dateConfirmation(proposal);
  if(c.status.equals("NEEDS_INFO")&&!values.containsKey("补充说明"))return "HR 请你补充："+crypto.decrypt(c.encryptedOpinion)+"\n直接回复补充内容，核对后再说‘确认提交’。";
  if(values.size()<f.fields.size())return "第 "+(values.size()+1)+" / "+f.fields.size()+" 步 · "+f.label+"\n"+f.questions.get(values.size())+(values.isEmpty()?"":"\n\n已记录："+String.join("；",values.keySet())+"。可说‘上一步’修改。");
  String draft="请核对这份"+f.label+"：\n"+String.join("\n",values.entrySet().stream().map(e->"- "+e.getKey()+"："+e.getValue()).toList());
  if(c.kind.equals("LEAVE")&&leaveType(values.get("假别"))==LeaveType.SICK&&c.medicalRecordId==null)return draft+"\n\n还差一份病历或诊断证明。点击下方上传清晰的 PDF/JPG/PNG（不超过 5 MB），我会先检查材料，再请你确认提交。可遮盖与本次申请无关的信息，保留姓名、医疗机构、日期和相关医嘱。";
  if(isEmploymentCertificate(c.kind)&&("自行上传".equals(values.get("模板选择"))||c.certificateTemplateId!=null)&&!c.certificateTemplateConfirmed)return draft+(c.certificateTemplateId==null?"\n\n请从下方上传入口或对话框附件上传 DOCX 模板。系统会提取字段，请补充自定义内容并确认后提交。":"\n\n已选定模板，请在下方核对提取的字段并补充内容，然后确认提交。");
  return draft+(c.medicalRecordId!=null?"\n\n病假材料已初检，真实性和医嘱由人工核验。":"")+"\n\n以上是待提交草稿。回复‘确认提交’交给人工审核；可说‘修改"+f.fields.get(0)+"’回到该项，或‘取消办理’。";
 }
 @Transactional
 public Optional<WebChatDtos.MessageResponse> guidedReply(UserAccount u,String message,String cid){
  return reply(u,message,cid).map(answer->new WebChatDtos.MessageResponse(answer,"hrssc-lifecycle",UUID.randomUUID().toString(),actions(u,message,cid,answer)));
 }
 private List<ChatAction> actions(UserAccount u,String message,String cid,String answer){
  if(answer.startsWith("模板已读取")||answer.startsWith("收到你的模板啦")){
   var c=cases.findFirstByTenantIdAndEmployeeIdAndConversationIdOrderByIdDesc(u.getTenantId(),u.getId(),cid==null?"default":cid).orElse(null);
   if(c!=null&&"DRAFT".equals(c.status)&&c.certificateTemplateId!=null)return List.of(new ChatAction("补齐并提交审核","certificate-template",c.id.toString()),LifecycleNavigation.say("取消办理","取消办理"));
  }
  if(answer.contains("请确认日期："))return List.of(LifecycleNavigation.say("对，日期没错","确认日期"),LifecycleNavigation.say("重新输入日期","重新输入日期"),LifecycleNavigation.say("取消办理","取消办理"));
  if(answer.startsWith("我查了一下")||answer.startsWith("暂时没查到你的年假余额")){
   var draft=cases.findFirstByTenantIdAndEmployeeIdAndConversationIdOrderByIdDesc(u.getTenantId(),u.getId(),cid==null?"default":cid);
   return List.of(LifecycleNavigation.say("查看申请记录","查看我的请假"),LifecycleNavigation.say("选择其他假别",draft.filter(c->c.kind.equals("LEAVE")&&c.status.equals("DRAFT")).isPresent()?"继续办理":"我要申请请假"),LifecycleNavigation.say("聊聊怎么开口","聊聊怎么开口"),LifecycleNavigation.page("打开请假页面","/my-leave"));
  }
  if(answer.equals(catalog()))return LifecycleNavigation.stages(former(u));
  String stage=LifecycleNavigation.stage(message);
  if(stage!=null && answer.equals(LifecycleNavigation.guide(former(u)?"离职后":stage)))return LifecycleNavigation.choices(former(u)?"离职后":stage);
  if(answer.startsWith("已提交，")||answer.startsWith("你的最近服务申请")||answer.startsWith("你目前没有服务申请"))return List.of(LifecycleNavigation.records(),LifecycleNavigation.say("选择其他服务","有哪些全周期服务"));
  if(answer.startsWith("已取消"))return List.of();
  if(answer.startsWith("你的交接清单"))return List.of(LifecycleNavigation.page("前往交接清单","/employee-experience?section=onboarding&journey=exit"));
  if(!(answer.startsWith("开始填写")||answer.startsWith("好，日期记下了")||answer.startsWith("好的，我们重新")||answer.startsWith("好，我们开始填写")||answer.startsWith("第 ")||answer.startsWith("请核对这份")||answer.startsWith("当前草稿")||answer.startsWith("已回到")||answer.startsWith("请选择已填写")||answer.startsWith("你还有一份")||answer.startsWith("HR 请你补充")))return List.of();
  var c=cases.findFirstByTenantIdAndEmployeeIdAndConversationIdOrderByIdDesc(u.getTenantId(),u.getId(),cid==null?"default":cid).orElse(null);
  if(c==null||!Set.of("DRAFT","NEEDS_INFO").contains(c.status))return List.of();
  var values=fields(c);var form=FORMS.get(c.kind);var result=new ArrayList<ChatAction>();
  if(answer.startsWith("你还有一份"))result.add(LifecycleNavigation.say("继续当前申请","继续办理"));
  else if(values.size()>=form.fields.size() && (!c.status.equals("NEEDS_INFO")||values.containsKey("补充说明"))){
   boolean sick=c.kind.equals("LEAVE")&&leaveType(values.get("假别"))==LeaveType.SICK;
   if(sick)result.add(new ChatAction(c.medicalRecordId==null?"上传病历并初检":"重新上传病历","medical-upload",c.id.toString()));
   boolean custom=isEmploymentCertificate(c.kind)&&("自行上传".equals(values.get("模板选择"))||c.certificateTemplateId!=null);
   if(custom)result.add(new ChatAction("上传模板并核对字段","certificate-template",c.id.toString()));
   if((!custom||c.certificateTemplateConfirmed)&&(!sick||c.medicalRecordId!=null))result.add(LifecycleNavigation.say("确认提交申请","确认提交"));
   if(c.status.equals("DRAFT"))for(String field:form.fields)if(!c.kind.equals("MATERNITY_LEAVE")||!field.equals("假别"))result.add(LifecycleNavigation.say("修改"+field,"修改"+field));
  }else if(c.status.equals("DRAFT")){
   String field=form.fields.get(values.size());List<String> options=switch(field){case "假别"->Arrays.stream(LeaveType.values()).map(LeaveType::getLabel).toList();case "模板选择"->List.of("公司模板","自行上传");case "文件语言"->List.of("中文","英文","中英双语");case "是否包含薪资"->List.of("需要","不需要");case "合同事项"->List.of("首次签署","续签","合同更正");case "申请方式"->List.of("首次申请","补发","更正");case "变更项目"->List.of("联系方式","住址","部门","岗位");case "咨询事项"->List.of("缴纳情况","转移","补缴");default->List.of();};
   options.forEach(v->result.add(LifecycleNavigation.say(v,v)));
  }
  if(!values.isEmpty()&&c.status.equals("DRAFT"))result.add(LifecycleNavigation.say("上一步","上一步"));
  if(!values.isEmpty()&&values.size()<form.fields.size()&&c.status.equals("DRAFT"))result.add(LifecycleNavigation.say("查看草稿","查看申请草稿"));
  result.add(LifecycleNavigation.say("取消办理","取消办理"));LifecycleNavigation.formPage(c.kind).ifPresent(result::add);
  return result;
 }
 private void save(LifecycleRequest c){c.updatedAt=LocalDateTime.now();cases.save(c);}
 @Transactional public Optional<WebChatDtos.MessageResponse> medicalAttachment(UserAccount u,String cid,org.springframework.web.multipart.MultipartFile file,String message){
  var draft=cases.findFirstByTenantIdAndEmployeeIdAndConversationIdOrderByIdDesc(u.getTenantId(),u.getId(),cid==null?"default":cid);
  var sick=draft.filter(c->c.kind.equals("LEAVE")&&c.status.equals("DRAFT")&&"病假".equals(fields(c).get("假别")));
  if(sick.isPresent())return Optional.of(medicalUpload(u,sick.get().id,file));
  if(has((message==null?"":message)+(file.getOriginalFilename()==null?"":file.getOriginalFilename()),"病历","病假","诊断证明"))return Optional.of(new WebChatDtos.MessageResponse("我可以帮你检查病假材料。先告诉我‘我想请病假’，填好日期和原因后，再从专门的病历入口上传；这份附件尚未扫描或提交。","hrssc-lifecycle",UUID.randomUUID().toString(),List.of(LifecycleNavigation.say("开始病假申请","我想请病假"))));
  return Optional.empty();
 }
 @Transactional public WebChatDtos.MessageResponse medicalUpload(UserAccount u,Long id,org.springframework.web.multipart.MultipartFile file){
  var c=own(u,id);var f=fields(c);
  if(!c.status.equals("DRAFT")||!c.kind.equals("LEAVE")||!"病假".equals(f.get("假别"))||f.size()<4||c.encryptedDateProposal!=null)throw AppException.badRequest("请先在当前病假草稿中填好并确认日期和原因，再上传材料");
  var scan=medical.upload(u,file,LocalDate.parse(f.get("开始日期")),LocalDate.parse(f.get("结束日期")));c.medicalRecordId=scan.id();save(c);
  String answer=next(c);return new WebChatDtos.MessageResponse(answer,"hrssc-lifecycle",UUID.randomUUID().toString(),actions(u,"",c.conversationId,answer));
 }

 private Map<String,String> certificateValues(LifecycleRequest c){if(c.encryptedCertificateValues==null)return Map.of();try{return json.readValue(crypto.decrypt(c.encryptedCertificateValues),new TypeReference<Map<String,String>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
 private LifecycleRequest certificateDraft(UserAccount u,Long id){var c=own(u,id);if(!"DRAFT".equals(c.status)||!isEmploymentCertificate(c.kind))throw AppException.badRequest("请在当前在职证明草稿中上传模板");return c;}
 @Transactional public Optional<WebChatDtos.MessageResponse> certificateAttachment(UserAccount u,String cid,org.springframework.web.multipart.MultipartFile file){
  return certificateAttachment(u,cid,file,null);
 }
 @Transactional public Optional<WebChatDtos.MessageResponse> certificateAttachment(UserAccount u,String cid,org.springframework.web.multipart.MultipartFile file,String message){
  String conversation=cid==null?"default":cid;
  if(!conversation.matches("[A-Za-z0-9_-]{1,80}"))throw AppException.badRequest("对话标识不正确");
  String text=message==null?"":message.trim();
  if(text.length()>1000)throw AppException.badRequest("附件说明不能超过 1000 个字符");
  var last=cases.findFirstByTenantIdAndEmployeeIdAndConversationIdOrderByIdDesc(u.getTenantId(),u.getId(),conversation).orElse(null);
  boolean active=last!=null&&Set.of("DRAFT","NEEDS_INFO").contains(last.status);
  String kind=intent(text);
  if(kind==null&&text.isBlank()&&file!=null&&file.getOriginalFilename()!=null)kind=intent(file.getOriginalFilename());
  boolean requested=kind!=null&&isEmploymentCertificate(kind);
  if(!requested&&!(active&&isEmploymentCertificate(last.kind)))return Optional.empty();
  if(cancellationRequested(text))return guidedReply(u,text,conversation);
  var safety=relations.triage(u,text);
  if(safety.isPresent())return Optional.of(new WebChatDtos.MessageResponse(safety.get()+"\n这份附件尚未处理。","er-human-support",UUID.randomUUID().toString()));
  if(former(u)||u.getRole()==Role.NEW_HIRE||u.getEmployeeStatus()!=EmployeeStatus.ACTIVE)throw AppException.badRequest("在职证明需要在职员工档案；离职后的证明请使用离职证明申请。");
  if(active&&(!isEmploymentCertificate(last.kind)||!"DRAFT".equals(last.status)||(requested&&!last.kind.equals(kind)))) {
   return Optional.of(new WebChatDtos.MessageResponse("你还有一份"+FORMS.get(last.kind).label+"正在办理。这份附件还没有保存；可以先继续当前申请，或取消后重新上传模板办理证明。","hrssc-lifecycle",UUID.randomUUID().toString(),List.of(LifecycleNavigation.say("继续当前申请","继续办理"),LifecycleNavigation.say("取消当前办理","取消办理"))));
  }
  if(!active){
   // Validate locally before creating a draft. DOCX templates do not need n8n/OCR
   // or an LLM callback; a broken parser must not stall this business flow.
   var preview=certificateTemplates.preview(u,file);
   if(!preview.canUpload())throw AppException.badRequest("请在模板中标出待填写字段，例如【姓名】、【接收单位】或姓名：____");
   last=new LifecycleRequest();last.tenantId=u.getTenantId();last.employeeId=u.getId();last.conversationId=conversation;last.kind=kind;last.encryptedFields=encode(new LinkedHashMap<String,String>());save(last);
  }
  return Optional.of(certificateUpload(u,last.id,file));
 }
 @Transactional public WebChatDtos.MessageResponse certificateUpload(UserAccount u,Long id,org.springframework.web.multipart.MultipartFile file){
  var c=certificateDraft(u,id);var values=fields(c);var preview=certificateTemplates.preview(u,file);
  if(!preview.canUpload())throw AppException.badRequest("请在模板中标出待填写字段，例如【姓名】、【接收单位】或姓名：____");
  var lang=values.containsKey("文件语言")?certificateLanguage(values.get("文件语言")):certificatePreparation.language(file);
  if(c.kind.equals("INCOME_CERT"))lang=CertificateLanguage.CHINESE;
  var template=certificateTemplates.uploadProposal(u,file,"个人证明模板",values.get("目的国家"),values.get("受理机构"),lang);
  c.certificateTemplateId=template.getId();c.certificateTemplateConfirmed=false;c.encryptedCertificateValues=null;
  values.put("文件语言",lang.getLabel());values.put("模板选择","自行上传");
  values.putIfAbsent("是否包含薪资",c.kind.equals("INCOME_CERT")||preview.placeholders().contains("{{monthlySalary}}")||preview.placeholders().contains("{{currency}}")?"需要":"不需要");
  values.putIfAbsent("补充要求","按上传模板开具");
  if(!preview.placeholders().contains("{{purpose}}"))values.putIfAbsent("证明用途","按员工提供模板开具"+FORMS.get(c.kind).label);
  c.encryptedFields=encode(values);save(c);return preparedCertificateReply(u,c);
 }
 private CertificateTemplatePreparationService.Prepared preparedCertificate(UserAccount u,LifecycleRequest c){
  var download=certificateTemplates.download(u,c.certificateTemplateId);var file=new CertificateMemoryFile(download.fileName(),download.contentType(),download.content());
  var preview=certificateTemplates.preview(u,file);return certificatePreparation.prepare(u,c.kind,preview.placeholders(),fields(c),certificateValues(c));
 }
 private WebChatDtos.MessageResponse preparedCertificateReply(UserAccount u,LifecycleRequest c){
  var state=preparedCertificate(u,c);var missing=state.missingFields().stream().filter(f->!state.values().containsKey(f.key())).map(CertificateTemplatePreparationService.MissingField::label).toList();
  String answer="模板已读取，员工档案中已有的信息我已经帮你带入啦。"+(missing.isEmpty()?"资料齐了，点击下方‘提交 HR 审核’就可以，不用重新填写。":"还差："+String.join("、",missing)+"。在下方一次补齐就能提交，也可以直接告诉我"+missing.getFirst()+"。")+"\n审核通过后，会按这份模板生成正式证明并进入签章。";
  return new WebChatDtos.MessageResponse(answer,"hrssc-lifecycle",UUID.randomUUID().toString(),List.of(new ChatAction("补齐并提交审核","certificate-template",c.id.toString()),LifecycleNavigation.say("取消办理","取消办理")));
 }
 @Transactional public Object certificateTemplateState(UserAccount u,Long id){
  var c=certificateDraft(u,id);if(c.certificateTemplateId==null)return Map.of("uploaded",false);
  var download=certificateTemplates.download(u,c.certificateTemplateId);
  var file=new CertificateMemoryFile(download.fileName(),download.contentType(),download.content());
  var preview=certificateTemplates.preview(u,file);var state=preparedCertificate(u,c);
  var result=new LinkedHashMap<String,Object>();result.put("uploaded",true);result.put("fileName",download.fileName());result.put("fields",preview.placeholders());result.put("customFields",preview.unsupportedPlaceholders());result.put("values",state.values());result.put("automatic",state.automatic());result.put("missingFields",state.missingFields());result.put("language",fields(c).getOrDefault("文件语言","按模板"));result.put("confirmed",c.certificateTemplateConfirmed);return result;
 }
 @Transactional public WebChatDtos.MessageResponse confirmCertificateTemplate(UserAccount u,Long id,Map<String,String> input){
  var c=own(u,id);if(c.certificateRequestId!=null)return guidedReply(u,"确认提交",c.conversationId).orElseThrow();
  certificateDraft(u,id);if(c.certificateTemplateId==null)throw AppException.badRequest("请先上传模板");
  var state=preparedCertificate(u,c);var supplied=new LinkedHashMap<>(certificateValues(c));
  var allowed=state.missingFields().stream().map(CertificateTemplatePreparationService.MissingField::key).collect(java.util.stream.Collectors.toSet());
  for(var entry:input.entrySet()){if(!allowed.contains(entry.getKey()))throw AppException.badRequest("只需补充当前缺失的字段，请勿修改已读取的档案信息");CertificateTemplatePreparationService.validateValue(entry.getKey(),entry.getValue());supplied.put(entry.getKey(),entry.getValue().trim());}
  for(var field:state.missingFields())CertificateTemplatePreparationService.validateValue(field.key(),supplied.get(field.key()));
  var form=fields(c);for(var entry:Map.of("purpose","证明用途","destinationCountry","目的国家","consulateName","受理机构").entrySet())if(supplied.containsKey(entry.getKey()))form.put(entry.getValue(),supplied.remove(entry.getKey()));
  form.putIfAbsent("证明用途","按员工提供模板开具"+FORMS.get(c.kind).label);form.putIfAbsent("文件语言","中文");form.putIfAbsent("是否包含薪资",state.required().contains("monthlySalary")?"需要":"不需要");form.putIfAbsent("补充要求","按上传模板开具");form.put("模板选择","自行上传");
  var ordered=new LinkedHashMap<String,String>();for(String key:FORMS.get(c.kind).fields)if(form.containsKey(key))ordered.put(key,form.get(key));
  c.encryptedFields=encode(ordered);c.encryptedCertificateValues=encode(supplied);c.certificateTemplateConfirmed=true;save(c);
  return guidedReply(u,"确认提交",c.conversationId).orElseThrow();
 }

 record DateProposal(String field,String start,String end,boolean confirmed) {}
 private DateProposal dateProposal(LifecycleRequest c){
  if(c.encryptedDateProposal==null)return null;
  try{return json.readValue(crypto.decrypt(c.encryptedDateProposal),DateProposal.class);}catch(Exception e){throw new IllegalStateException("待确认日期读取失败",e);}
 }
 private String dateConfirmation(DateProposal p){
  return "请确认日期：你是指 **"+p.start()+"**"+(p.end()==null?"（"+p.field()+"）":" 至 **"+p.end()+"** 这段时间")+"，对吗？\n\n确认后我再填入申请；如果需要调整，直接告诉我新的日期就好。";
 }
 private String acceptLeaveDates(LifecycleRequest c,Map<String,String> values,String text,String field){
  try{
   var selection=LeaveDateParser.parse(text,EmployeeReminderService.today(),field.equals("结束日期")&&values.containsKey("开始日期")?LocalDate.parse(values.get("开始日期")):null);
   c.encryptedDateProposal=encode(new DateProposal(selection.end()!=null?"开始日期":field,selection.start().toString(),selection.end()==null?null:selection.end().toString(),!selection.confirmation()));
   applyConfirmedDates(c,values);save(c);return next(c);
  }catch(IllegalArgumentException e){return e.getMessage()+(c.encryptedDateProposal!=null?"\n\n"+next(c):"");}
 }
 private void applyConfirmedDates(LifecycleRequest c,Map<String,String> values){
  var p=dateProposal(c);if(p==null||!p.confirmed()||!values.containsKey("假别"))return;
  values.put(p.field(),p.start());if(p.end()!=null)values.put("结束日期",p.end());
  c.medicalRecordId=null;c.encryptedDateProposal=null;c.encryptedFields=encode(values);
 }
 private Map<String,String> fields(LifecycleRequest c){try{return json.readValue(crypto.decrypt(c.encryptedFields),new TypeReference<LinkedHashMap<String,String>>(){});}catch(Exception e){throw new IllegalStateException("申请内容读取失败",e);}}
 private String encode(Object value){try{return crypto.encrypt(json.writeValueAsString(value));}catch(Exception e){throw new IllegalStateException(e);}}
 private LifecycleRequest own(UserAccount u,Long id){var c=cases.findByIdAndTenantId(id,u.getTenantId()).orElseThrow(()->AppException.notFound("申请不存在"));if(!c.employeeId.equals(u.getId()))throw AppException.forbidden("只能查看本人申请");return c;}
 private LifecycleRequest hrCase(UserAccount u,Long id){requireHr(u);return cases.findByIdAndTenantId(id,u.getTenantId()).orElseThrow(()->AppException.notFound("申请不存在"));}
 static void requireHr(UserAccount u){if(u.getRole()!=Role.HR||!u.isActive()||u.getEmployeeStatus()!=EmployeeStatus.ACTIVE)throw AppException.forbidden("仅在职 HRSSC 管理员可处理");}
 private boolean signed(LifecycleRequest c){return signJobs!=null&&c.certificateRequestId!=null&&signJobs.findById(c.certificateRequestId).filter(j->j.tenantId.equals(c.tenantId)&&"SIGNED".equals(j.status)).isPresent();}
 private String status(LifecycleRequest c){if(c.certificateRequestId!=null&&signed(c))return "电子签章已完成，可下载 PDF";if(c.certificateRequestId!=null)return originalCertificate(c).map(r->r.getStatus()==CertificateRequestStatus.GENERATED?"文件已生成，等待电子签章":r.getStatus().getLabel()).orElse("证明记录待核实");if(c.leaveRequestId!=null)return leaveRequests.findById(c.leaveRequestId).filter(r->r.getTenantId().equals(c.tenantId)&&r.getEmployeeId().equals(c.employeeId)).map(r->r.getStatus().getLabel()).orElse("请假记录待核实");return switch(c.status){case "DRAFT"->"待填写";case "SUBMITTED"->"待 HRSSC 审核";case "NEEDS_INFO"->"待补充材料";case "APPROVED"->"审核通过，办理中";case "COMPLETED"->"已办结";case "REJECTED"->"未通过";case "CANCELLED"->"已取消";default->c.status;};}
 private Optional<LeaveRequest> originalLeave(LifecycleRequest c){return Optional.ofNullable(c.leaveRequestId).flatMap(leaveRequests::findById).filter(r->r.getTenantId().equals(c.tenantId)&&r.getEmployeeId().equals(c.employeeId));}
 private String opinion(LifecycleRequest c){if(c.leaveRequestId!=null)return originalLeave(c).map(r->r.getHrOpinion()!=null?r.getHrOpinion():r.getManagerOpinion()!=null?r.getManagerOpinion():"").orElse("");return originalCertificate(c).map(r->r.getHrOpinion()==null?"":r.getHrOpinion()).orElseGet(()->crypto.decrypt(c.encryptedOpinion));}
 private String effectiveStatus(LifecycleRequest c){if(c.leaveRequestId!=null)return originalLeave(c).map(r->switch(r.getStatus()){case APPROVED->"COMPLETED";case REJECTED->"REJECTED";default->"SUBMITTED";}).orElse(c.status);return originalCertificate(c).map(r->switch(r.getStatus()){case PENDING_HR->"SUBMITTED";case APPROVED,GENERATION_FAILED->"APPROVED";case GENERATED->"COMPLETED";case REJECTED->"REJECTED";case CANCELLED->"CANCELLED";}).orElse(c.status);}
 private String summary(LifecycleRequest c){return "#"+c.id+" · "+FORMS.get(c.kind).label+" · "+status(c)+(opinion(c).isBlank()?"":"\nHR 回复："+opinion(c))+(c.certificateRequestId!=null?"\n[查看证明申请及下载文件](http://localhost:5173/certificates)":c.fileName!=null&&c.status.equals("COMPLETED")?"\n文件已交付，请到[我的服务单](http://localhost:5174/?view=requests)下载。":"");}
 private String mineSummary(UserAccount u){var list=cases.findByTenantIdAndEmployeeIdOrderByUpdatedAtDesc(u.getTenantId(),u.getId());return list.isEmpty()?"你目前没有服务申请。" : "你的最近服务申请：\n\n"+String.join("\n\n",list.stream().limit(10).map(this::summary).toList())+"\n\n可说‘查看服务单 123’查看指定记录。退回补充的申请可在[我的服务单](http://localhost:5174/?view=requests)继续办理。";}
 public record View(Long id,Long employeeId,String employeeName,String employeeNo,String kind,String label,String status,String statusLabel,Map<String,String> fields,String opinion,Long leaveRequestId,String fileName,String materialsName,LocalDateTime updatedAt,Long certificateRequestId,String certificateFileName,boolean certificateSigned){}
 private View view(LifecycleRequest c){var u=users.findById(c.employeeId).orElseThrow();return new View(c.id,c.employeeId,u.getName(),u.getEmployeeNo(),c.kind,FORMS.get(c.kind).label,effectiveStatus(c),status(c),fields(c),opinion(c),c.leaveRequestId,c.status.equals("COMPLETED")?c.fileName:null,c.materialsName,c.updatedAt,c.certificateRequestId,originalCertificate(c).filter(r->r.getStatus()==CertificateRequestStatus.GENERATED).map(EmploymentCertificateRequest::getGeneratedFileName).orElse(null),signed(c));}
 @Transactional public List<View> mine(UserAccount u){return cases.findByTenantIdAndEmployeeIdOrderByUpdatedAtDesc(u.getTenantId(),u.getId()).stream().map(this::view).toList();}
 @Transactional public List<View> queue(UserAccount u){requireHr(u);return cases.findByTenantIdAndStatusNotOrderByUpdatedAtDesc(u.getTenantId(),"DRAFT").stream().filter(c->!c.status.equals("CANCELLED")).map(this::view).toList();}
 @Transactional public View supplement(UserAccount u,Long id,String text){var c=own(u,id);if(!c.status.equals("NEEDS_INFO"))throw AppException.badRequest("当前申请无需补充");var f=fields(c);f.put("补充说明",limited(text,1000));c.encryptedFields=encode(f);c.status="SUBMITTED";save(c);audit.log(u,"LIFECYCLE_SUPPLEMENT","lifecycle_request",id,c.kind);return view(c);}
 @Transactional public View review(UserAccount u,Long id,String action,String opinion){var c=hrCase(u,id);if(c.employeeId.equals(u.getId()))throw AppException.forbidden("不能审批本人申请");if(c.certificateRequestId!=null)throw AppException.badRequest("请在原有证明管理中审核，系统会按原流程生成文件");if(c.leaveRequestId!=null)throw AppException.badRequest("请假请在现有主管审批和 HR 备案流程处理");
  var allowed=switch(c.status){case "SUBMITTED"->Set.of("APPROVED","REJECTED","NEEDS_INFO");case "APPROVED"->Set.of("COMPLETED","NEEDS_INFO");default->Set.<String>of();};
  if(!allowed.contains(action))throw AppException.badRequest("申请状态已变化，请刷新后操作");
  if(action.equals("COMPLETED")&&Set.of("EXIT_CERT","RELATION_CERT").contains(c.kind)&&c.fileName==null)throw AppException.badRequest("请先上传经核实的证明文件，再办结");
  c.status=action;c.encryptedOpinion=crypto.encrypt(limited(opinion,2000));c.reviewerId=u.getId();
  if(action.equals("NEEDS_INFO")){var f=fields(c);f.remove("补充说明");c.encryptedFields=encode(f);}
  save(c);audit.log(u,"LIFECYCLE_"+action,"lifecycle_request",id,c.kind);return view(c);
 }
 @Transactional public void resultFile(UserAccount u,Long id,String filename,byte[] bytes){var c=hrCase(u,id);if(c.employeeId.equals(u.getId()))throw AppException.forbidden("不能交付本人申请");if(!c.status.equals("APPROVED"))throw AppException.badRequest("审核通过后才能上传交付文件");if(bytes.length==0||bytes.length>5*1024*1024)throw AppException.badRequest("请上传不超过 5 MB 的 PDF");if(bytes.length<5||!new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw AppException.badRequest("交付文件必须是 PDF");c.fileName="服务单-"+c.id+".pdf";c.encryptedFile=crypto.encrypt(Base64.getEncoder().encodeToString(bytes));save(c);audit.log(u,"LIFECYCLE_FILE","lifecycle_request",id,c.kind);}
 @Transactional public byte[] download(UserAccount u,Long id){var c=own(u,id);if(!c.status.equals("COMPLETED")||c.encryptedFile==null)throw AppException.notFound("文件尚未交付");audit.log(u,"LIFECYCLE_DOWNLOAD","lifecycle_request",id,c.kind);return Base64.getDecoder().decode(crypto.decrypt(c.encryptedFile));}
 @Transactional public void materials(UserAccount u,Long id,byte[] bytes){var c=own(u,id);if(c.certificateRequestId!=null)throw AppException.badRequest("在职证明的资料和签证模板请在原有证明页面补充");if(!Set.of("DRAFT","SUBMITTED","NEEDS_INFO").contains(c.status))throw AppException.badRequest("当前状态不能修改申请材料");if(bytes.length<5||bytes.length>5*1024*1024||!new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw AppException.badRequest("请上传不超过 5 MB 的 PDF 材料包");c.encryptedMaterials=crypto.encrypt(Base64.getEncoder().encodeToString(bytes));c.materialsName="申请材料-"+id+".pdf";save(c);audit.log(u,"LIFECYCLE_MATERIALS","lifecycle_request",id,c.kind);}
 @Transactional public byte[] materialsDownload(UserAccount u,Long id,boolean hr){var c=hr?hrCase(u,id):own(u,id);if(hr&&Set.of("DRAFT","CANCELLED").contains(c.status))throw AppException.forbidden("员工尚未确认提交这份材料");if(c.encryptedMaterials==null)throw AppException.notFound("尚未上传材料");audit.log(u,"LIFECYCLE_MATERIALS_READ","lifecycle_request",id,c.kind);return Base64.getDecoder().decode(crypto.decrypt(c.encryptedMaterials));}
 public String payroll(UserAccount u,String text){var m=Pattern.compile("(20\\d{2})[-年](\\d{1,2})月?").matcher(text);String month=null;
  if(m.find())try{month=YearMonth.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2))).toString();}catch(Exception e){return "请填写有效月份，例如 2026-08。";}
  if(month==null&&has(text,"上月","上个月"))month=YearMonth.from(EmployeeReminderService.today()).minusMonths(1).toString();
  if(month==null&&has(text,"本月","这个月"))month=YearMonth.from(EmployeeReminderService.today()).toString();
  var rows=payslips.findByTenantIdAndEmployeeIdOrderByPayMonthDesc(u.getTenantId(),u.getId());final String wanted=month;
  var row=rows.stream().filter(p->wanted==null||p.payMonth.equals(wanted)).findFirst();
  return row.map(p->"你的 "+p.payMonth+" 工资明细（HR 已发布）：\n\n"+crypto.decrypt(p.encryptedDetails)+"\n\n如需核对社保公积金，可以说‘我要咨询社保公积金’。").orElse("还没有查到"+(month==null?"你的工资条":month+" 的工资条")+"。需要 HRSSC 发布该月明细后才能查询，当前不会用示例金额代替。");
 }
 @Transactional public void publishPay(UserAccount hr,Long employeeId,String month,String details){requireHr(hr);var employee=users.findById(employeeId).filter(u->u.getTenantId().equals(hr.getTenantId())).orElseThrow(()->AppException.notFound("员工不存在"));try{month=YearMonth.parse(month).toString();}catch(Exception e){throw AppException.badRequest("月份格式为 YYYY-MM");}
  var p=payslips.findByTenantIdAndEmployeeIdAndPayMonth(hr.getTenantId(),employee.getId(),month).orElse(null);
  if(p==null){p=new EmployeePayslip();p.tenantId=hr.getTenantId();p.employeeId=employeeId;p.payMonth=month;}
  p.encryptedDetails=crypto.encrypt(limited(details,4000));p.publishedBy=hr.getId();p.updatedAt=LocalDateTime.now();payslips.save(p);audit.log(hr,"PAYSLIP_PUBLISHED","employee_payslip",p.id,month);
 }
 private static String limited(String text,int length){if(text==null||text.isBlank()||text.length()>length)throw AppException.badRequest("请填写 1 至 "+length+" 字的内容");return text.trim();}
 private static boolean has(String s,String...terms){return Arrays.stream(terms).anyMatch(s::contains);}
}
