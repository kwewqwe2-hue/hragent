package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmploymentCertificateDtos;
import com.hragent.hragentv1.repo.*;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CertificateGenerationIntegrationTest {
 @TempDir Path dir;
 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true}) void customChineseTemplateGeneratesVerifiedEmployeeAndConfirmedFieldsAndStaysPrivate(boolean missingTitle)throws Exception{
  var requestRepo=mock(EmploymentCertificateRequestRepository.class);var templateRepo=mock(EmploymentCertificateTemplateRepository.class);
  var profiles=mock(EmployeePersonalProfileRepository.class);var users=mock(UserAccountRepository.class);var tenants=mock(TenantRepository.class);var audit=mock(AuditService.class);
  var crypto=new SecretCryptoService("certificate-generation-real-service-test");
  var templates=new EmploymentCertificateTemplateService(templateRepo,audit,dir.toString());
  var documents=new EmploymentCertificateDocumentService(tenants,dir.toString(),"测试公司地址","010-00000000");
  var renderer=new VisaCertificateDocumentService(tenants,crypto,dir.toString());
  var service=new EmploymentCertificateService(requestRepo,profiles,users,audit,documents,templateRepo,templates,renderer);ReflectionTestUtils.setField(service,"templateCrypto",crypto);
  var employee=LifecycleServiceTest.user(3L,1L,Role.EMPLOYEE);employee.setName("测试员工");employee.setDepartment("研发部");employee.setTitle("工程师");employee.setEntryDate(LocalDate.of(2024,3,1));
  var hr=LifecycleServiceTest.user(2L,1L,Role.HR);var profile=new EmployeePersonalProfile();profile.setLegalName("测试员工");
  var tenant=new Tenant();tenant.setId(1L);tenant.setName("测试科技有限公司");tenant.setCode("TEST");
  when(tenants.findById(1L)).thenReturn(Optional.of(tenant));when(users.findById(3L)).thenReturn(Optional.of(employee));when(profiles.findByTenantIdAndEmployeeId(1L,3L)).thenReturn(Optional.of(profile));
  Map<Long,EmploymentCertificateRequest> requestStore=new HashMap<>();Map<Long,EmploymentCertificateTemplate> templateStore=new HashMap<>();
  when(requestRepo.save(any())).thenAnswer(a->{EmploymentCertificateRequest r=a.getArgument(0);r.setId(101L);requestStore.put(101L,r);return r;});
  when(requestRepo.lockForUpdate(101L,1L)).thenAnswer(a->Optional.ofNullable(requestStore.get(101L)));
  when(templateRepo.save(any())).thenAnswer(a->{EmploymentCertificateTemplate t=a.getArgument(0);t.setId(51L);templateStore.put(51L,t);return t;});
  when(templateRepo.findByIdAndTenantId(51L,1L)).thenAnswer(a->Optional.ofNullable(templateStore.get(51L)));
  when(templateRepo.findByTenantIdOrderByUpdatedAtDesc(1L)).thenAnswer(a->List.copyOf(templateStore.values()));
  byte[] source;try(var doc=new XWPFDocument();var out=new java.io.ByteArrayOutputStream()){
   var title=doc.createParagraph();title.setAlignment(ParagraphAlignment.CENTER);var run=title.createRun();run.setText("在职证明");run.setBold(true);run.setFontSize(20);run.setFontFamily("宋体");title.setSpacingAfter(500);
   var body=doc.createParagraph();body.setSpacingAfter(240);body.createRun().setText("兹证明【姓名】在【公司名称】任职，现任【部门】【职位】。本证明用于【用途】。");
   var table=doc.createTable(2,2);table.getRow(0).getCell(0).setText("入职日期");table.getRow(1).getCell(0).setText("接收单位：");
   doc.createParagraph().createRun().setText("开具日期：【开具日期】");
   doc.write(out);source=out.toByteArray();
  }
  if(missingTitle)employee.setTitle(null);
  var file=new MockMultipartFile("file","自备模板.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",source);
  var preview=templates.preview(employee,file);assertThat(preview.canUpload()).isTrue();assertThat(preview.unsupportedPlaceholders()).containsExactly("{{接收单位}}");
  var preparation=new CertificateTemplatePreparationService(profiles,tenants,crypto);ReflectionTestUtils.setField(service,"templatePreparation",preparation);
  var cases=mock(LifecycleRequestRepository.class);var drafts=new ArrayList<LifecycleRequest>();
  when(cases.save(any())).thenAnswer(a->{LifecycleRequest c=a.getArgument(0);if(c.id==null){c.id=1L;drafts.add(c);}return c;});
  when(cases.findFirstByTenantIdAndEmployeeIdAndConversationIdOrderByIdDesc(1L,3L,"template-test")).thenAnswer(a->drafts.stream().findFirst());
  when(cases.findByIdAndTenantId(1L,1L)).thenAnswer(a->drafts.stream().findFirst());
  var lifecycle=new LifecycleService(cases,mock(EmployeePayslipRepository.class),users,mock(LeaveRequestRepository.class),mock(LeaveService.class),mock(EmployeeRelationsService.class),crypto,new com.fasterxml.jackson.databind.ObjectMapper(),audit,service,requestRepo,mock(LeaveMedicalService.class));
  ReflectionTestUtils.setField(lifecycle,"certificateTemplates",templates);ReflectionTestUtils.setField(lifecycle,"certificatePreparation",preparation);
  var opening=lifecycle.certificateAttachment(employee,"template-test",file,"帮我办一下在职证明").orElseThrow();assertThat(opening.answer()).contains("模板已读取").doesNotContain("第 1 /");assertThat(requestStore).isEmpty();
  var state=(Map<?,?>)lifecycle.certificateTemplateState(employee,1L);assertThat(((Map<?,?>)state.get("automatic")).get("legalName")).isEqualTo("测试员工");
  var input=new LinkedHashMap<String,String>();input.put("purpose","资格审核");input.put("接收单位","资格审核中心");if(missingTitle)input.put("title","工程师");
  assertThatThrownBy(()->lifecycle.confirmCertificateTemplate(employee,1L,Map.of("legalName","不允许覆盖"))).hasMessageContaining("请勿修改");assertThat(requestStore).isEmpty();
  var receipt=lifecycle.confirmCertificateTemplate(employee,1L,input);assertThat(receipt.answer()).contains("已提交");assertThat(requestStore.get(101L).getStatus()).isEqualTo(CertificateRequestStatus.PENDING_HR);assertThat(requestStore.get(101L).getGeneratedFileName()).isNull();
  lifecycle.confirmCertificateTemplate(employee,1L,input);verify(requestRepo,times(1)).save(any());assertThat(employee.getTitle()).isEqualTo(missingTitle?null:"工程师");verify(profiles,never()).save(any());

  assertThat(requestStore.get(101L).getEncryptedTemplateValues()).doesNotContain("资格审核中心");
  var reviewed=service.review(hr,101L,new EmploymentCertificateDtos.ReviewRequest(true,"档案核验通过"));assertThat(reviewed.documentReady()).isTrue();assertThat(reviewed.templateValues()).containsEntry("接收单位","资格审核中心");
  byte[] generated=service.download(employee,101L).content();
  try(var doc=new XWPFDocument(new java.io.ByteArrayInputStream(generated))){var text=new StringBuilder();CertificateTemplateFields.paragraphs(doc,p->text.append(p.getText()));assertThat(text.toString()).contains("测试员工","测试科技有限公司","研发部工程师","资格审核中心","2024-03-01","公司盖章处").doesNotContain("{{","【姓名】");assertThat(doc.getParagraphs().getFirst().getRuns().getFirst().isBold()).isTrue();}
  assertThat(templates.download(employee,51L).content()).isEqualTo(source);
  var other=LifecycleServiceTest.user(4L,1L,Role.EMPLOYEE);assertThat(templates.list(other)).isEmpty();assertThatThrownBy(()->templates.download(other,51L)).hasMessageContaining("自己的模板");
  String fixtureDir=System.getProperty("certificate.fixture.dir");if(fixtureDir!=null){Path destination=Path.of(fixtureDir);Files.createDirectories(destination);Files.write(destination.resolve("personal-template.docx"),source);Files.write(destination.resolve("generated-certificate.docx"),generated);}
 }
}
