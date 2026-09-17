package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;

/** Read-only profile completion. Applicant supplements belong to the request, never to the HR master record. */
@Service
public class CertificateTemplatePreparationService {
 public static final Set<String> SUPPLEMENTABLE=Set.of("legalName","englishName","department","title","entryDate","passportNumber","passportExpiryDate","monthlySalary","currency");
 private final EmployeePersonalProfileRepository profiles;
 private final TenantRepository tenants;
 private final SecretCryptoService crypto;
 public CertificateTemplatePreparationService(EmployeePersonalProfileRepository profiles,TenantRepository tenants,SecretCryptoService crypto){this.profiles=profiles;this.tenants=tenants;this.crypto=crypto;}
 public record MissingField(String key,String label,boolean profileSupplement){}
 public record Prepared(Map<String,String> automatic,List<MissingField> missingFields,Map<String,String> values,List<String> required){}
 private static String str(Object v){return v==null?"":v.toString();}
 public Map<String,String> profileValues(UserAccount employee){
  var p=profiles.findByTenantIdAndEmployeeId(employee.getTenantId(),employee.getId()).orElseGet(EmployeePersonalProfile::new);
  var v=new LinkedHashMap<String,String>();
  v.put("legalName",str(p.getLegalName()));v.put("englishName",str(p.getEnglishName()));v.put("employeeNo",str(employee.getEmployeeNo()));
  v.put("department",str(employee.getDepartment()));v.put("title",str(employee.getTitle()));v.put("entryDate",str(employee.getEntryDate()!=null?employee.getEntryDate():p.getContractStartDate()));
  v.put("passportNumber",p.getPassportNumberEncrypted()==null?"":str(crypto.decrypt(p.getPassportNumberEncrypted())));v.put("passportExpiryDate",str(p.getPassportExpiryDate()));
  v.put("monthlySalary",str(p.getMonthlySalary()));v.put("currency",p.getCurrency()==null?"CNY":p.getCurrency());
  v.put("companyName",tenants.findById(employee.getTenantId()).map(Tenant::getName).orElse(""));v.put("issueDate",LocalDate.now().toString());return v;
 }
 public Prepared prepare(UserAccount u,String kind,List<String> placeholders,Map<String,String> form,Map<String,String> supplied){
  var required=new LinkedHashSet<String>();placeholders.forEach(k->required.add(k.replace("{{","").replace("}}","")));
  required.addAll(List.of("legalName","department","title","entryDate"));
  if(kind.equals("VISA_CERT"))required.addAll(List.of("englishName","passportNumber","passportExpiryDate","destinationCountry","consulateName"));
  if(kind.equals("INCOME_CERT")||"需要".equals(form.get("是否包含薪资")))required.add("monthlySalary");
  var automatic=profileValues(u);automatic.put("purpose",form.getOrDefault("证明用途",""));automatic.put("destinationCountry",form.getOrDefault("目的国家",""));automatic.put("consulateName",form.getOrDefault("受理机构",""));
  var missing=new ArrayList<MissingField>();var values=new LinkedHashMap<String,String>();
  for(String key:required){if(!automatic.getOrDefault(key,"").isBlank())continue;
   String candidate=supplied.get(key);if(candidate!=null&&!candidate.isBlank()){validateValue(key,candidate);values.put(key,candidate.trim());}
   missing.add(new MissingField(key,CertificateTemplateFields.LABELS.getOrDefault(key,key),SUPPLEMENTABLE.contains(key)));
  }
  automatic.entrySet().removeIf(e->!required.contains(e.getKey())||e.getValue().isBlank());
  return new Prepared(automatic,missing,values,List.copyOf(required));
 }
 public void validateSupplement(UserAccount u,String key,String value){
  if(!SUPPLEMENTABLE.contains(key)||!profileValues(u).getOrDefault(key,"").isBlank())throw AppException.badRequest("员工档案字段由系统填入，请勿覆盖");
  validateValue(key,value);
 }
 public static void validateValue(String key,String v){
  if(v==null||v.isBlank()||v.length()>1000||v.contains("{{")||v.contains("${")||v.contains("【"))throw AppException.badRequest("请补充有效的模板字段："+CertificateTemplateFields.LABELS.getOrDefault(key,key));
  try{if(Set.of("entryDate","passportExpiryDate").contains(key))LocalDate.parse(v.trim());if(key.equals("monthlySalary")&&new BigDecimal(v.trim()).signum()<0)throw new IllegalArgumentException();}catch(Exception e){throw AppException.badRequest(key.equals("monthlySalary")?"月薪请填写有效金额":"日期请填写为 YYYY-MM-DD，例如 2026-09-15");}
  int max=switch(key){case "purpose"->200;case "destinationCountry"->100;case "consulateName"->160;default->1000;};if(v.length()>max)throw AppException.badRequest("字段内容过长："+CertificateTemplateFields.LABELS.getOrDefault(key,key));
 }
 public CertificateLanguage language(MultipartFile file){
  try(var doc=new XWPFDocument(file.getInputStream())){var text=new StringBuilder();CertificateTemplateFields.paragraphs(doc,p->text.append(p.getText()).append(' '));String s=text.toString().replaceAll("\\{\\{.*?}}|【.*?】|\\$\\{.*?}","");
   boolean english=s.replaceAll("[^A-Za-z]","").length()>35,chinese=s.matches("(?s).*[\\u4e00-\\u9fff].*");return english?(chinese?CertificateLanguage.BILINGUAL:CertificateLanguage.ENGLISH):CertificateLanguage.CHINESE;
  }catch(Exception e){throw AppException.badRequest("Word 模板无法读取，请上传有效的 DOCX 文件");}
 }
}
