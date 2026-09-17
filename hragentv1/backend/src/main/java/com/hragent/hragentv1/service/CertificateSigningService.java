package com.hragent.hragentv1.service;

import com.fasterxml.jackson.databind.*;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

@Service
public class CertificateSigningService {
 private final CertificateSignConfigRepository configs;
 private final CertificateSignJobRepository jobs;
 private final EmploymentCertificateRequestRepository requests;
 private final EmploymentCertificateDocumentService documents;
 private final SecretCryptoService crypto;
 private final TencentEssClient client;
 private final AuditService audit;
 private final TransactionTemplate tx;
 private final ObjectMapper json=new ObjectMapper();
 public CertificateSigningService(CertificateSignConfigRepository configs,CertificateSignJobRepository jobs,EmploymentCertificateRequestRepository requests,EmploymentCertificateDocumentService documents,SecretCryptoService crypto,TencentEssClient client,AuditService audit,PlatformTransactionManager tm){this.configs=configs;this.jobs=jobs;this.requests=requests;this.documents=documents;this.crypto=crypto;this.client=client;this.audit=audit;this.tx=new TransactionTemplate(tm);}
 public record ConfigInput(boolean enabled,String secretId,String secretKey,String operatorId,String organizationName,String sealId,String sealKeyword) {}
 public Object config(UserAccount user){hr(user);var entity=configs.findById(user.getTenantId()).orElse(null);var c=entity==null?null:settings(entity.encryptedConfig);return Map.of("enabled",entity!=null&&entity.enabled,"hasCredentials",c!=null,"operatorId",c==null?"":c.operatorId(),"organizationName",c==null?"":c.organizationName(),"sealId",c==null?"":c.sealId(),"sealKeyword",c==null?"公司盖章处":c.sealKeyword());}
 public Object configure(UserAccount user,ConfigInput input){hr(user);return tx.execute(t->{
  var entity=configs.findById(user.getTenantId()).orElseGet(CertificateSignConfig::new);var old=entity.encryptedConfig==null?null:settings(entity.encryptedConfig);
  String secretId=blank(input.secretId())&&old!=null?old.secretId():input.secretId(),secretKey=blank(input.secretKey())&&old!=null?old.secretKey():input.secretKey();
  var c=new TencentEssClient.Settings(required(secretId,200),required(secretKey,200),required(input.operatorId(),200),required(input.organizationName(),200),required(input.sealId(),200),required(input.sealKeyword(),60));
  entity.tenantId=user.getTenantId();entity.enabled=input.enabled();entity.encryptedConfig=encode(c);configs.save(entity);audit.log(user,"CONFIGURE_CERTIFICATE_ESIGN","certificate_sign_config",user.getTenantId(),input.enabled()?"启用证明腾讯电子签章":"暂停新证明自动签章");return config(user);
 });}
 /** Enqueued in the same database transaction as successful document generation. */
 public void enqueue(EmploymentCertificateRequest request){
  if(jobs.existsById(request.getId()))return;
  var job=new CertificateSignJob();job.certificateId=request.getId();job.tenantId=request.getTenantId();
  var config=configs.findById(request.getTenantId()).filter(c->c.enabled).orElse(null);
  job.status=config==null?"WAITING_CONFIG":"READY";job.encryptedConfig=config==null?null:config.encryptedConfig;jobs.save(job);
 }
 public Object status(UserAccount actor,Long id){access(actor,id);var job=jobs.findById(id).orElse(null);return view(job);}
 private Object view(CertificateSignJob j){String state=j==null?"NOT_STARTED":j.status;return Map.of("status",state,"label",label(state),"flowId",j==null||j.flowId==null?"":j.flowId,"error",j==null||j.error==null?"":j.error,"signed",state.equals("SIGNED"));}
 private String label(String s){return switch(s){case "WAITING_CONFIG"->"等待 HR 配置电子签";case "READY","UPLOADING","UPLOADED","CONVERTING","CONVERT_READY"->"准备签章文件";case "CREATING","SIGNING","POLLING"->"电子签章处理中";case "SIGNED"->"电子签章已完成";case "UNCERTAIN"->"待核对腾讯签章结果";case "FAILED"->"签章处理失败";default->"尚未发起签章";};}
 public Object start(UserAccount actor,Long id){hr(actor);var request=access(actor,id);if(request.getStatus()!=CertificateRequestStatus.GENERATED)throw AppException.badRequest("请先生成证明");return tx.execute(t->{
  var j=jobs.findById(id).orElse(null);if(j!=null&&!Set.of("WAITING_CONFIG","FAILED").contains(j.status))return view(j);
  if(j!=null&&j.flowId!=null)throw AppException.badRequest("已有电子签流程，请先在腾讯电子签核对处理结果");
  var config=configs.findById(actor.getTenantId()).filter(c->c.enabled).orElseThrow(()->AppException.badRequest("请先完成并启用电子签设置"));
  if(j==null){j=new CertificateSignJob();j.certificateId=id;j.tenantId=actor.getTenantId();}
  j.status="READY";j.encryptedConfig=config.encryptedConfig;j.error=null;j.failures=0;j.resourceId=null;j.taskId=null;j.updatedAt=LocalDateTime.now();jobs.save(j);audit.log(actor,"START_CERTIFICATE_ESIGN","employment_certificate_request",id,"发起腾讯电子签章");return view(j);
 });}
 @Scheduled(fixedDelay=20000,initialDelay=20000)
 public void tick(){
  for(var job:jobs.findTop10ByStatusInOrderByUpdatedAtAsc(Set.of("READY","UPLOADED","CONVERTING","CONVERT_READY","SIGNING","CREATING","UPLOADING","POLLING"))){
   if(Set.of("CREATING","UPLOADING","POLLING").contains(job.status)){
    if(job.updatedAt.isBefore(LocalDateTime.now().minusMinutes(3))){job.status=job.status.equals("CREATING")?"UNCERTAIN":job.flowId!=null?"SIGNING":"READY";job.error=job.status.equals("UNCERTAIN")?"请 HR 在腾讯电子签按证明编号核对流程；系统不会重复发起盖章。":null;save(job);}continue;
   }
   process(job);
  }
 }
 void process(CertificateSignJob candidate){
  String stage=candidate.status,claimed=stage.equals("CONVERT_READY")?"CREATING":stage.equals("SIGNING")?"POLLING":"UPLOADING";
  Integer locked=tx.execute(t->jobs.claim(candidate.certificateId,stage,claimed,candidate.version));if(locked==null||locked!=1)return;
  var j=jobs.findById(candidate.certificateId).orElseThrow();
  try{
   var c=settings(j.encryptedConfig);var operator=Map.of("UserId",c.operatorId());
   var request=requests.findByIdAndTenantId(j.certificateId,j.tenantId).orElseThrow();
   if(request.getStatus()!=CertificateRequestStatus.GENERATED)throw new IllegalStateException("证明文件状态已改变");
   if(stage.equals("READY")){
    byte[] content=documents.read(request.getGeneratedFileStorageKey());
    // A unique visible anchor is required before any cloud upload or stamp is attempted.
    try(var doc=new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(content))){final int[] count={0};CertificateTemplateFields.paragraphs(doc,p->{String text=p.getText();for(int i=text.indexOf(c.sealKeyword());i>=0;i=text.indexOf(c.sealKeyword(),i+c.sealKeyword().length()))count[0]++;});if(count[0]!=1)throw new IllegalStateException("证明中需要且仅能有一处盖章定位文字："+c.sealKeyword());}
    var result=client.call(c,"UploadFiles",Map.of("BusinessType","DOCUMENT","FileType","docx","Caller",Map.of("OperatorId",c.operatorId()),"FileInfos",List.of(Map.of("FileName",request.getGeneratedFileName(),"FileBody",Base64.getEncoder().encodeToString(content)))));
    j.resourceId=required(result.path("FileIds").path(0).asText(),200);j.status="UPLOADED";
   }else if(stage.equals("UPLOADED")){
    var result=client.call(c,"CreateFileConvertTask",Map.of("Operator",operator,"ResourceType","docx","ResourceName",request.getGeneratedFileName(),"ResourceId",j.resourceId));j.taskId=required(result.path("TaskId").asText(),200);j.status="CONVERTING";
   }else if(stage.equals("CONVERTING")){
    var result=client.call(c,"DescribeFileConvertTask",Map.of("Operator",operator,"TaskId",j.taskId));int status=result.path("TaskStatus").asInt(-99);if(status<0)throw new IllegalStateException("腾讯电子签文档转换失败（"+status+"）");j.status="CONVERTING";if(status==8){j.resourceId=required(result.path("ResourceId").asText(),200);j.status="CONVERT_READY";}
   }else if(stage.equals("CONVERT_READY")){
    var component=new LinkedHashMap<String,Object>();component.put("ComponentType","SIGN_SEAL");component.put("ComponentValue",c.sealId());component.put("FileIndex",0);component.put("GenerateMode","KEYWORD");component.put("ComponentId",c.sealKeyword());component.put("KeywordOrder","Positive");component.put("RelativeLocation","Middle");component.put("ComponentWidth",119);component.put("ComponentHeight",119);
    var result=client.call(c,"CreateFlowByFiles",Map.of("Operator",operator,"FlowName",flowName(j),"FlowType","在职证明","FileIds",List.of(j.resourceId),"Approvers",List.of(Map.of("ApproverType",3,"OrganizationName",c.organizationName(),"NotifyType","NONE","SignComponents",List.of(component)))));
    j.flowId=required(result.path("FlowId").asText(),200);j.status="SIGNING";
   }else if(stage.equals("SIGNING")){
    var result=client.call(c,"DescribeFlowBriefs",Map.of("Operator",operator,"FlowIds",List.of(j.flowId)));boolean found=false;
    for(var flow:result.path("FlowBriefs"))if(j.flowId.equals(flow.path("FlowId").asText())){found=true;int state=flow.path("FlowStatus").asInt(-1);j.status=state==4?"SIGNED":Set.of(3,5,6,10,16,21).contains(state)?"FAILED":"SIGNING";if(j.status.equals("FAILED"))j.error="腾讯签署流程已结束，状态码："+state;}
    if(!found)throw new IllegalStateException("腾讯电子签未返回对应流程，请 HR 核对");
   }
   j.failures=0;if(!j.status.equals("FAILED"))j.error=null;
  }catch(Exception e){
   j.failures++;j.error=e instanceof TencentEssClient.ApiFailure?e.getMessage():stage.equals("CONVERT_READY")?"创建流程结果待核对，请 HR 在腾讯电子签按证明编号查询。":e.getMessage();
   if(stage.equals("CONVERT_READY"))j.status=e instanceof TencentEssClient.ApiFailure failure && failure.definiteRejection()?"FAILED":"UNCERTAIN";
   else j.status=j.failures>=3||stage.equals("READY")?"FAILED":stage;
  }
  save(j);
 }
 public Object reconcile(UserAccount actor,Long id,String flowId){hr(actor);access(actor,id);var j=jobs.findById(id).orElseThrow(()->AppException.badRequest("尚无签章任务"));if(!"UNCERTAIN".equals(j.status))throw AppException.badRequest("仅待核对任务可以关联腾讯流程");var c=settings(j.encryptedConfig);String selected=required(flowId,200);var result=client.call(c,"DescribeFlowBriefs",Map.of("Operator",Map.of("UserId",c.operatorId()),"FlowIds",List.of(selected)));boolean match=false;for(var f:result.path("FlowBriefs"))if(selected.equals(f.path("FlowId").asText())&&flowName(j).equals(f.path("FlowName").asText())&&c.operatorId().equals(f.path("Creator").asText()))match=true;if(!match)throw AppException.badRequest("该腾讯流程与当前证明编号或经办人不匹配");j.flowId=selected;j.status="SIGNING";j.failures=0;j.error=null;save(j);audit.log(actor,"RECONCILE_CERTIFICATE_ESIGN","employment_certificate_request",id,"关联腾讯流程 "+selected);return view(j);}
 public byte[] download(UserAccount actor,Long id){access(actor,id);var j=jobs.findById(id).filter(job->"SIGNED".equals(job.status)).orElseThrow(()->AppException.badRequest("电子签章尚未完成"));var c=settings(j.encryptedConfig);var result=client.call(c,"DescribeFileUrls",Map.of("Operator",Map.of("UserId",c.operatorId()),"BusinessType","FLOW","BusinessIds",List.of(j.flowId),"FileType","PDF","UrlTtl",300));String url=result.path("FileUrls").path(0).path("Url").asText();if(blank(url))throw AppException.badRequest("签章文件正在合成，请稍后下载");byte[] data=client.download(url);audit.log(actor,"DOWNLOAD_SIGNED_CERTIFICATE","employment_certificate_request",id,"下载腾讯电子签章 PDF");return data;}
 private String flowName(CertificateSignJob j){return "HR在职证明-"+j.tenantId+"-"+j.certificateId;}
 private void save(CertificateSignJob j){j.updatedAt=LocalDateTime.now();if(j.error!=null&&j.error.length()>300)j.error=j.error.substring(0,300);jobs.saveAndFlush(j);}
 private EmploymentCertificateRequest access(UserAccount actor,Long id){var r=requests.findByIdAndTenantId(id,actor.getTenantId()).orElseThrow(()->AppException.notFound("证明申请不存在"));if(!r.getEmployeeId().equals(actor.getId())&&actor.getRole()!=Role.HR)throw AppException.forbidden("只能查看自己的证明签章");return r;}
 private void hr(UserAccount u){if(u.getRole()!=Role.HR)throw AppException.forbidden("只有 HR 可以管理证明电子签");}
 private boolean blank(String s){return s==null||s.isBlank();}
 private String required(String s,int limit){if(blank(s)||s.length()>limit)throw AppException.badRequest("请填写完整有效的电子签参数");return s.trim();}
 private TencentEssClient.Settings settings(String encrypted){try{return json.readValue(crypto.decrypt(encrypted),TencentEssClient.Settings.class);}catch(Exception e){throw new IllegalStateException("电子签配置读取失败",e);}}
 private String encode(Object c){try{return crypto.encrypt(json.writeValueAsString(c));}catch(Exception e){throw new IllegalStateException(e);}}
}
