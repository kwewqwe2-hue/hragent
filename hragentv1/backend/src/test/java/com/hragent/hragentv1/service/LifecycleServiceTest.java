package com.hragent.hragentv1.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.dto.LeaveDtos;
import com.hragent.hragentv1.web.AppException;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class LifecycleServiceTest {
 final LifecycleRequestRepository cases=mock(LifecycleRequestRepository.class);
 final EmployeePayslipRepository pay=mock(EmployeePayslipRepository.class);
 final UserAccountRepository users=mock(UserAccountRepository.class);
 final LeaveRequestRepository requests=mock(LeaveRequestRepository.class);
 final LeaveService leaves=mock(LeaveService.class);
 final EmployeeRelationsService relations=mock(EmployeeRelationsService.class);
 final SecretCryptoService crypto=new SecretCryptoService("lifecycle-test-key-32-characters-long");
 final EmploymentCertificateService certificates=mock(EmploymentCertificateService.class);
 final EmploymentCertificateRequestRepository certificateRequests=mock(EmploymentCertificateRequestRepository.class);
 final LeaveMedicalService medical=mock(LeaveMedicalService.class);
 final LifecycleService service=new LifecycleService(cases,pay,users,requests,leaves,relations,crypto,new ObjectMapper(),mock(AuditService.class),certificates,certificateRequests,medical);
 final List<LifecycleRequest> saved=new ArrayList<>();
 final UserAccount employee=user(1L,1L,Role.EMPLOYEE),hr=user(2L,1L,Role.HR);
 static UserAccount user(Long id,Long tenant,Role role){var u=new UserAccount();u.setId(id);u.setTenantId(tenant);u.setRole(role);u.setName("测试员工");u.setEmployeeNo("T"+id);u.setEmployeeStatus(EmployeeStatus.ACTIVE);return u;}
 @BeforeEach void setup(){
  org.springframework.test.util.ReflectionTestUtils.setField(service,"certificatePreparation",CertificateAttachmentConversationTest.preparation(employee,crypto));
  when(cases.save(any())).thenAnswer(a->{LifecycleRequest c=a.getArgument(0);if(c.id==null){c.id=(long)saved.size()+1;saved.add(c);}return c;});
  when(cases.findFirstByTenantIdAndEmployeeIdAndConversationIdOrderByIdDesc(anyLong(),anyLong(),anyString())).thenAnswer(a->saved.stream().filter(c->c.tenantId.equals(a.getArgument(0))&&c.employeeId.equals(a.getArgument(1))&&c.conversationId.equals(a.getArgument(2))).reduce((x,y)->y));
  when(cases.findByIdAndTenantId(anyLong(),anyLong())).thenAnswer(a->saved.stream().filter(c->c.id.equals(a.getArgument(0))&&c.tenantId.equals(a.getArgument(1))).findFirst());
  when(users.findById(1L)).thenReturn(Optional.of(employee));
 }
 String say(String text){return service.reply(employee,text,"test").orElseThrow();}
 @Test void sideQuestionsNeverBecomeCertificatePurposeOrLeaveDate(){
  say("我要开在职证明");var before=saved.getFirst().encryptedFields;
  for(String q:List.of("多久能办好？","需要哪些材料","下载后打不开","审核进度怎么样")){
   assertThat(service.reply(employee,q,"test")).as(q).isEmpty();assertThat(saved.getFirst().encryptedFields).isEqualTo(before);
  }
  assertThat(say("资格审核")).contains("中文");verifyNoInteractions(certificates);
 }
 @Test void controllerCarriesBusinessTopicAcrossNaturalFollowupsAndDraftSteps(){
  var followups=new ServiceConversationGuide();var policy=new PolicyConversationGuide();var auth=mock(AuthService.class);var gateway=mock(WebChatGatewayService.class);var workspace=mock(AssistantWorkspaceService.class);
  var servlet=mock(jakarta.servlet.http.HttpServletRequest.class);when(auth.requireLifecycleUser(servlet)).thenReturn(employee);
  when(gateway.resolveServiceFollowup(any(),anyString(),anyString())).thenAnswer(a->followups.command(a.getArgument(0),a.getArgument(1),a.getArgument(2)).orElse(a.getArgument(1)));
  when(gateway.serviceGuidance(any(),anyString(),anyString())).thenAnswer(a->followups.guidance(a.getArgument(0),a.getArgument(1),a.getArgument(2)));
  when(gateway.policyConversation(any(),anyString(),anyString())).thenAnswer(a->policy.interviewRelevant(a.getArgument(0),a.getArgument(1),a.getArgument(2))?policy.opening(a.getArgument(0),a.getArgument(1),a.getArgument(2)):Optional.empty());
  doAnswer(a->{followups.observe(a.getArgument(0),a.getArgument(1),a.getArgument(2),a.getArgument(3));return null;}).when(gateway).rememberService(any(),anyString(),anyString(),any());
  doAnswer(a->{policy.clear(a.getArgument(0),a.getArgument(1));return null;}).when(gateway).clearPolicyConversation(any(),anyString());
  var controller=new com.hragent.hragentv1.web.WebChatController(auth,gateway,service,workspace);
  java.util.function.Function<String,com.hragent.hragentv1.dto.WebChatDtos.MessageResponse> chat=q->controller.chat(servlet,new com.hragent.hragentv1.dto.WebChatDtos.MessageRequest(q,"test")).data();
  chat.apply("我想了解一下产假");chat.apply("上海");var guidance=chat.apply("我应该怎么申请");assertThat(guidance.actions()).anyMatch(a->a.value().equals("我要申请产假"));assertThat(saved).isEmpty();
  assertThat(chat.apply("帮我填写").answer()).contains("计划从哪天");assertThat(saved).hasSize(1);
  assertThat(chat.apply("下一步呢").answer()).contains("计划从哪天");chat.apply("9.15-9.17");chat.apply("确认日期");chat.apply("休假准备");
  assertThat(chat.apply("下一步呢").answer()).contains("请核对这份产假申请");assertThat(saved).hasSize(1);assertThat(saved.get(0).status).isEqualTo("DRAFT");verifyNoInteractions(leaves);
 }
 @Test void unsupportedDirectOperationsBecomeExplicitHrAssistanceRequests(){
  var r=service.guidedReply(employee,"我要办理报销协助","test").orElseThrow();assertThat(r.answer()).contains("协助","HRSSC");assertThat(saved.get(0).kind).isEqualTo("SERVICE_HELP");assertThat(crypto.decrypt(saved.get(0).encryptedFields)).contains("报销");
  say("差旅费用材料准备");say("需要材料清单");assertThat(saved.get(0).status).isEqualTo("DRAFT");say("确认提交");assertThat(saved.get(0).status).isEqualTo("SUBMITTED");assertThat(saved.get(0).leaveRequestId).isNull();verifyNoInteractions(leaves);
 }
 @Test void maternityGuidanceStartsDateFormAndOnlyExplicitConfirmationCreatesHrReview(){
  var policy=new PolicyConversationGuide();policy.opening(employee,"产假","test");policy.opening(employee,"上海","test");
  var guidance=policy.opening(employee,"我应该怎么申请","test").orElseThrow();assertThat(saved).isEmpty();
  var start=guidance.actions().stream().filter(a->a.value().equals("我要申请产假")).findFirst().orElseThrow();
  var response=service.guidedReply(employee,start.value(),"test").orElseThrow();
  assertThat(response.answer()).contains("产假申请","计划从哪天").doesNotContain("你要请哪种假");assertThat(saved).hasSize(1);var draft=saved.get(0);
  assertThat(draft.kind).isEqualTo("MATERNITY_LEAVE");assertThat(draft.status).isEqualTo("DRAFT");assertThat(crypto.decrypt(draft.encryptedFields)).contains("产假");
  assertThat(say("26.9.15-27.2.19")).contains("请确认日期","2026-09-15","2027-02-19");
  assertThat(say("确认日期")).contains("简单说一下");say("计划生育休假安排");
  assertThat(draft.status).isEqualTo("DRAFT");assertThat(say("确认提交")).contains("HRSSC","产假申请");assertThat(draft.status).isEqualTo("SUBMITTED");
  assertThat(say("确认提交")).contains("无需重复提交");assertThat(saved).hasSize(1);assertThat(draft.leaveRequestId).isNull();verifyNoInteractions(leaves,medical);
  service.review(hr,draft.id,"APPROVED","安排已核对");assertThat(say("查询服务单1")).contains("审核通过，办理中","安排已核对");
  service.review(hr,draft.id,"COMPLETED","已完成休假安排");assertThat(say("查询服务单1")).contains("已办结","已完成休假安排");
 }
 @Test void maternityStillSupportsCorrectionsCancellationAndIdentityChecks(){
  say("我想请产假");var draft=saved.get(0);assertThat(say("上一步")).contains("这份是产假申请");
  assertThat(say("9/31")).contains("这个月没有");say("9.15-9.17");say("确认日期");
  say("修改开始日期");assertThat(crypto.decrypt(draft.encryptedFields)).doesNotContain("2026-09-15","2026-09-17");
  say("不办理了");assertThat(draft.status).isEqualTo("CANCELLED");assertThat(say("确认提交")).contains("无需重复提交");verifyNoInteractions(leaves);
  var former=user(8L,1L,Role.EMPLOYEE);former.setEmployeeStatus(EmployeeStatus.LEFT);
  assertThat(service.reply(former,"我要申请产假","former").orElseThrow()).contains("离职员工");assertThat(saved).hasSize(1);
 }
 @Test void compactRangeRequiresConfirmationThenSkipsToReasonAndCanBeStopped(){
  say("我想请事假");String before=saved.get(0).encryptedFields;
  var r=service.guidedReply(employee,"915-917","test").orElseThrow();
  assertThat(r.answer()).contains("-09-15","-09-17","请确认日期");assertThat(saved.get(0).encryptedFields).isEqualTo(before);
  assertThat(r.actions()).anyMatch(a->a.value().equals("确认日期")).noneMatch(a->a.value().equals("确认提交"));
  assertThat(say("确认日期")).contains("请假原因");say("结束");assertThat(saved.get(0).status).isEqualTo("CANCELLED");
  say("我要休假");say("915-917");assertThat(say("确认日期")).contains("哪种假");assertThat(say("事假")).contains("请假原因");verifyNoInteractions(leaves);
 }
 @Test void ambiguousCompactInputDoesNotMutateDraftAndAcceptsClarification(){
  say("我想请事假");String before=saved.get(0).encryptedFields;
  assertThat(say("111-113")).contains("1月11日","11月1日");assertThat(saved.get(0).encryptedFields).isEqualTo(before);assertThat(saved.get(0).encryptedDateProposal).isNull();
  assertThat(say("11月1日到11月3日")).contains("-11-01","-11-03","请确认日期");say("不办理了");assertThat(saved.get(0).encryptedDateProposal).isNull();verifyNoInteractions(leaves);
 }
 @Test void chatEndpointStopsDraftBeforePolicyOrWorkspaceRouting(){
  var auth=mock(AuthService.class);var gateway=mock(WebChatGatewayService.class);var workspace=mock(AssistantWorkspaceService.class);
  var servlet=mock(jakarta.servlet.http.HttpServletRequest.class);when(auth.requireLifecycleUser(servlet)).thenReturn(employee);
  var controller=new com.hragent.hragentv1.web.WebChatController(auth,gateway,service,workspace);
  say("我想请事假");say("26.9.15-9.17");
  var response=controller.chat(servlet,new com.hragent.hragentv1.dto.WebChatDtos.MessageRequest("不办理了","test"));
  assertThat(response.data().answer()).contains("已取消");assertThat(saved.get(0).status).isEqualTo("CANCELLED");
  verify(gateway).clearPolicyConversation(employee,"test");verify(gateway,never()).policyConversation(any(),anyString(),anyString());verifyNoInteractions(workspace,leaves);
 }
 @Test void naturalStopCommandsCancelWithoutBeingStoredAsDatesOrReasons(){
  for(String command:List.of("结束","不办理了","停止","先不办了","我不想办了","算了，不办了","取消办理")){
   say("我想请事假");say("26.9.15-9.17");var draft=saved.get(saved.size()-1);String before=draft.encryptedFields;
   var r=service.guidedReply(employee,command,"test").orElseThrow();
   assertThat(r.answer()).as(command).contains("已取消").doesNotContain("日期","请确认","第 ");assertThat(r.actions()).isEmpty();
   assertThat(draft.status).isEqualTo("CANCELLED");assertThat(draft.encryptedDateProposal).isNull();assertThat(draft.encryptedFields).isEqualTo(before);
   assertThat(service.reply(employee,"继续办理","test")).isEmpty();
  }
  verifyNoInteractions(leaves);
 }
 @Test void stopWorksAfterUnrecognizedInputAndAtFinalConfirmation(){
  say("我想请事假");say("9/31");assertThat(say("结束")).contains("已取消");
  assertThat(say("不办理了")).contains("没有待填写");
  say("我想请事假");say("2026-09-15");say("2026-09-17");say("个人安排");
  assertThat(say("不办理了")).contains("已取消");say("确认提交");assertThat(saved.get(1).status).isEqualTo("CANCELLED");verifyNoInteractions(leaves);
 }
 @Test void stopIsScopedAndDoesNotWithdrawAlreadySubmittedApplications(){
  say("我想请事假");say("26.9.15-9.17");var draft=saved.get(0);
  assertThat(service.reply(employee,"结束","other").orElseThrow()).contains("没有待填写");
  assertThat(service.reply(user(8L,1L,Role.EMPLOYEE),"结束","test").orElseThrow()).contains("没有待填写");
  assertThat(service.reply(user(1L,2L,Role.EMPLOYEE),"结束","test").orElseThrow()).contains("没有待填写");
  assertThat(draft.status).isEqualTo("DRAFT");assertThat(draft.encryptedDateProposal).isNotNull();
  draft.status="SUBMITTED";assertThat(say("不办理了")).contains("已提交的申请");assertThat(draft.status).isEqualTo("SUBMITTED");verifyNoInteractions(leaves);
 }
 @Test void stopCommandsDoNotMatchOrdinaryDateOrNegationPhrases(){
  for(String text:List.of("结束日期","结束日期9月17日","不取消","不要取消办理","不是不办理了","请问怎么结束办理","请假结束后回来工作"))assertThat(LifecycleService.cancellationRequested(text)).as(text).isFalse();
  for(String text:List.of("结束。","不办理了！","算了，不办了","请取消办理","我先不办了","帮我停止办理"))assertThat(LifecycleService.cancellationRequested(text)).as(text).isTrue();
 }
 @Test void screenshotMixedYearRangeIsConfirmedAndContinuesToReason(){
  say("我想请事假");String original=saved.get(0).encryptedFields;
  var r=service.guidedReply(employee,"26.9.15-9.17","test").orElseThrow();
  assertThat(r.answer()).contains("2026-09-15","2026-09-17","请确认日期").doesNotContain("还没确定");
  assertThat(saved.get(0).encryptedFields).isEqualTo(original);
  assertThat(r.actions()).anyMatch(a->a.value().equals("确认日期")).noneMatch(a->a.value().equals("确认提交"));
  assertThat(say("确认日期")).contains("请假原因").doesNotContain("到哪天结束");
  assertThat(crypto.decrypt(saved.get(0).encryptedFields)).contains("2026-09-15","2026-09-17");verifyNoInteractions(leaves);
 }
 @Test void screenshotShortYearAndSlashDatesContinueTheSameLeaveConversation(){
  say("我想请事假");String original=saved.get(0).encryptedFields;
  assertThat(say("26.9.15")).contains("2026-09-15","请确认日期");
  assertThat(saved.get(0).encryptedFields).isEqualTo(original);
  assertThat(say("确认日期")).contains("到哪天结束");
  assertThat(say("9/17")).contains("2026-09-17","请确认日期");
  assertThat(say("确认日期")).contains("请假原因");
  assertThat(crypto.decrypt(saved.get(0).encryptedFields)).contains("2026-09-15","2026-09-17");verifyNoInteractions(leaves);
 }
 @Test void slashDateCorrectionAndRangeWorkWithoutRestartingDraft(){
  say("我想请事假");assertThat(say("9/31")).contains("这个月没有");
  assertThat(say("9/15")).contains("-09-15","请确认日期");
  var r=service.guidedReply(employee,"9/15-9/17","test").orElseThrow();
  assertThat(r.answer()).contains("-09-15","-09-17","请确认日期");assertThat(r.actions()).anyMatch(a->a.value().equals("确认日期")).noneMatch(a->a.value().equals("确认提交"));
  assertThat(say("对")).contains("请假原因");assertThat(saved).hasSize(1);verifyNoInteractions(leaves);
 }
 @Test void shorthandDateMustBeConfirmedBeforeFillingAndDoesNotSubmit(){
  say("我想请事假");String original=saved.get(0).encryptedFields;
  String year=""+EmployeeReminderService.today().getYear();
  var r=service.guidedReply(employee,"9.15","test").orElseThrow();
  assertThat(r.answer()).contains(year+"-09-15","对吗");assertThat(r.actions()).anyMatch(a->a.value().equals("确认日期")).noneMatch(a->a.value().equals("确认提交"));
  assertThat(saved.get(0).encryptedFields).isEqualTo(original);assertThat(saved.get(0).encryptedDateProposal).doesNotContain("09-15");
  assertThat(say("确认提交")).contains("请确认日期");assertThat(saved.get(0).encryptedFields).isEqualTo(original);
  assertThat(say("是的")).contains("到哪天结束");assertThat(crypto.decrypt(saved.get(0).encryptedFields)).contains(year+"-09-15");verifyNoInteractions(leaves);
 }
 @Test void confirmsBothRangeDatesAndSkipsRedundantEndQuestion(){
  say("我想请事假");say("9.15-9.17");assertThat(say("对")).contains("请假原因").doesNotContain("到哪天结束");
  var values=crypto.decrypt(saved.get(0).encryptedFields);assertThat(values).contains("开始日期","结束日期","-09-15","-09-17");
  say("个人安排");verifyNoInteractions(leaves);
  var view=mock(LeaveDtos.LeaveRequestView.class);when(view.id()).thenReturn(88L);when(leaves.createForAgent(any(),any())).thenReturn(view);say("确认提交");
  verify(leaves).createForAgent(eq(employee),argThat(input->input.startDate().getDayOfMonth()==15&&input.endDate().getDayOfMonth()==17));
 }
 @Test void dateCorrectionsReplaceProposalWithoutStoringRejectedDate(){
  say("我想请病假");say("9.15-9.17");assertThat(say("不是，是9.16-9.18")).contains("-09-16","-09-18");
  say("确认日期");assertThat(crypto.decrypt(saved.get(0).encryptedFields)).contains("-09-16","-09-18").doesNotContain("-09-15");
  say("上一步");assertThat(saved.get(0).encryptedDateProposal).isNull();say("9.19");say("重新输入日期");assertThat(saved.get(0).encryptedDateProposal).isNull();
  assertThat(say("9.31")).contains("这个月没有");verifyNoInteractions(leaves,medical);
 }
 @Test void acceptsRangeInOpeningMessageOrBeforeLeaveType(){
  assertThat(say("我想请事假9.15-9.17")).contains("请确认日期");assertThat(say("确认日期")).contains("请假原因");say("取消办理");
  say("我要休假");say("9.15-9.17");assertThat(say("确认日期")).contains("哪种假");
  assertThat(crypto.decrypt(saved.get(1).encryptedFields)).isEqualTo("{}");
  assertThat(say("事假")).contains("请假原因");assertThat(crypto.decrypt(saved.get(1).encryptedFields)).contains("-09-15","-09-17");
 }
 @Test void pendingDatesSurviveContinuationButRemainScopedAndCancellable(){
  say("我想请事假");say("9.15-9.17");String before=saved.get(0).encryptedFields;
  assertThat(service.reply(employee,"确认日期","other-conversation")).isEmpty();
  assertThat(service.reply(user(8L,1L,Role.EMPLOYEE),"确认日期","test")).isEmpty();
  assertThat(service.reply(user(1L,2L,Role.EMPLOYEE),"确认日期","test")).isEmpty();
  assertThat(say("继续办理")).contains("请确认日期");assertThat(saved.get(0).encryptedFields).isEqualTo(before);
  say("取消办理");assertThat(saved.get(0).encryptedDateProposal).isNull();assertThat(saved.get(0).status).isEqualTo("CANCELLED");verifyNoInteractions(leaves);
 }
 @Test void genericVacationIntentStartsLeaveFormAndPolicyQuestionsDoNotFillFields(){
  for(String q:List.of("我要休假","我想休假","帮我申请休假","想休假"))assertThat(LifecycleService.intent(q)).isEqualTo("LEAVE");
  assertThat(LifecycleService.intent("休假政策有什么规定")).isNull();assertThat(LifecycleService.intent("我不想休假")).isNull();
  assertThat(say("我要休假")).contains("哪种假");String before=saved.get(0).encryptedFields;
  assertThat(service.reply(employee,"年假有多少天","test")).isEmpty();assertThat(saved.get(0).encryptedFields).isEqualTo(before);
 }
 @Test void naturalAnnualLeaveIntentStartsFormWithActualBalance(){
  when(leaves.agentBalances(employee)).thenReturn(List.of(new com.hragent.hragentv1.dto.AgentIntegrationDtos.BalanceLine("ANNUAL","年假",new java.math.BigDecimal("10"),new java.math.BigDecimal("3"),new java.math.BigDecimal("2"),new java.math.BigDecimal("5"))));
  var r=service.guidedReply(employee,"我想休年假","test").orElseThrow();assertThat(r.answer()).contains("5 天","从哪天开始").doesNotContain("累计工龄","条款");assertThat(saved).hasSize(1);verify(leaves,never()).createForAgent(any(),any());
  assertThat(LifecycleService.intent("我想休年休假")).isEqualTo("LEAVE");assertThat(LifecycleService.intent("我不想休年假")).isNull();
 }
 @Test void exhaustedAnnualLeaveDoesNotCreateAnApplication(){
  when(leaves.agentBalances(employee)).thenReturn(List.of(new com.hragent.hragentv1.dto.AgentIntegrationDtos.BalanceLine("ANNUAL","年假",java.math.BigDecimal.TEN,java.math.BigDecimal.TEN,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO)));
  assertThat(say("我想休年假")).contains("0 天","想休息的感受我理解").doesNotContain("法规","必须工作");assertThat(saved).isEmpty();verify(leaves,never()).createForAgent(any(),any());
 }
 @Test void missingAnnualBalanceIsNeverPresentedAsZero(){assertThat(say("我想休年假")).contains("暂时没查到").doesNotContain("用完了","0 天");assertThat(saved).isEmpty();}
 @Test void sickLeaveRequiresScannedMaterialThenExplicitConfirmation(){
  say("我想休病假");say("2026-09-09");say("2026-09-10");var r=service.guidedReply(employee,"身体不适","test").orElseThrow();
  assertThat(r.actions()).anyMatch(a->a.type().equals("medical-upload")).noneMatch(a->a.value().equals("确认提交"));say("确认提交");verify(leaves,never()).createForAgent(any(),any());
  when(medical.upload(eq(employee),any(),any(),any())).thenReturn(new LeaveMedicalService.Scan(77L,LeaveMedicalService.SUMMARY));
  var uploaded=service.medicalUpload(employee,1L,new org.springframework.mock.web.MockMultipartFile("file","record.pdf","application/pdf","fixture".getBytes()));
  assertThat(uploaded.actions()).anyMatch(a->a.value().equals("确认提交"));verify(leaves,never()).createForAgent(any(),any());
  var view=mock(LeaveDtos.LeaveRequestView.class);when(view.id()).thenReturn(9L);when(leaves.createForAgent(any(),any())).thenReturn(view);say("确认提交");
  verify(leaves).createForAgent(eq(employee),argThat(input->Long.valueOf(77L).equals(input.medicalRecordId())));
 }
 @Test void editingSickLeaveDatesInvalidatesPreviousScan(){say("我想休病假");say("2026-09-09");say("2026-09-10");say("身体不适");saved.get(0).medicalRecordId=77L;say("修改开始日期");assertThat(saved.get(0).medicalRecordId).isNull();}
 @Test void careDisclosurePausesDraftWithoutFillingAField(){
  say("我要申请请假");say("事假");say("2026-10-12");say("2026-10-13");
  String original=saved.get(0).encryptedFields;
  assertThat(service.reply(employee,"我现在心情很差","test")).isEmpty();
  assertThat(service.reply(employee,"我想休息","test")).isEmpty();
  assertThat(saved.get(0).encryptedFields).isEqualTo(original);
  assertThat(say("继续办理")).contains("第 4 / 4 步");verifyNoInteractions(leaves);
 }
 @Test void stageChoicesNeverBecomeFormData(){
  var catalog=service.guidedReply(employee,"有哪些全周期服务","test").orElseThrow();
  assertThat(catalog.actions()).anyMatch(a->a.value().equals("在职"));
  var active=service.guidedReply(employee,"在职","test").orElseThrow();
  assertThat(active.answer()).contains("你这次想先办","确认提交");assertThat(active.actions()).anyMatch(a->a.value().equals("我要申请请假"));assertThat(saved).isEmpty();
  say("我要申请请假");var original=saved.get(0).encryptedFields;say("在职");assertThat(saved.get(0).encryptedFields).isEqualTo(original);
  assertThat(say("继续办理")).contains("第 1 / 4 步");
 }
 @Test void stepsSupportChoicesCorrectionAndFinalConfirmation(){
  var first=service.guidedReply(employee,"请假","test").orElseThrow();assertThat(first.actions()).anyMatch(a->a.value().equals("事假"));
  say("事假");say("2026-10-12");assertThat(say("上一步")).contains("第 2 / 4 步");say("2026-10-13");say("2026-10-14");
  var ready=service.guidedReply(employee,"个人安排","test").orElseThrow();assertThat(ready.actions()).anyMatch(a->a.value().equals("确认提交"));verifyNoInteractions(leaves);
  assertThat(say("修改开始日期")).contains("第 2 / 4 步");assertThat(crypto.decrypt(saved.get(0).encryptedFields)).doesNotContain("2026-10-14","个人安排");
  assertThat(service.guidedReply(employee,"继续办理","test").orElseThrow().actions()).noneMatch(a->a.value().equals("确认提交"));
 }
 @Test void incompleteConfirmationAndQuestionDoNotSubmit(){
  say("我要开在职证明");assertThat(say("确认提交")).contains("第 1 / 5 步");
  assertThat(service.reply(employee,"休假有哪些规定","test")).isEmpty();assertThat(crypto.decrypt(saved.get(0).encryptedFields)).isEqualTo("{}");verifyNoInteractions(certificates);
 }
 @Test void formerStageDoesNotOfferActiveEmployeeServices(){
  employee.setEmployeeStatus(EmployeeStatus.LEFT);var reply=service.guidedReply(employee,"在职","test").orElseThrow();assertThat(reply.answer()).contains("离职后");assertThat(reply.actions()).noneMatch(a->a.value().equals("我要申请请假")||a.type().equals("workbench"));
 }
 @Test void stageMenusCoverFullLifecycleWithoutCreatingRequests(){
  for(String stage:List.of("入职","在职","离职","离职后")){var reply=service.guidedReply(employee,stage,"test").orElseThrow();assertThat(reply.actions()).isNotEmpty();assertThat(reply.answer()).doesNotContain("暂时还答不上来");}assertThat(saved).isEmpty();
 }
 void certificate(){say("补发离职证明");say("补发");say("新单位入职");say("中文，无指定抬头");}
 @Test void explicitConfirmationIdempotencyAndApprovalStateMachine(){
  certificate();assertThat(saved.get(0).status).isEqualTo("DRAFT");assertThat(saved.get(0).encryptedFields).doesNotContain("新单位");
  assertThat(say("确认提交")).contains("HRSSC");say("确认提交");assertThat(saved).hasSize(1);
  assertThatThrownBy(()->service.review(hr,1L,"COMPLETED","交付")).isInstanceOf(AppException.class);
  service.review(hr,1L,"NEEDS_INFO","补充抬头");
  assertThat(say("继续办理")).contains("补充抬头");say("示例单位");say("确认提交");
  service.review(hr,1L,"APPROVED","已核对");
  assertThatThrownBy(()->service.review(hr,1L,"COMPLETED","完成")).hasMessageContaining("上传");
  byte[] pdf="%PDF-1.4\nverified test fixture".getBytes();service.resultFile(hr,1L,"proof.pdf",pdf);service.review(hr,1L,"COMPLETED","文件已核对");
  assertThat(service.download(employee,1L)).isEqualTo(pdf);
 }
 @Test void leaveUsesExistingEngineOnlyAfterConfirmation(){
  var view=mock(LeaveDtos.LeaveRequestView.class);when(view.id()).thenReturn(88L);when(leaves.createForAgent(eq(employee),any())).thenReturn(view);
  say("我要申请请假");say("事假");say("2026-10-12");say("2026-10-13");say("个人安排");
  verifyNoInteractions(leaves);say("确认提交");say("确认提交");verify(leaves,times(1)).createForAgent(eq(employee),any());assertThat(saved.get(0).leaveRequestId).isEqualTo(88L);
  assertThatThrownBy(()->service.review(hr,1L,"APPROVED","通过")).hasMessageContaining("现有");
 }
 @Test void ownerTenantAndRoleIsolation(){certificate();say("确认提交");
  assertThatThrownBy(()->service.download(user(3L,1L,Role.EMPLOYEE),1L)).hasMessageContaining("本人");
  assertThatThrownBy(()->service.review(user(9L,2L,Role.HR),1L,"APPROVED","通过")).hasMessageContaining("不存在");
  assertThatThrownBy(()->service.review(employee,1L,"APPROVED","通过")).hasMessageContaining("仅在职");
 }
 @Test void conversationsAndFormerEmployeeScope(){say("我要办理人事变更");assertThat(service.reply(employee,"确认提交","another")).isEmpty();assertThat(saved.get(0).status).isEqualTo("DRAFT");say("取消办理");employee.setEmployeeStatus(EmployeeStatus.LEFT);
  assertThat(say("我要申请请假")).contains("离职员工");assertThat(say("补发离职证明")).contains("开始填写");
 }
 @Test void allIntentsAndPayrollStayPersonal(){
  Map<String,String> intents=Map.of("我要办理入职材料核验","ONBOARDING","我要咨询合同签署","CONTRACT","我要申请请假","LEAVE","我要办理人事变更","CHANGE","我要咨询社保公积金","SOCIAL","我要申请离职","EXIT","我要核对离职结算","SETTLEMENT","补发离职证明","EXIT_CERT","我要调取档案","ARCHIVE","我要申请劳动关系证明","RELATION_CERT");
  intents.forEach((text,kind)->assertThat(LifecycleService.intent(text)).isEqualTo(kind));
  assertThat(LifecycleService.intent("请在我申请请假前，帮我列出需要确认的信息和材料。")).isNull();
  assertThat(LifecycleService.intent("申请年假有什么规定")).isNull();
  assertThat(service.payroll(employee,"2026-08 工资条")).contains("还没有查到");verify(pay).findByTenantIdAndEmployeeIdOrderByPayMonthDesc(1L,1L);
 }
 @Test void payrollMonthSelectionAndMaterialAccess(){
  var slip=new EmployeePayslip();slip.tenantId=1L;slip.employeeId=1L;slip.payMonth="2026-08";slip.encryptedDetails=crypto.encrypt("单元测试工资明细");
  when(pay.findByTenantIdAndEmployeeIdOrderByPayMonthDesc(1L,1L)).thenReturn(List.of(slip));
  assertThat(service.payroll(employee,"2026年8月工资条")).contains("单元测试工资明细");
  assertThat(service.payroll(employee,"2026-07工资条")).doesNotContain("单元测试工资明细");
  certificate();byte[] pdf="%PDF-1.4\nUnit test materials".getBytes();service.materials(employee,1L,pdf);
  assertThat(saved.get(0).encryptedMaterials).doesNotContain("Unit test");assertThatThrownBy(()->service.materialsDownload(hr,1L,true)).hasMessageContaining("尚未确认");say("确认提交");assertThat(service.materialsDownload(hr,1L,true)).isEqualTo(pdf);
  assertThatThrownBy(()->service.materialsDownload(user(3L,1L,Role.EMPLOYEE),1L,false)).hasMessageContaining("本人");
 }
 @Test void employmentCertificatesReuseOriginalRequestAndReview(){
  var created=mock(com.hragent.hragentv1.dto.EmploymentCertificateDtos.RequestView.class);when(created.id()).thenReturn(91L);when(certificates.create(eq(employee),any())).thenReturn(created);
  assertThat(say("买房需要在职证明")).contains("开始填写");say("购房贷款");say("中文");say("不需要");say("无");say("公司模板");verifyNoInteractions(certificates);
  say("确认提交");say("确认提交");
  verify(certificates,times(1)).create(eq(employee),argThat(r->r.certificateType()==EmploymentCertificateType.STANDARD&&r.language()==CertificateLanguage.CHINESE&&r.purpose().equals("购房贷款")&&!r.includeSalary()));
  assertThatThrownBy(()->service.review(hr,1L,"APPROVED","通过")).hasMessageContaining("原有证明管理");
  var original=new EmploymentCertificateRequest();original.setTenantId(1L);original.setEmployeeId(1L);original.setStatus(CertificateRequestStatus.REJECTED);original.setHrOpinion("请核实用途");when(certificateRequests.findById(91L)).thenReturn(Optional.of(original));
  assertThat(say("查看服务单 1")).contains("已驳回","请核实用途");
 }
 @Test void personalCertificateRequiresUploadAndFieldConfirmation() throws Exception {
  var templates=mock(EmploymentCertificateTemplateService.class);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"certificateTemplates",templates);
  var preview=new com.hragent.hragentv1.dto.EmploymentCertificateTemplateDtos.TemplatePreview("个人模板.docx",100,true,true,true,List.of("{{legalName}}","{{接收单位}}"),List.of("{{接收单位}}"),List.of());
  when(templates.preview(eq(employee),any())).thenReturn(preview);
  var template=new EmploymentCertificateTemplate();template.setId(55L);template.setSourceFileName("个人模板.docx");
  when(templates.uploadProposal(eq(employee),any(),anyString(),any(),any(),any())).thenReturn(template);
  when(templates.download(employee,55L)).thenReturn(new EmploymentCertificateTemplateService.TemplateDownload("个人模板.docx","application/docx",new byte[]{1}));
  var result=mock(com.hragent.hragentv1.dto.EmploymentCertificateDtos.RequestView.class);when(result.id()).thenReturn(91L);when(certificates.create(eq(employee),any())).thenReturn(result);
  say("我要开在职证明");say("资格审核");say("中文");say("不需要");
  assertThat(say("无")).contains("是否需要使用你自己提供的模板");
  var choice=service.guidedReply(employee,"自行上传","test").orElseThrow();assertThat(choice.actions()).anyMatch(a->a.type().equals("certificate-template"));
  say("确认提交");verifyNoInteractions(certificates);
  var file=new org.springframework.mock.web.MockMultipartFile("file","个人模板.docx","application/docx",new byte[]{1});
  assertThat(service.certificateAttachment(employee,"test",file).orElseThrow().answer()).contains("模板已读取","接收单位");
  assertThatThrownBy(()->say("确认提交")).hasMessageContaining("接收单位");verifyNoInteractions(certificates);
  assertThatThrownBy(()->service.confirmCertificateTemplate(employee,1L,Map.of())).hasMessageContaining("接收单位");
  service.confirmCertificateTemplate(employee,1L,Map.of("接收单位","资格审核中心"));
  assertThat(saved.getFirst().encryptedCertificateValues).doesNotContain("资格审核中心");
  say("确认提交");say("确认提交");verify(certificates,times(1)).create(eq(employee),argThat(r->r.requestedTemplateId().equals(55L)&&r.templateValues().get("接收单位").equals("资格审核中心")));
 }
 @Test void visaAndIncomeFieldsUseExistingTypes(){
  var created=mock(com.hragent.hragentv1.dto.EmploymentCertificateDtos.RequestView.class);when(created.id()).thenReturn(92L);when(certificates.create(eq(employee),any())).thenReturn(created);
  say("我要开出国签证在职证明");say("日本");say("日本驻上海总领事馆");say("旅游签证");say("中英双语");say("需要");say("无");say("公司模板");say("确认提交");
  verify(certificates).create(eq(employee),argThat(r->r.certificateType()==EmploymentCertificateType.VISA&&r.language()==CertificateLanguage.BILINGUAL&&r.destinationCountry().equals("日本")&&r.consulateName().equals("日本驻上海总领事馆")&&r.includeSalary()));
  say("我要开收入证明");say("购房贷款");say("银行抬头待核实");say("公司模板");say("确认提交");
  verify(certificates).create(eq(employee),argThat(r->r.certificateType()==EmploymentCertificateType.INCOME&&r.language()==CertificateLanguage.CHINESE&&r.includeSalary()));
 }
}
