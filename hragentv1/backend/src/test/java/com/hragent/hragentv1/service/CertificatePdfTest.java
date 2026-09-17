package com.hragent.hragentv1.service;
import com.hragent.hragentv1.web.*;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class CertificatePdfTest {
 final byte[] unsigned="%PDF-1.7\nunit-test-unsigned-content\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
 final byte[] signed="%PDF-1.7\nunit-test-provider-signed-content\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
 @Test void rejectsJsonAndTruncatedFiles(){assertThatThrownBy(()->CertificatePdfService.validate("{\"success\":false}".getBytes())).hasMessageContaining("不完整");assertThatThrownBy(()->CertificatePdfService.validate("%PDF-1.7 unfinished file".getBytes())).hasMessageContaining("不完整");CertificatePdfService.validate(unsigned);}
 @Test void previewIsPdfAndCannotBeMistakenForSignedOutput(){
  var auth=mock(AuthService.class);var certificates=mock(EmploymentCertificateService.class);var signing=mock(CertificateSigningService.class);var pdf=mock(CertificatePdfService.class);var request=mock(HttpServletRequest.class);var actor=LifecycleServiceTest.user(1L,1L,com.hragent.hragentv1.domain.Role.EMPLOYEE);byte[] docx={80,75,1,2};
  when(auth.requireUser(request)).thenReturn(actor);when(certificates.download(actor,10L)).thenReturn(new EmploymentCertificateService.DocumentDownload("原稿.docx",docx));when(signing.status(actor,10L)).thenReturn(Map.of("signed",false));when(pdf.preview(docx)).thenReturn(unsigned);
  var controller=new CertificateDocumentController(auth,certificates,signing,pdf);var response=controller.document(request,10L,true);
  assertThat(response.getBody()).isEqualTo(unsigned);assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("inline");assertThat(response.getHeaders().getContentDisposition().getFilename()).contains("未签章预览");assertThat(response.getHeaders().getFirst("X-Certificate-Signed")).isEqualTo("false");assertThat(response.getHeaders().getCacheControl()).contains("no-store");verify(signing,never()).download(any(),any());
  when(signing.status(actor,10L)).thenReturn(Map.of("signed",true));when(signing.download(actor,10L)).thenReturn(signed);clearInvocations(pdf);
  var finalFile=controller.document(request,10L,false);assertThat(finalFile.getBody()).isEqualTo(signed);assertThat(finalFile.getHeaders().getContentDisposition().getFilename()).contains("已签章.pdf");assertThat(finalFile.getHeaders().getFirst("X-Certificate-Signed")).isEqualTo("true");verifyNoInteractions(pdf);
 }
 @Test void ownershipIsCheckedBeforeReadingOrSigning(){
  var auth=mock(AuthService.class);var certificates=mock(EmploymentCertificateService.class);var signing=mock(CertificateSigningService.class);var pdf=mock(CertificatePdfService.class);var request=mock(HttpServletRequest.class);var actor=LifecycleServiceTest.user(1L,1L,com.hragent.hragentv1.domain.Role.EMPLOYEE);
  when(auth.requireUser(request)).thenReturn(actor);when(certificates.download(actor,99L)).thenThrow(AppException.forbidden("只能下载自己的证明"));assertThatThrownBy(()->new CertificateDocumentController(auth,certificates,signing,pdf).document(request,99L,true)).hasMessageContaining("自己的证明");verifyNoInteractions(signing,pdf);
 }
}
