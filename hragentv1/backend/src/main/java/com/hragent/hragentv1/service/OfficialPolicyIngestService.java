package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class OfficialPolicyIngestService {
 private final PolicyMonitorCandidateRepository candidates;
 public OfficialPolicyIngestService(PolicyMonitorCandidateRepository candidates){this.candidates=candidates;}
 public java.util.Map<String,String> watched(String sourceName){
  var result=new java.util.LinkedHashMap<String,String>();
  candidates.findBySourceNameOrderBySourceUrlAsc(sourceName).stream().filter(c->c.getSourceId().startsWith("official-")).forEach(c->result.putIfAbsent(c.getSourceUrl(),c.getTitle()));return result;
 }
 public static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 @Transactional public boolean ingest(Long tenantId,String sourceName,String url,String region,String title,String content,LocalDate published,LocalDate effective){
  String sourceId="official-"+hash(url),digest=hash(title+"\n"+content);
  if(candidates.findByTenantIdAndSourceIdAndContentHash(tenantId,sourceId,digest).isPresent())return false;
  var c=new PolicyMonitorCandidate();c.setTenantId(tenantId);c.setSourceId(sourceId);c.setSourceName(sourceName);c.setSourceUrl(url);c.setTitle(title);c.setContent(content);c.setContentHash(digest);c.setRegion(region);c.setPublishedAt(published);c.setEffectiveAt(effective);c.setVersion(digest.substring(0,12));c.setDetectedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));c.setSourceUpdatedAt(published==null?null:published.atStartOfDay());c.setReviewStatus(PolicyReviewStatus.PENDING_REVIEW);
  c.setChangeSummary("官方栏目首次发现或正文发生变化；请核验适用范围、生效日期、附件及旧版废止关系。"+(effective==null?"未自动确认生效日期。":""));
  candidates.save(c);return true;
 }
}
