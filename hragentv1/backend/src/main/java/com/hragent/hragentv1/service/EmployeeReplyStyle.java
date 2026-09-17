package com.hragent.hragentv1.service;

import com.hragent.hragentv1.dto.WebChatDtos.MessageResponse;

/** Presentation only: actions, sources, amounts, dates and approval decisions remain untouched. */
public final class EmployeeReplyStyle {
 private EmployeeReplyStyle() {}
 public static MessageResponse soften(MessageResponse reply){
  if(reply==null||reply.answer()==null||reply.answer().isBlank())return reply;
  String provider=reply.provider()==null?"":reply.provider();String text=reply.answer();
  if(provider.startsWith("er-")||ErSafety.has(text,"立即拨打","伤害自己","自杀","紧急风险","性骚扰","劳动仲裁","违规举报"))return reply;
  String answer=text;
  if(provider.equals("workplace-support")){
   answer=answer.replaceFirst("^听起来你现在有些难受。","先送你一朵小花 🌷 今天有点难受呢，我听你慢慢说。")
    .replaceFirst("^任务堆在一起，确实容易让人喘不过气。","任务挤在一起，脑袋也会有点打结呢。辛苦啦，我们一起把它们拆小一点。")
    .replaceFirst("^做平台要顾到的事情很多，觉得累很正常，","做平台要顾到好多事情呀，辛苦啦 🌱 ")
    .replaceFirst("^这项任务压在心上，确实不容易。","这件事一直挂在心上，会累的呀。先给自己一点温柔的空间。")
    .replaceFirst("^可以，先照顾好自己的状态。","好呀，先照顾好自己，我们慢慢来 🌷 ")
    .replaceFirst("^好，我们先聊到这里。","好呀，我们先聊到这里。给自己留一点轻松的时间吧。")
    .replaceFirst("^我在听。","嗯嗯，我在认真听呢。")
    .replaceFirst("^好，我们先不急着给建议。","好呀，先不急着想办法，我听你说。")
    .replaceFirst("^那就先","好呀，那就先");
  }else{
   answer=answer.replaceFirst("^好，我们开始填写","好呀，我陪你一步一步填写")
    .replaceFirst("^请确认日期：你是指","日期帮你整理好啦 🌱 你是指")
    .replaceFirst("^好，日期记下了。","好哒，日期记下啦。")
    .replaceFirst("^已取消这次填写，","好哒，已取消这次填写，")
    .replaceFirst("^已提交，服务单","提交好啦，服务单")
    .replaceFirst("^请核对这份","快完成啦，我们一起核对这份")
    .replaceFirst("^我查到的最新请假进度：","帮你查到最新进度啦 🌱")
    .replaceFirst("^你还没有请假申请。","还没有找到你的请假申请呢。")
    .replaceFirst("^暂时没查到","我这边暂时还没查到")
    .replaceFirst("^我还没能准确理解你想解决的事。","我想再听明白一点，好陪你找到合适的下一步。")
    .replace("你要请哪种假？","这次想请哪种假呀？")
    .replace("从哪天开始？","想从哪天开始休息呀？")
    .replace("到哪天结束呢？","想休息到哪天呢？")
    .replace("请简要说明请假原因。","再简单说说请假原因，就快填好啦。")
    .replace("还差一份病历或诊断证明。","还需要补充一份病历或诊断证明，我陪你一起完成。")
    .replace("请填写完整日期。","告诉我具体日期就好，我来帮你整理。");
   // Short, factual policy replies retain their original wording and evidence.
   if(answer.equals(text)&&ErSafety.has(provider,"policy","knowledge","handbook")&&!text.contains("🌱"))answer="陪你一起看一下呀 🌱\n\n"+text;
  }
  return new MessageResponse(answer,reply.provider(),reply.requestId(),reply.actions(),reply.details());
 }
}
