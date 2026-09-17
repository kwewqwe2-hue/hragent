package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CertificateTemplatePreparationServiceTest {
 @Test void onlyAbsentFieldsNeedInputAndExistingProfileCannotBeOverridden(){
  var profiles=mock(EmployeePersonalProfileRepository.class);var tenants=mock(TenantRepository.class);var crypto=new SecretCryptoService("template-preparation-test-key-32-characters");
  var employee=LifecycleServiceTest.user(8L,4L,Role.EMPLOYEE);employee.setDepartment("产品部");employee.setEntryDate(LocalDate.of(2024,1,1));var profile=new EmployeePersonalProfile();profile.setLegalName("测试姓名");
  var tenant=new Tenant();tenant.setName("测试企业");when(tenants.findById(4L)).thenReturn(Optional.of(tenant));when(profiles.findByTenantIdAndEmployeeId(4L,8L)).thenReturn(Optional.of(profile));
  var service=new CertificateTemplatePreparationService(profiles,tenants,crypto);
  var ready=service.prepare(employee,"EMPLOYMENT_CERT",List.of("{{legalName}}","{{title}}","{{purpose}}","{{接收单位}}"),Map.of("证明用途","资格审核"),Map.of("接收单位","业务中心"));
  assertThat(ready.automatic()).containsEntry("legalName","测试姓名").containsEntry("purpose","资格审核");assertThat(ready.missingFields()).extracting(CertificateTemplatePreparationService.MissingField::key).containsExactly("title","接收单位");assertThat(ready.values()).containsEntry("接收单位","业务中心");
  assertThatThrownBy(()->service.validateSupplement(employee,"legalName","其他姓名")).hasMessageContaining("请勿覆盖");assertThatThrownBy(()->service.validateSupplement(employee,"companyName","其他企业")).hasMessageContaining("请勿覆盖");
  service.validateSupplement(employee,"title","产品经理");assertThat(employee.getTitle()).isNull();verify(profiles,never()).save(any());
  assertThatThrownBy(()->CertificateTemplatePreparationService.validateValue("entryDate","2026-02-30")).hasMessageContaining("日期");assertThatThrownBy(()->CertificateTemplatePreparationService.validateValue("monthlySalary","abc")).hasMessageContaining("金额");
 }
}
