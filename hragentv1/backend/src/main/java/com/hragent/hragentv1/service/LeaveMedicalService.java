package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class LeaveMedicalService {
 public static final String SUMMARY="已完成文字识别与材料初检；姓名、医疗机构和日期信息可识别。真实性、医嘱及请假期限仍需主管与 HR 核验。";
 private final LeaveMedicalRecordRepository records;
 private final LeaveRequestRepository leaves;
 private final EmployeePersonalProfileRepository profiles;
 private final MedicalTextParser parser;
 private final SecretCryptoService crypto;
 private final AuditService audit;
 public LeaveMedicalService(LeaveMedicalRecordRepository records,LeaveRequestRepository leaves,EmployeePersonalProfileRepository profiles,MedicalTextParser parser,SecretCryptoService crypto,AuditService audit){this.records=records;this.leaves=leaves;this.profiles=profiles;this.parser=parser;this.crypto=crypto;this.audit=audit;}
 public record Scan(Long id,String summary){}
 public record Document(byte[] bytes,String mediaType,String fileName){}
 @Transactional public Scan upload(UserAccount u,MultipartFile file,LocalDate start,LocalDate end){
  if(u.getEmployeeStatus()!=EmployeeStatus.ACTIVE||u.getRole()==Role.NEW_HIRE)throw AppException.forbidden("请先完成入职建档");
  if(start==null||end==null||end.isBefore(start))throw AppException.badRequest("请先填写病假的开始和结束日期");
  byte[] bytes;try{bytes=file.getBytes();}catch(Exception e){throw AppException.badRequest("文件读取失败，请重新上传");}
  String ext=extension(bytes);
  String text=parser.extract(bytes,ext);
  String name=profiles.findByTenantIdAndEmployeeId(u.getTenantId(),u.getId()).map(EmployeePersonalProfile::getLegalName).filter(n->!n.isBlank()).orElse(u.getName());
  check(text,name,start,end);
  var r=new LeaveMedicalRecord();r.tenantId=u.getTenantId();r.employeeId=u.getId();r.startDate=start;r.endDate=end;r.extension=ext;r.mediaType=ext.equals("pdf")?"application/pdf":"image/"+(ext.equals("jpg")?"jpeg":ext);
  r.encryptedFile=crypto.encrypt(Base64.getEncoder().encodeToString(bytes));r.encryptedText=crypto.encrypt(text);records.save(r);
  audit.log(u,"MEDICAL_SCAN","leave_medical_record",r.id,"材料初检完成，等待员工确认提交");return new Scan(r.id,SUMMARY);
 }
 static String extension(byte[] b){
  if(b.length<8||b.length>5*1024*1024)throw AppException.badRequest("请上传不超过 5 MB 的 PDF、JPG 或 PNG 病历");
  if(new String(b,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))return "pdf";
  if((b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "jpg";
  if(Arrays.equals(Arrays.copyOf(b,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))return "png";
  throw AppException.badRequest("文件内容不是支持的 PDF、JPG 或 PNG，请重新选择原始病历文件");
 }
 static void check(String raw,String name,LocalDate start,LocalDate end){
  String t=raw.replaceAll("\\s+","");
  if(t.length()<25||!Pattern.compile("病历|病程记录|诊断证明|疾病证明|病休证明|出院记录|出院小结").matcher(t).find()||!Pattern.compile("医院|卫生院|卫生服务中心|门诊部|诊所").matcher(t).find()||!Pattern.compile("诊断|医嘱|处理意见|建议休息|病情").matcher(t).find())throw AppException.badRequest("还没能确认这是一份完整病历或诊断证明。请上传包含医疗机构、姓名、就诊日期和诊疗信息的清晰材料；挂号单或付款截图暂不能替代。");
  if(name==null||name.isBlank()||!Pattern.compile("(?:患者姓名|病人姓名|姓名|患者)[:：]?"+Pattern.quote(name.replaceAll("\\s+",""))+"(?=性别|年龄|男|女|[0-9]|就诊|科室|门诊|住院|出生|病历|诊断|$)",Pattern.CASE_INSENSITIVE).matcher(t).find())throw AppException.badRequest("病历上的患者姓名与员工档案暂时没有匹配上。请检查照片是否清晰；若档案姓名需更正，请先联系 HR 核实。");
  var date=Pattern.compile("(?:就诊日期|就诊时间|诊疗日期|诊断日期|开具日期|入院日期|日期)[:：]?(20\\d{2})[-年/.](\\d{1,2})[-月/.](\\d{1,2})").matcher(t);
  if(!date.find())throw AppException.badRequest("没有清楚识别到就诊或开具日期，请补充包含日期的病历照片。");
  try {var d=LocalDate.of(Integer.parseInt(date.group(1)),Integer.parseInt(date.group(2)),Integer.parseInt(date.group(3)));if(d.isAfter(EmployeeReminderService.today())||d.isAfter(end))throw new IllegalArgumentException();}
  catch(Exception e){throw AppException.badRequest("材料日期与当前申请暂时对不上，请核对就诊日期和请假日期后重新上传。");}
 }
 @Transactional public void validate(UserAccount u,Long id,LocalDate start,LocalDate end){validateRecord(u,id,start,end,false);}
 private LeaveMedicalRecord validateRecord(UserAccount u,Long id,LocalDate start,LocalDate end,boolean lock){
  if(id==null)throw AppException.badRequest("身体不舒服就先照顾好自己。病假提交前，请上传病历或诊断证明完成材料初检。");
  var r=(lock?records.lockById(id):records.findById(id)).orElseThrow(()->AppException.notFound("病假材料不存在"));
  if(!r.tenantId.equals(u.getTenantId())||!r.employeeId.equals(u.getId()))throw AppException.forbidden("只能使用本人的病假材料");
  if(r.leaveRequestId!=null)throw AppException.badRequest("该材料已随另一份申请提交，请为本次申请重新上传");
  if(!r.startDate.equals(start)||!r.endDate.equals(end))throw AppException.badRequest("请假日期已变化，请重新上传病历核对本次申请");
  return r;
 }
 @Transactional public void bind(UserAccount u,Long id,LocalDate start,LocalDate end,Long leaveId){var r=validateRecord(u,id,start,end,true);r.leaveRequestId=leaveId;records.save(r);}
 @Transactional public Document download(UserAccount u,Long leaveId){
  var leave=leaves.findById(leaveId).filter(l->l.getTenantId().equals(u.getTenantId())).orElseThrow(()->AppException.notFound("申请不存在"));
  boolean owner=leave.getEmployeeId().equals(u.getId());
  boolean reviewer=!owner&&u.getEmployeeStatus()==EmployeeStatus.ACTIVE&&((u.getRole()==Role.MANAGER&&leave.getManagerId().equals(u.getId()))||u.getRole()==Role.HR);
  if(!owner&&!reviewer)throw AppException.forbidden("仅本人和有权限的审核人员可查看病假材料");
  var r=Optional.ofNullable(leave.getMedicalRecordId()).flatMap(records::findById).filter(m->m.tenantId.equals(u.getTenantId())&&leaveId.equals(m.leaveRequestId)).orElseThrow(()->AppException.notFound("这份申请没有病假材料"));
  audit.log(u,"MEDICAL_READ","leave_request",leaveId,"查看审核材料");return new Document(Base64.getDecoder().decode(crypto.decrypt(r.encryptedFile)),r.mediaType,"病假材料-"+leaveId+"."+r.extension);
 }
}
