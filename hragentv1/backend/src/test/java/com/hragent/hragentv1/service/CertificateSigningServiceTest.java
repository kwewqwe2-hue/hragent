package com.hragent.hragentv1.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CertificateSigningServiceTest {
 final CertificateSignConfigRepository configs=mock(CertificateSignConfigRepository.class);
 final CertificateSignJobRepository jobs=mock(CertificateSignJobRepository.class);
 final EmploymentCertificateRequestRepository requests=mock(EmploymentCertificateRequestRepository.class);
 final EmploymentCertificateDocumentService docs=mock(EmploymentCertificateDocumentService.class);
 final SecretCryptoService crypto=new SecretCryptoService("certificate-signing-test-key-12345");
 final TencentEssClient client=mock(TencentEssClient.class);
 final PlatformTransactionManager tm=mock(PlatformTransactionManager.class);
 final CertificateSigningService service=new CertificateSigningService(configs,jobs,requests,docs,crypto,client,mock(AuditService.class),tm);
 final ObjectMapper json=new ObjectMapper();
 final CertificateSignJob job=new CertificateSignJob();
 final EmploymentCertificateRequest request=new EmploymentCertificateRequest();
 final UserAccount employee=LifecycleServiceTest.user(1L,7L,Role.EMPLOYEE),hr=LifecycleServiceTest.user(2L,7L,Role.HR);
 @BeforeEach void setup()throws Exception{
  when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
  job.certificateId=9L;job.tenantId=7L;job.version=0L;job.flowId="flow-9";job.resourceId="pdf-9";
  job.encryptedConfig=crypto.encrypt(json.writeValueAsString(new TencentEssClient.Settings("secret-id","secret-key","operator","企业","seal-1","公司盖章处")));
  request.setId(9L);request.setTenantId(7L);request.setEmployeeId(1L);request.setStatus(CertificateRequestStatus.GENERATED);request.setGeneratedFileName("证明.docx");request.setGeneratedFileStorageKey("file.docx");
  when(requests.findByIdAndTenantId(9L,7L)).thenReturn(Optional.of(request));
  when(jobs.findById(9L)).thenReturn(Optional.of(job));when(jobs.claim(anyLong(),anyString(),anyString(),anyLong())).thenReturn(1);
 }
 @Test void partialSignatureIsNotCompletion()throws Exception{
  job.status="SIGNING";when(client.call(any(),eq("DescribeFlowBriefs"),any())).thenReturn(json.readTree("{\"FlowBriefs\":[{\"FlowId\":\"flow-9\",\"FlowStatus\":2}]}"));
  service.process(job);assertThat(job.status).isEqualTo("SIGNING");assertThatThrownBy(()->service.download(employee,9L)).hasMessageContaining("尚未完成");
 }
 @Test void onlyMatchingCompletedFlowUnlocksPdf()throws Exception{
  job.status="SIGNING";when(client.call(any(),eq("DescribeFlowBriefs"),any())).thenReturn(json.readTree("{\"FlowBriefs\":[{\"FlowId\":\"other\",\"FlowStatus\":4},{\"FlowId\":\"flow-9\",\"FlowStatus\":4}]}"));
  service.process(job);assertThat(job.status).isEqualTo("SIGNED");
  when(client.call(any(),eq("DescribeFileUrls"),any())).thenReturn(json.readTree("{\"FileUrls\":[{\"Url\":\"https://file.ess.tencent.cn/signed.pdf\"}]}"));when(client.download(anyString())).thenReturn("%PDF-fixture".getBytes());
  assertThat(service.download(employee,9L)).startsWith("%PDF".getBytes());
 }
 @Test void creationTimeoutNeverAutomaticallyCreatesAnotherFlow(){
  job.status="CONVERT_READY";job.flowId=null;when(client.call(any(),eq("CreateFlowByFiles"),any())).thenThrow(new IllegalStateException("timeout"));
  service.process(job);assertThat(job.status).isEqualTo("UNCERTAIN");service.start(hr,9L);verify(client,times(1)).call(any(),eq("CreateFlowByFiles"),any());
 }
 @Test void createsEnterpriseSealWithKeywordAndNoOutboundNotifications()throws Exception{
  job.status="CONVERT_READY";job.flowId=null;when(client.call(any(),eq("CreateFlowByFiles"),any())).thenReturn(json.readTree("{\"FlowId\":\"flow-created\"}"));
  service.process(job);assertThat(job.status).isEqualTo("SIGNING");assertThat(job.flowId).isEqualTo("flow-created");
  verify(client).call(any(),eq("CreateFlowByFiles"),argThat(body->body.toString().contains("GenerateMode=KEYWORD")&&body.toString().contains("NotifyType=NONE")&&body.toString().contains("ApproverType=3")&&body.toString().contains("ComponentValue=seal-1")));
 }
 @Test void failedClaimMakesNoProviderCall(){job.status="CONVERT_READY";when(jobs.claim(anyLong(),anyString(),anyString(),anyLong())).thenReturn(0);service.process(job);verifyNoInteractions(client);}
 @Test void deniesOtherEmployeeAndTenantAndConfigChanges(){
  assertThatThrownBy(()->service.status(LifecycleServiceTest.user(3L,7L,Role.EMPLOYEE),9L)).hasMessageContaining("自己的");
  assertThatThrownBy(()->service.status(LifecycleServiceTest.user(1L,8L,Role.HR),9L)).hasMessageContaining("不存在");
  assertThatThrownBy(()->service.config(employee)).hasMessageContaining("只有 HR");verifyNoInteractions(client);
 }
 @Test void rejectedFlowDoesNotUnlockDownload()throws Exception{job.status="SIGNING";when(client.call(any(),eq("DescribeFlowBriefs"),any())).thenReturn(json.readTree("{\"FlowBriefs\":[{\"FlowId\":\"flow-9\",\"FlowStatus\":3}]}"));service.process(job);assertThat(job.status).isEqualTo("FAILED");assertThatThrownBy(()->service.download(employee,9L)).hasMessageContaining("尚未完成");}
 @Test void duplicateOrMissingSealAnchorStopsBeforeCloudUpload()throws Exception{
  job.status="READY";try(var doc=new org.apache.poi.xwpf.usermodel.XWPFDocument();var out=new java.io.ByteArrayOutputStream()){doc.createParagraph().createRun().setText("公司盖章处 公司盖章处");doc.write(out);when(docs.read("file.docx")).thenReturn(out.toByteArray());}
  service.process(job);assertThat(job.status).isEqualTo("FAILED");verifyNoInteractions(client);
 }
 @Test void configurationResponseNeverExposesCredentials(){var c=new CertificateSignConfig();c.tenantId=7L;c.enabled=true;c.encryptedConfig=job.encryptedConfig;when(configs.findById(7L)).thenReturn(Optional.of(c));assertThat(service.config(hr).toString()).contains("hasCredentials=true").doesNotContain("secret-id","secret-key");}
 @Test void conversionCompletesOnlyAtTaskStatusEight()throws Exception{job.status="CONVERTING";job.taskId="convert-1";when(client.call(any(),eq("DescribeFileConvertTask"),any())).thenReturn(json.readTree("{\"TaskStatus\":4}"),json.readTree("{\"TaskStatus\":8,\"ResourceId\":\"converted-pdf\"}"));service.process(job);assertThat(job.status).isEqualTo("CONVERTING");service.process(job);assertThat(job.status).isEqualTo("CONVERT_READY");assertThat(job.resourceId).isEqualTo("converted-pdf");}
}
