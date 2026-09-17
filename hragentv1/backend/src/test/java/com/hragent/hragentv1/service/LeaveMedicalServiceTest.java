package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.dto.LeaveDtos;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveMedicalServiceTest {
 final LeaveMedicalRecordRepository records=mock(LeaveMedicalRecordRepository.class);
 final LeaveRequestRepository leaves=mock(LeaveRequestRepository.class);
 final EmployeePersonalProfileRepository profiles=mock(EmployeePersonalProfileRepository.class);
 final MedicalTextParser parser=mock(MedicalTextParser.class);
 final SecretCryptoService crypto=new SecretCryptoService("medical-test-key-32-characters-long");
 final LeaveMedicalService service=new LeaveMedicalService(records,leaves,profiles,parser,crypto,mock(AuditService.class));
 final UserAccount u=LifecycleServiceTest.user(1L,1L,Role.EMPLOYEE);
 final LocalDate start=LocalDate.of(2026,9,9),end=start.plusDays(1);
 final String text="某某医院 门诊病历\n姓名：测试员工\n性别：男\n就诊日期：2026-09-08\n诊断：测试材料\n医嘱：建议休息";
 final MockMultipartFile file=new MockMultipartFile("file","record.pdf","application/pdf","%PDF-1.4 synthetic fixture".getBytes());
 @Test void onlyCompleteMatchingMedicalTextPasses(){
  assertThatCode(()->LeaveMedicalService.check(text,u.getName(),start,end)).doesNotThrowAnyException();
  assertThatThrownBy(()->LeaveMedicalService.check("公司年假通知",u.getName(),start,end)).hasMessageContaining("完整病历");
  assertThatThrownBy(()->LeaveMedicalService.check(text.replace("测试员工","其他员工"),u.getName(),start,end)).hasMessageContaining("姓名");
  assertThatThrownBy(()->LeaveMedicalService.check(text.replace("2026-09-08","2099-09-08"),u.getName(),start,end)).hasMessageContaining("日期");
  assertThatThrownBy(()->LeaveMedicalService.check(text.replace("2026-09-08","日期不清晰"),u.getName(),start,end)).hasMessageContaining("日期");
 }
 @Test void forgedFileExtensionIsNotTrusted(){assertThatThrownBy(()->LeaveMedicalService.extension("not actually a PDF document".getBytes())).hasMessageContaining("文件内容");}
 @Test void validUploadEncryptsMaterialAndDoesNotSubmit(){
  when(parser.extract(any(),eq("pdf"))).thenReturn(text);when(records.save(any())).thenAnswer(a->{LeaveMedicalRecord r=a.getArgument(0);r.id=22L;return r;});
  var result=service.upload(u,file,start,end);assertThat(result.id()).isEqualTo(22L);assertThat(result.summary()).contains("仍需");
  var capture=org.mockito.ArgumentCaptor.forClass(LeaveMedicalRecord.class);verify(records).save(capture.capture());
  assertThat(capture.getValue().encryptedText).doesNotContain("测试员工");assertThat(crypto.decrypt(capture.getValue().encryptedText)).isEqualTo(text);assertThat(capture.getValue().leaveRequestId).isNull();verifyNoInteractions(leaves);
 }
 @Test void scannerFailureCannotCreateSuccessfulRecord(){when(parser.extract(any(),any())).thenThrow(new RuntimeException("unavailable"));assertThatThrownBy(()->service.upload(u,file,start,end)).isInstanceOf(RuntimeException.class);verifyNoInteractions(records,leaves);}
 LeaveMedicalRecord record(){var r=new LeaveMedicalRecord();r.id=22L;r.tenantId=1L;r.employeeId=1L;r.startDate=start;r.endDate=end;when(records.findById(22L)).thenReturn(Optional.of(r));when(records.lockById(22L)).thenReturn(Optional.of(r));return r;}
 @Test void materialsAreBoundToOwnerTenantDatesAndSingleSubmission(){
  var r=record();assertThatThrownBy(()->service.validate(u,null,start,end)).hasMessageContaining("上传");
  assertThatThrownBy(()->service.validate(LifecycleServiceTest.user(3L,1L,Role.EMPLOYEE),22L,start,end)).hasMessageContaining("本人");
  assertThatThrownBy(()->service.validate(LifecycleServiceTest.user(1L,2L,Role.EMPLOYEE),22L,start,end)).hasMessageContaining("本人");
  assertThatThrownBy(()->service.validate(u,22L,start.plusDays(1),end)).hasMessageContaining("日期已变化");
  service.bind(u,22L,start,end,9L);assertThat(r.leaveRequestId).isEqualTo(9L);
  assertThatThrownBy(()->service.bind(u,22L,start,end,10L)).hasMessageContaining("另一份申请");
 }
 @Test void unrelatedEmployeeCannotDownloadMedicalRecord(){var leave=new LeaveRequest();leave.setId(9L);leave.setTenantId(1L);leave.setEmployeeId(2L);leave.setManagerId(3L);when(leaves.findById(9L)).thenReturn(Optional.of(leave));assertThatThrownBy(()->service.download(u,9L)).hasMessageContaining("仅本人");verifyNoInteractions(records);}
 @Test void directLeaveApiCannotSkipMedicalUpload(){
  var repo=mock(LeaveRequestRepository.class);var leaveService=new LeaveService(mock(LeaveBalanceRepository.class),repo,mock(UserAccountRepository.class),mock(AssistantService.class),mock(AuditService.class),mock(WorkdayService.class),mock(AgentNotificationService.class),service);
  assertThatThrownBy(()->leaveService.create(u,new LeaveDtos.CreateLeaveRequest(LeaveType.SICK,start,end,BigDecimal.ONE,"身体不适"))).hasMessageContaining("上传病历");verifyNoInteractions(repo);
 }
 @Test void sickLeaveCannotAutoSkipHrReview(){
  var repo=mock(LeaveRequestRepository.class);var leave=new LeaveRequest();leave.setId(9L);leave.setTenantId(1L);leave.setEmployeeId(1L);leave.setManagerId(2L);leave.setLeaveType(LeaveType.SICK);leave.setStatus(RequestStatus.PENDING_MANAGER);when(repo.lockForReview(9L,1L)).thenReturn(Optional.of(leave));
  var manager=LifecycleServiceTest.user(2L,1L,Role.MANAGER);var leaveService=spy(new LeaveService(mock(LeaveBalanceRepository.class),repo,mock(UserAccountRepository.class),mock(AssistantService.class),mock(AuditService.class),mock(WorkdayService.class),mock(AgentNotificationService.class),service));
  var review=new LeaveDtos.ReviewRequest(true,"已核实材料");doReturn(null).when(leaveService).managerReview(manager,9L,review);leaveService.managerReviewAndAutoRecord(manager,9L,review);verify(leaveService).managerReview(manager,9L,review);
 }
}
