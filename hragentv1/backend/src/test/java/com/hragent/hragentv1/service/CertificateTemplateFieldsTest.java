package com.hragent.hragentv1.service;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.*;
class CertificateTemplateFieldsTest {
 @Test void recognizesChineseLabelsBlankCellsHeadersAndSplitRuns()throws Exception{
  try(var doc=new XWPFDocument()){
   var p=doc.createParagraph();p.createRun().setBold(true);p.getRuns().getFirst().setText("【姓");p.createRun().setText("名】；职位：____；【接收单位】");
   var table=doc.createTable(1,2);table.getRow(0).getCell(0).setText("入职日期");
   doc.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("${公司名称}");
   var out=new java.io.ByteArrayOutputStream();doc.write(out);
   try(var reopened=new XWPFDocument(new java.io.ByteArrayInputStream(out.toByteArray()))){assertThat(CertificateTemplateFields.normalize(reopened)).containsExactly("legalName","title","接收单位","entryDate","companyName");}
   CertificateTemplateFields.normalize(doc);
   assertThat(p.getRuns().getFirst().isBold()).isTrue();
   assertThat(table.getRow(0).getCell(1).getText()).contains("{{entryDate}}");
  }
 }
 @Test void replacementsDoNotReinterpretValueAsInstructionsOrLoop()throws Exception{
  try(var doc=new XWPFDocument()){var p=doc.createParagraph();p.createRun().setText("{{姓名}} and {{姓名}}");org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1),()->CertificateTemplateFields.replace(p,"{{姓名}}","{{姓名}}literal"));assertThat(p.getText()).isEqualTo("{{姓名}}literal and {{姓名}}literal");}
 }
 @Test void leavesUnlabelledBlanksUntouchedAndAddsOneSealAnchor()throws Exception{
  try(var doc=new XWPFDocument()){doc.createParagraph().createRun().setText("未注明：____");assertThat(CertificateTemplateFields.normalize(doc)).isEmpty();CertificateTemplateFields.sealAnchor(doc);CertificateTemplateFields.sealAnchor(doc);assertThat(doc.getParagraphs().stream().filter(p->p.getText().contains("公司盖章处"))).hasSize(1);}
 }
}
