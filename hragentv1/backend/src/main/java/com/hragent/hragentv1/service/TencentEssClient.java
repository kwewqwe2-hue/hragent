package com.hragent.hragentv1.service;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Server-side TC3 client. Hosts are fixed; credentials and document data are never logged. */
@Component
public class TencentEssClient {
 public record Settings(String secretId,String secretKey,String operatorId,String organizationName,String sealId,String sealKeyword) {}
 private final ObjectMapper json=new ObjectMapper();
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
 public JsonNode call(Settings config,String action,Map<String,Object> input) {
  String host=action.equals("UploadFiles")?"file.ess.tencent.cn":"ess.tencentcloudapi.com";
  try {
   String body=json.writeValueAsString(input);long timestamp=Instant.now().getEpochSecond();
   var request=HttpRequest.newBuilder(URI.create("https://"+host+"/")).timeout(Duration.ofSeconds(40))
    .header("Content-Type","application/json; charset=utf-8").header("X-TC-Action",action).header("X-TC-Version","2020-11-11")
    .header("X-TC-Timestamp",Long.toString(timestamp)).header("Authorization",authorization(config,host,body,timestamp))
    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
   var response=http.send(request,HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()!=200)throw new IllegalStateException("电子签网络响应异常，HTTP "+response.statusCode());
   JsonNode data=json.readTree(response.body()).path("Response");
   if(data.has("Error"))throw new ApiFailure(data.path("Error").path("Code").asText(),data.path("RequestId").asText());
   if(data.isMissingNode())throw new IllegalStateException("电子签响应格式异常");
   return data;
  }catch(ApiFailure e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("电子签请求中断",e);}catch(Exception e){throw new IllegalStateException("电子签连接失败，请查询签章任务状态",e);}
 }
 public static String authorization(Settings c,String host,String body,long timestamp)throws Exception {
  String date=Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC).toLocalDate().toString();
  String canonical="POST\n/\n\ncontent-type:application/json; charset=utf-8\nhost:"+host+"\n\ncontent-type;host\n"+hash(body);
  String scope=date+"/ess/tc3_request";
  String toSign="TC3-HMAC-SHA256\n"+timestamp+"\n"+scope+"\n"+hash(canonical);
  byte[] key=hmac(("TC3"+c.secretKey()).getBytes(StandardCharsets.UTF_8),date);key=hmac(key,"ess");key=hmac(key,"tc3_request");
  return "TC3-HMAC-SHA256 Credential="+c.secretId()+"/"+scope+", SignedHeaders=content-type;host, Signature="+HexFormat.of().formatHex(hmac(key,toSign));
 }
 static String hash(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
 static byte[] hmac(byte[] key,String value)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(value.getBytes(StandardCharsets.UTF_8));}
 public byte[] download(String url){
  try{URI uri=URI.create(url);String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443)||!(host.equals("file.ess.tencent.cn")||host.equals("file.ess.myqcloud.com")||host.endsWith(".myqcloud.com")))throw new IllegalStateException("电子签下载地址不正确");
   var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(40)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
   try(var in=response.body()){if(response.statusCode()!=200)throw new IllegalStateException("电子签文件尚未就绪，请稍后下载");byte[] data=in.readNBytes(20*1024*1024+1);if(data.length>20*1024*1024||data.length<5||!new String(data,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw new IllegalStateException("电子签文件格式或大小异常");return data;}
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("下载中断",e);}catch(Exception e){throw new IllegalStateException("电子签 PDF 下载失败，请重试",e);}
 }
 public static class ApiFailure extends RuntimeException {
  public final String code;
  public ApiFailure(String code,String requestId){super("腾讯电子签："+code+"（请求 "+requestId+"）");this.code=code;}
  public boolean definiteRejection(){return List.of("InvalidParameter","MissingParameter","AuthFailure","UnauthorizedOperation","OperationDenied","ResourceNotFound","LimitExceeded","RequestLimitExceeded").stream().anyMatch(code::startsWith);}
 }
}
