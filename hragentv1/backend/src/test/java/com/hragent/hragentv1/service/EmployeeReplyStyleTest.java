package com.hragent.hragentv1.service;
import com.hragent.hragentv1.dto.WebChatDtos.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class EmployeeReplyStyleTest {
 @Test void gentleVoiceKeepsDatesActionsAndOfficialEvidenceExact(){
  var actions=List.of(new ChatAction("确认日期","message","确认日期"));String details="制度原文：第三条，生效日期2025-10-14";
  var r=EmployeeReplyStyle.soften(new MessageResponse("请确认日期：你是指 **2026-09-15** 至 **2026-09-17**，对吗？","hrssc-lifecycle","id",actions,details));
  assertThat(r.answer()).contains("整理好啦","2026-09-15","2026-09-17");assertThat(r.actions()).isSameAs(actions);assertThat(r.details()).isEqualTo(details);assertThat(r.requestId()).isEqualTo("id");
 }
 @Test void careIsWarmWithoutRemovingTheConcreteNextStep(){
  var r=EmployeeReplyStyle.soften(new MessageResponse("做平台要顾到的事情很多，觉得累很正常，不用逼自己一下子全部做完。先挑一条核心流程。","workplace-support","id"));
  assertThat(r.answer()).contains("辛苦啦","核心流程").doesNotContain("宝宝","乖");
 }
 @Test void crisisAndComplianceRepliesRemainUnchanged(){
  for(String provider:List.of("er-human-support","er-safety")){var r=new MessageResponse("请先确认你现在是否安全。",""+provider,"id");assertThat(EmployeeReplyStyle.soften(r)).isSameAs(r);}
  var legal=new MessageResponse("劳动仲裁可以通过正式渠道咨询。","policy","id");assertThat(EmployeeReplyStyle.soften(legal)).isSameAs(legal);
 }
 @Test void pendingSubmissionNeverBecomesApproval(){
  var r=EmployeeReplyStyle.soften(new MessageResponse("已提交，服务单 #9。等待主管审批。","hrssc-lifecycle","id"));
  assertThat(r.answer()).contains("提交好啦","等待主管审批").doesNotContain("审批通过");
 }
}
