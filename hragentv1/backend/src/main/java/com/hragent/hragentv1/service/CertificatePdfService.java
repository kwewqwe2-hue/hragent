package com.hragent.hragentv1.service;

import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.*;

/** Private local conversion. Original DOCX and signed PDFs are never rewritten. */
@Service
public class CertificatePdfService {
 private final Semaphore slots=new Semaphore(2);
 public byte[] preview(byte[] docx){
  Path dir=null;Process process=null;boolean acquired=false;
  try{
   acquired=slots.tryAcquire(5,TimeUnit.SECONDS);if(!acquired)throw AppException.badRequest("证明预览正在处理中，请稍后重试");
   dir=Files.createTempDirectory("hr-certificate-pdf-");Path input=dir.resolve("certificate.docx");
   try(var document=new XWPFDocument(new java.io.ByteArrayInputStream(docx));var output=Files.newOutputStream(input)){
    var header=document.createHeader(HeaderFooterType.DEFAULT);var paragraph=header.createParagraph();paragraph.setAlignment(ParagraphAlignment.RIGHT);
    var run=paragraph.createRun();run.setText("未签章预览 · 正式文件以电子签章版为准");run.setFontSize(8);run.setColor("777777");run.setFontFamily("Noto Sans CJK SC");document.write(output);
   }
   Path log=dir.resolve("convert.log");
   process=new ProcessBuilder("libreoffice","-env:UserInstallation="+dir.resolve("profile").toUri(),"--headless","--convert-to","pdf:writer_pdf_Export","--outdir",dir.toString(),input.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
   if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();throw AppException.badRequest("证明转换超时，请稍后重试");}
   Path pdf=dir.resolve("certificate.pdf");if(process.exitValue()!=0||!Files.isRegularFile(pdf))throw AppException.badRequest("证明预览暂时无法生成，请联系 HR 检查模板排版");
   byte[] bytes=Files.readAllBytes(pdf);validate(bytes);return bytes;
  }catch(AppException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw AppException.badRequest("证明预览已中断，请重试");}catch(Exception e){throw AppException.badRequest("证明转换服务暂不可用，请稍后重试");}
  finally{if(process!=null&&process.isAlive())process.destroyForcibly();if(acquired)slots.release();if(dir!=null)try(var paths=Files.walk(dir)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}catch(Exception ignored){}}
 }
 public static void validate(byte[] bytes){if(bytes==null||bytes.length<20||bytes.length>20*1024*1024||!new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-")||!new String(bytes,Math.max(0,bytes.length-1024),Math.min(1024,bytes.length),StandardCharsets.ISO_8859_1).contains("%%EOF"))throw AppException.badRequest("证明 PDF 文件不完整，请重新获取");}
}
