package com.hragent.hragentv1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmploymentCertificateTemplateDtos.TemplatePreview;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.web.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CertificateAttachmentConversationTest {
 final LifecycleRequestRepository cases=mock(LifecycleRequestRepository.class);
 final EmploymentCertificateService certificates=mock(EmploymentCertificateService.class);
 final EmploymentCertificateTemplateService templates=mock(EmploymentCertificateTemplateService.class);
 final EmployeeRelationsService relations=mock(EmployeeRelationsService.class);
 final SecretCryptoService crypto=new SecretCryptoService("attachment-test-key-32-characters-long");
 final LifecycleService service=new LifecycleService(cases,mock(EmployeePayslipRepository.class),mock(UserAccountRepository.class),mock(LeaveRequestRepository.class),mock(LeaveService.class),relations,crypto,new ObjectMapper(),mock(AuditService.class),certificates,mock(EmploymentCertificateRequestRepository.class),mock(LeaveMedicalService.class));
 final UserAccount employee=LifecycleServiceTest.user(1L,1L,Role.EMPLOYEE);
 final List<LifecycleRequest> saved=new ArrayList<>();
 final MockMultipartFile file=new MockMultipartFile("file","在职证明-自备模板-演示示例.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",new byte[]{1});
 @BeforeEach void setup(){
  ReflectionTestUtils.setField(service,"certificateTemplates",templates);
  ReflectionTestUtils.setField(service,"certificatePreparation",preparation(employee,crypto));
  when(templates.download(any(),eq(55L))).thenReturn(new EmploymentCertificateTemplateService.TemplateDownload("个人模板.docx","application/docx",new byte[]{1}));
  when(cases.save(any())).thenAnswer(a->{LifecycleRequest c=a.getArgument(0);if(c.id==null){c.id=(long)saved.size()+1;saved.add(c);}return c;});
  when(cases.findFirstByTenantIdAndEmployeeIdAndConversationIdOrderByIdDesc(anyLong(),anyLong(),anyString())).thenAnswer(a->saved.stream().filter(c->c.tenantId.equals(a.getArgument(0))&&c.employeeId.equals(a.getArgument(1))&&c.conversationId.equals(a.getArgument(2))).reduce((x,y)->y));
  when(cases.findByIdAndTenantId(anyLong(),anyLong())).thenAnswer(a->saved.stream().filter(c->c.id.equals(a.getArgument(0))&&c.tenantId.equals(a.getArgument(1))).findFirst());
  when(templates.preview(any(),any())).thenReturn(new TemplatePreview(file.getOriginalFilename(),100,true,true,true,List.of("{{legalName}}","{{接收单位}}"),List.of("{{接收单位}}"),List.of()));
  when(templates.uploadProposal(any(),any(),anyString(),any(),any(),any())).thenAnswer(a->{var t=new EmploymentCertificateTemplate();t.setId(55L);return t;});
 }
 static CertificateTemplatePreparationService preparation(UserAccount employee,SecretCryptoService crypto){
  employee.setDepartment("研发部");employee.setTitle("工程师");employee.setEntryDate(java.time.LocalDate.of(2024,1,1));
  var profiles=mock(EmployeePersonalProfileRepository.class);var tenants=mock(TenantRepository.class);var profile=new EmployeePersonalProfile();profile.setLegalName("测试员工");
  when(profiles.findByTenantIdAndEmployeeId(anyLong(),anyLong())).thenReturn(Optional.of(profile));var tenant=new Tenant();tenant.setName("测试企业");when(tenants.findById(anyLong())).thenReturn(Optional.of(tenant));
  var preparation=spy(new CertificateTemplatePreparationService(profiles,tenants,crypto));doReturn(CertificateLanguage.CHINESE).when(preparation).language(any());return preparation;
 }
 String say(String message){return service.reply(employee,message,"test").orElseThrow();}
 @Test void screenshotUploadGoesStraightToLocalDraftWithoutGatewayOrSubmission(){
  var auth=mock(AuthService.class);var gateway=mock(WebChatGatewayService.class);var workspace=mock(AssistantWorkspaceService.class);var request=mock(jakarta.servlet.http.HttpServletRequest.class);
  when(auth.requireUser(request)).thenReturn(employee);
  var controller=new WebChatController(auth,gateway,service,workspace);
  var result=controller.chatWithAttachment(request,file,"帮我办一下在职证明","test").data();
  assertThat(result.provider()).isEqualTo("hrssc-lifecycle");assertThat(result.answer()).contains("模板已读取","接收单位").doesNotContain("第 1 /","文件语言？");
  assertThat(result.actions()).anyMatch(a->a.type().equals("certificate-template"));
  assertThat(saved).hasSize(1);assertThat(saved.getFirst().status).isEqualTo("DRAFT");assertThat(saved.getFirst().certificateTemplateId).isEqualTo(55L);
  verify(gateway,never()).chatWithAttachment(any(),any(),any());verifyNoInteractions(workspace,certificates);
  assertThatThrownBy(()->say("确认提交")).hasMessageContaining("接收单位");verifyNoInteractions(certificates);
  var created=mock(com.hragent.hragentv1.dto.EmploymentCertificateDtos.RequestView.class);when(created.id()).thenReturn(99L);when(certificates.create(eq(employee),any())).thenReturn(created);
  var submitted=service.confirmCertificateTemplate(employee,1L,Map.of("接收单位","资格审核中心"));
  assertThat(submitted.answer()).contains("已提交","HR 审核");assertThat(saved.getFirst().status).isEqualTo("SUBMITTED");
  service.confirmCertificateTemplate(employee,1L,Map.of("接收单位","资格审核中心"));verify(certificates,times(1)).create(eq(employee),argThat(r->r.requestedTemplateId().equals(55L)&&r.templateValues().get("接收单位").equals("资格审核中心")));

 }
 @Test void uploadDuringDraftRetainsAnswersAndDoesNotTreatCaptionAsPurpose(){
  say("我要开在职证明");say("资格审核");
  var result=service.certificateAttachment(employee,"test",file,"这是模板").orElseThrow();
  assertThat(saved).hasSize(1);assertThat(result.answer()).contains("接收单位").doesNotContain("中文、英文");
  assertThat(crypto.decrypt(saved.getFirst().encryptedFields)).contains("资格审核").doesNotContain("这是模板");verifyNoInteractions(certificates);
 }
 @Test void previouslyConfirmedTemplateContinuesWithMissingItemsInsteadOfFiveStepForm(){
  say("我要开在职证明");say("资格审核");var c=saved.getFirst();c.certificateTemplateId=55L;c.certificateTemplateConfirmed=true;c.encryptedCertificateValues=crypto.encrypt("{\"接收单位\":\"演示单位\"}");
  assertThat(say("继续办理")).contains("资料齐了").doesNotContain("第 2 /","中文、英文");
  var created=mock(com.hragent.hragentv1.dto.EmploymentCertificateDtos.RequestView.class);when(created.id()).thenReturn(99L);when(certificates.create(eq(employee),any())).thenReturn(created);
  assertThat(say("确认提交")).contains("已提交");verify(certificates,times(1)).create(eq(employee),argThat(r->r.purpose().equals("资格审核")&&r.templateValues().get("接收单位").equals("演示单位")));
 }
 @Test void invalidTemplateDoesNotCreateAnEmptyDraft(){
  when(templates.preview(any(),any())).thenThrow(AppException.badRequest("Word 模板无法读取"));
  assertThatThrownBy(()->service.certificateAttachment(employee,"test",file,"帮我办一下在职证明")).hasMessageContaining("无法读取");
  assertThat(saved).isEmpty();verify(templates,never()).uploadProposal(any(),any(),any(),any(),any(),any());
 }
 @Test void existingOtherBusinessIsNotOverwrittenByUpload(){
  say("我要申请请假");String before=saved.getFirst().encryptedFields;
  var result=service.certificateAttachment(employee,"test",file,"帮我办一下在职证明").orElseThrow();
  assertThat(result.answer()).contains("附件还没有保存");assertThat(saved).hasSize(1);assertThat(saved.getFirst().encryptedFields).isEqualTo(before);verifyNoInteractions(templates,certificates);
 }
 @Test void summaryRequestDoesNotStartBusinessFromFilename(){
  assertThat(service.certificateAttachment(employee,"test",file,"请总结一下这份文件")).isEmpty();assertThat(saved).isEmpty();verifyNoInteractions(templates);
 }
 @Test void tenantAndConversationDoNotAttachToAnotherDraft(){
  say("我要开收入证明");
  var other=LifecycleServiceTest.user(1L,2L,Role.EMPLOYEE);
  service.certificateAttachment(other,"test",file,"帮我办一下在职证明").orElseThrow();
  service.certificateAttachment(employee,"other",file,"帮我办一下在职证明").orElseThrow();
  assertThat(saved).hasSize(3);assertThat(saved.getFirst().certificateTemplateId).isNull();assertThat(saved.get(1).tenantId).isEqualTo(2L);assertThat(saved.get(2).conversationId).isEqualTo("other");
 }
 @Test void inactiveEmployeeCannotCreateCertificateFromAttachment(){
  employee.setEmployeeStatus(EmployeeStatus.LEFT);
  assertThatThrownBy(()->service.certificateAttachment(employee,"test",file,"帮我办一下在职证明")).hasMessageContaining("在职员工档案");assertThat(saved).isEmpty();verifyNoInteractions(templates);
 }
 @Test void safetyReplyAndCancellationDoNotProcessFile(){
  when(relations.triage(employee,"帮我办在职证明，我想伤害自己")).thenReturn(Optional.of("先确保你现在安全"));
  var safety=service.certificateAttachment(employee,"test",file,"帮我办在职证明，我想伤害自己").orElseThrow();assertThat(safety.provider()).isEqualTo("er-human-support");assertThat(saved).isEmpty();
  say("我要开在职证明");assertThat(service.certificateAttachment(employee,"test",file,"不办理了").orElseThrow().answer()).contains("已取消");assertThat(saved.getFirst().status).isEqualTo("CANCELLED");verifyNoInteractions(templates,certificates);
 }
}
