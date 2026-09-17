package com.hragent.hragentv1.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import com.hragent.hragentv1.web.AppException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

/** Local extraction only: medical documents never enter general chat or n8n execution history. */
@Service
public class MedicalTextParser {
 private final RestClient client;
 private final String pdfUrl,ocrUrl;
 public MedicalTextParser(@Value("${app.medical.pdf-url:http://hragent-n8n-pdf-parser:3000/extract}") String pdfUrl,
                          @Value("${app.medical.ocr-url:http://hragent-n8n-ocr:8000/ocr}") String ocrUrl){
  this.pdfUrl=pdfUrl;this.ocrUrl=ocrUrl;
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  factory.setReadTimeout(Duration.ofSeconds(75));client=RestClient.builder().requestFactory(factory).build();
 }
 public String extract(byte[] bytes,String extension){
  try {
   var response=client.post().uri(extension.equals("pdf")?pdfUrl:ocrUrl).body(Map.of("data",Base64.getEncoder().encodeToString(bytes))).retrieve().body(Map.class);
   if(response==null||!Boolean.TRUE.equals(response.get("success"))||!(response.get("text") instanceof String text)||text.isBlank()||text.length()>60000)throw new IllegalStateException();
   if(response.get("averageConfidence") instanceof Number confidence && confidence.doubleValue()<0.75)throw new IllegalStateException();
   return text;
  }catch(Exception ignored){throw AppException.badRequest("这份材料暂时没能清楚识别。请上传清晰的 JPG/PNG 病历照片或带文字的 PDF；若仍失败，请联系 HR 协助。材料尚未通过初检，也没有提交审批。");}
 }
}
