package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.WebChatDtos.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Stores only a short-lived topic, never policy text, employee answers or business drafts. */
final class PolicyConversationGuide {
    private record Key(Long tenant, Long employee, String conversation) {}
    private record Topic(String name, String question, long expires) {}
    private final Map<Key,Topic> topics = new ConcurrentHashMap<>();
    private final AnnualLeaveConversation annual = new AnnualLeaveConversation();
    private final MaternityLeaveConversation maternity = new MaternityLeaveConversation();
    void clear(UserAccount user,String cid){annual.clear(user,cid);maternity.clear(user,cid);var k=key(user,cid);if(k!=null)topics.remove(k);}
    boolean interviewRelevant(UserAccount user,String message,String cid){return (annual.relevant(user,message,cid)||maternity.relevant(user,message,cid))&&LifecycleService.intent(message)==null;}
    private static final long TTL = 15 * 60 * 1000L;
    private Key key(UserAccount user,String cid) { return cid == null || cid.isBlank() ? null : new Key(user.getTenantId(),user.getId(),cid); }
    private static String clean(String text) { return text.replaceAll("[\\s。！？!?]", ""); }
    private void remember(UserAccount user,String cid,String topic) {
        remember(user,cid,topic,topic);
    }
    private void remember(UserAccount user,String cid,String topic,String question) {
        var key=key(user,cid); if(key==null)return;
        topics.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis());
        if(topics.size()>=2000)topics.clear();
        topics.put(key,new Topic(topic,question,System.currentTimeMillis()+TTL));
    }
    Optional<MessageResponse> opening(UserAccount user,String message,String cid) {
        var birth=maternity.reply(user,message,cid);
        if(birth.isPresent()){annual.clear(user,cid);remember(user,cid,"产假");return birth;}
        var yearly=annual.reply(user,message,cid);
        if(yearly.isPresent()){remember(user,cid,"年假");return yearly;}
        var remembered=key(user,cid)==null?null:topics.get(key(user,cid));
        boolean leaveApplication=message.matches(".*(请假|休假|年假|病假|婚假|产假).*")||clean(message).matches("^(我|那我|这个|那|接下来)?(应该|该|要|可以)?(怎么|如何|怎样|在哪里|在哪儿)(申请|办理|提交)(呢|呀|啊)?$");
        if(leaveApplication&&MaternityLeaveConversation.applicationQuestion(clean(message))&&(remembered==null||remembered.expires<System.currentTimeMillis()||remembered.name.matches("年假|休假|病假|婚假|产假"))){
            String leave=remembered!=null&&remembered.expires>=System.currentTimeMillis()&&remembered.name.matches("年假|病假|婚假|产假")?remembered.name:"";
            if(!message.matches(".*(报销|证明|离职|合同|社保|公积金|出差).*"))return Optional.of(reply("可以呀，我陪你一步步填写。"+(leave.isBlank()?"你是想申请请假吗？可以点下方开始，先选假别，再填日期和原因。":"我们接着办理"+leave+"，点下方开始，再告诉我计划休假的日期就好。")+"最后会请你核对，确认后才提交审核。",List.of(say(leave.isBlank()?"开始请假申请":"开始"+leave+"申请",leave.isBlank()?"我要申请请假":"我要申请"+leave),leave.equals("产假")?LifecycleNavigation.records():LifecycleNavigation.page("前往请假页面","/my-leave"))));
        }
        String text=clean(message).replaceFirst("^(我想了解一下|我想了解|我想问问|我想咨询|了解一下|咨询一下|查一下)", "");
        if(Set.of("你好","您好","嗨","hello","hi").contains(text.toLowerCase())) return Optional.of(reply("你好，我在。想办点事情、问问制度，或者聊聊最近的工作，都可以直接告诉我。",List.of()));
        if(Set.of("谢谢","谢谢你","多谢","好的谢谢").contains(text)) return Optional.of(reply("不客气。有需要时，接着告诉我就好。",List.of()));
        String topic=switch(text) {
            case "年假","年假政策","年休假","年假规定" -> "年假";
            case "休假","休假政策","休假有哪些","休假有哪些政策" -> "休假";
            case "出差","差旅","出差政策","差旅政策","出差有哪些" -> "出差";
            case "出差住宿标准是什么","出差住宿标准","住宿标准" -> "出差住宿";
            case "报销","报销制度","报销注意什么","报销有哪些" -> "报销";
            case "考勤","考勤要求","考勤有哪些" -> "考勤";
            case "政策","制度","政策咨询","制度咨询" -> "制度";
            default -> "";
        };
        if(topic.isEmpty())return Optional.empty();
        remember(user,cid,topic);
        var options=switch(topic) {
            case "年假" -> List.of(say("年假天数","年假天数如何规定"),say("使用期限","年假可以用到什么时候"),say("申请方式","年假申请需要什么手续"));
            case "休假" -> List.of(say("年假","我想了解年假"),say("病假","病假需要什么材料"),say("产假","产假有哪些适用条件"));
            case "出差" -> List.of(say("住宿标准","出差住宿标准是什么"),say("交通与餐费","出差交通和餐费如何报销"),say("申请流程","出差申请需要什么手续"));
            case "出差住宿" -> List.of(say("北京","北京的出差住宿标准"),say("上海","上海的出差住宿标准"));
            case "报销" -> List.of(say("差旅报销","出差报销需要什么材料"),say("借款审批","员工借款审批有什么规定"));
            case "考勤" -> List.of(say("工作时间","工作时间有什么规定"),say("加班与调休","加班和调休有什么规定"));
            default -> List.of(say("休假考勤","我想了解休假"),say("差旅报销","我想了解出差"),say("薪酬福利","薪酬福利有哪些"));
        };
        String question=switch(topic) {
            case "年假" -> "年假通常需要分清天数、使用期限和申请方式。你现在最想了解哪一项？";
            case "休假" -> "可以，我们先看你关心的那一种假。你想了解年假、病假，还是产假？";
            case "出差" -> "出差涉及申请、费用标准和报销几个环节。你这次主要想了解住宿标准，还是怎么办手续？";
            case "出差住宿" -> "住宿标准要结合目的地、出行日期和岗位来确认。我们先看目的地：你这次去哪个城市？";
            case "报销" -> "报销要求会随费用类型不同而变化。你要报的是差旅费用，还是其他费用？";
            case "考勤" -> "你是想确认日常工作时间，还是遇到了加班、调休方面的问题？";
            default -> "可以，我帮你找重点。你想先了解休假、差旅报销，还是薪酬福利？";
        };
        return Optional.of(reply(question,options));
    }
    String resolve(UserAccount user,String message,String cid) {
        var key=key(user,cid); var topic=key==null?null:topics.get(key);
        if(topic==null || topic.expires<System.currentTimeMillis())return message;
        String text=clean(message);
        if(topic.name.equals("出差住宿")&&Set.of("普通员工","员工","中层干部","中心副总监及以上").contains(text))return topic.question+"，按"+text+"的住宿标准";
        if(topic.name.equals("年假")&&Set.of("还剩多少","还剩几天","现在还有几天","还有几天","查一下余额").contains(text))return "查询我的年假余额";
        if(text.matches("北京|上海|广州|深圳|杭州|南京|成都|重庆|武汉|西安|苏州|天津|青岛|三亚|拉萨|大连|厦门|长沙|郑州|济南"))return topic.name.equals("出差住宿")?text+"的出差住宿标准是什么":text+topic.name+"适用条件";
        if(topic.name.equals("加班")&&Set.of("工作日","周末","休息日","法定节假日").contains(text))return text+"加班有什么规定";
        if(topic.name.matches("借款|报销")&&text.matches("(?:大概|约)?[0-9]+(?:\\.[0-9]+)?[万千]?元?"))return "员工"+topic.name+text+"审批规定";
        if(Set.of("天数","多少天","使用期限","有效期","什么时候到期","怎么申请","申请方式","申请流程","需要什么材料","材料","适用条件","住宿标准","交通与餐费","原文","查看原文","详细说说").contains(text)) {
            String detail=switch(text){case "有效期","使用期限","什么时候到期" -> "可以用到什么时候";case "天数","多少天" -> "天数如何规定";case "申请方式","怎么申请","申请流程" -> "申请需要什么手续"; default->text;};
            return topic.name + detail;
        }
        // An unrelated turn ends the short-answer context instead of reviving an old topic later.
        topics.remove(key);
        return message;
    }
    MessageResponse present(UserAccount user,String question,String cid,EmployeeAgentRouter.Reply routed,String requestId) {
        String answer=routed.answer();
        if(routed.provider().equals("policy-catalog"))return new MessageResponse("可以聊休假考勤、薪酬社保、差旅报销，也可以问合同和员工关怀。你最近遇到了什么具体问题？",routed.provider(),requestId,List.of(say("休假考勤","我想了解休假"),say("差旅报销","我想了解出差"),say("薪酬福利","薪酬福利有哪些")));
        if(!Set.of("company-policy-documents","knowledge-reference","er-verified-guidance","employee-services").contains(routed.provider()))return new MessageResponse(answer,routed.provider(),requestId,routed.actions());
        if(answer==null || answer.isBlank())return new MessageResponse(answer,routed.provider(),requestId);
        String topic=question.contains("年假")||question.contains("年休假")?"年假":question.contains("住宿")?"出差住宿":question.contains("出差")?"出差":question.contains("借款")?"借款":question.contains("报销")?"报销":question.contains("育儿假")?"育儿假":question.contains("产假")?"产假":question.contains("婚假")?"婚假":question.contains("病假")?"病假":question.contains("加班")?"加班":"制度";
        if(!topic.isBlank())remember(user,cid,topic,question);
        if(question.matches(".*(原文|全文|完整条文|详细说说|详细解释).*"))return new MessageResponse(answer,routed.provider(),requestId);
        // Do not collapse a personal result or an already brief, unsupported-policy reply.
        if(!answer.contains("出处") && !answer.contains("生效") && answer.length()<180)return new MessageResponse(answer,routed.provider(),requestId,routed.actions());
        String brief="";
        boolean clarification=false;
        boolean conflict=answer.matches("(?s).*(这条制度需要先核对版本|需要先核实制度版本|同一条款出现不同内容).*" );
        if(!conflict&&AnnualLeaveConversation.question(question)){
            var personal=annual.reply(user,question,cid);
            if(personal.isPresent()){var r=personal.get();return new MessageResponse(r.answer(),routed.provider(),requestId,r.actions(),answer);}
        }
        if(conflict)brief="这条规定的版本还需要核对，现在不能据此确定你的执行标准。我把发现的依据留在下方，方便和 HR 一起确认。";
        else {
            // Use complete curated paragraphs; never cut a number away from its conditions.
            for(String block:answer.split("\\n\\n")) {
                if(!block.contains("｜") || !block.contains("\n"))continue;
                String body=block.substring(block.indexOf('\n')+1).split("\n(?:出处|说明)：",2)[0].strip();
                String specific=scenarioSummary(body,question);
                if(!specific.isBlank()){brief=specific;break;}
                if(!body.isBlank() && body.length()<=130) {brief=body;break;}
            }
            if(brief.isBlank() && answer.length()<150)brief=answer;
            if(topic.equals("年假") && question.matches(".*(什么时候|有效期|使用期限|到期).*")) {
                var deadline=java.util.regex.Pattern.compile("[^。\\n]*年假使用到[^。\\n]*。").matcher(answer);
                if(deadline.find())brief=deadline.group();
            }
            if(brief.isBlank())brief=focusedParagraph(answer,question);
            if(brief.isBlank()){brief=focusQuestion(question,topic);clarification=true;}
            if(topic.equals("年假")&&brief.contains("工龄")){brief=focusQuestion(question,topic);clarification=true;}
            // Applicability notes belong after a substantive explanation, not beside a question collecting context.
            if(!clarification){
                if(answer.contains("不能直接认定") || answer.contains("暂时没有可确认适用"))brief+="\n\n这份文件是否适用于你，还需要 HR 核对合同主体和用工身份。";
                if(routed.provider().equals("knowledge-reference"))brief+="\n\n目前找到的是 2022 年参考资料，不能当作现行执行标准。";
                if(answer.contains("法律依据与核对提示"))brief+="\n\n这项规定还涉及法规补充，需结合工作地核对，不能只看企业条款。";
            }
        }
        if(!conflict && !brief.contains("？"))brief+="\n\n"+(topic.equals("出差住宿")?(city(question).isBlank()?"你这次去哪个城市出差？":"还需要我帮你看看报销要准备什么材料吗？"):topic.equals("年假")?"你还想了解怎么申请，还是先查自己的年假余额？":"你还想了解申请步骤，还是适用条件？");
        var actions=new ArrayList<ChatAction>();
        if(topic.equals("年假")){actions.add(say("查我的年假余额","查询我的年假余额"));actions.add(say("了解申请方式","年假申请需要什么手续"));}
        if(topic.equals("出差住宿")&&!city(question).isBlank()&&!question.matches(".*(员工|中层干部|副总监).*")){actions.add(say("普通员工",city(question)+"普通员工出差住宿标准"));actions.add(say("中层干部",city(question)+"中层干部出差住宿标准"));}
        if(brief.length()>230)brief=focusQuestion(question,topic);
        return new MessageResponse(brief,routed.provider(),requestId,actions,answer);
    }
    private static String city(String q){return Arrays.stream("北京,上海,广州,深圳,杭州,南京,成都,重庆,武汉,西安,苏州,天津,青岛,三亚,拉萨,大连,厦门,长沙,郑州,济南".split(",")).filter(q::contains).findFirst().orElse("");}
    private static String scenarioSummary(String body,String question){
        if(question.contains("住宿")&&!city(question).isBlank()&&question.matches(".*(员工|中层干部|副总监).*")){
            var rates=java.util.regex.Pattern.compile("北京、上海、广州、深圳：中层干部每人每晚最高([0-9]+)元，员工([0-9]+)元；其他城市：中层干部([0-9]+)元，员工([0-9]+)元").matcher(body);
            if(rates.find()){
                boolean major=Set.of("北京","上海","广州","深圳").contains(city(question)),manager=question.matches(".*(中层干部|副总监).*" );
                String amount=rates.group(major?(manager?1:2):(manager?3:4));
                return "按这份文件，"+city(question)+(manager?"中层干部":"普通员工")+"住宿的"+(major?"":"非旺季基础")+"上限是 **"+amount+" 元/人晚**，限额内按实际费用报销。"+(major?"":"旺季是否上浮还要核对出差日期。");
            }
        }
        if(question.contains("借款")){
            var amount=java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(万|千)?元").matcher(question);
            if(amount.find()){
                var n=new java.math.BigDecimal(amount.group(1)).multiply(java.math.BigDecimal.valueOf("万".equals(amount.group(2))?10000:"千".equals(amount.group(2))?1000:1));
                var row=java.util.regex.Pattern.compile("员工借款([0-9]+)(万)?元(?:(（含）以下)|(以上至)([0-9]+)(万)?元（含）|(以上))：审批 ([^。\\n]+)。").matcher(body);
                while(row.find()){
                    var lower=new java.math.BigDecimal(row.group(1)).multiply(java.math.BigDecimal.valueOf(row.group(2)==null?1:10000));
                    boolean match=row.group(3)!=null?n.signum()>0&&n.compareTo(lower)<=0:row.group(4)!=null?n.compareTo(lower)>0&&n.compareTo(new java.math.BigDecimal(row.group(5)).multiply(java.math.BigDecimal.valueOf(row.group(6)==null?1:10000)))<=0:n.compareTo(lower)>0;
                    if(match)return "按这份文件，你问的这笔员工借款走这一档："+row.group()+"这是支付审批，业务事前审批和预算也需先确认。";
                }
            }
        }
        return "";
    }
    private static String focusedParagraph(String answer,String question){
        // Keep a complete source sentence and its conditions; never take a naked amount from a table.
        var candidates=new ArrayList<String>();
        for(String block:answer.split("\\n\\n")){
            if(!block.contains("｜")||!block.contains("\n"))continue;
            String body=block.substring(block.indexOf('\n')+1).split("\n(?:出处|说明)：",2)[0].strip();
            for(String sentence:body.split("(?<=。)")){
                String s=sentence.strip();
                if(s.length()>12&&s.length()<=110&&!s.matches("^(但|其中|上述|以上|此外|其余|除外|未满|超过|不足|：).*"))candidates.add(s);
            }
        }
        String[] focus=question.matches(".*(期限|什么时候|多久|几天内).*" )?new String[]{"期限","以内","内","截止"}:question.matches(".*(材料|凭证|发票).*" )?new String[]{"材料","凭证","发票","证明"}:question.matches(".*(申请|办理|审批|手续).*" )?new String[]{"申请","审批","批准","手续"}:new String[0];
        // Monetary/medical/legal conditions can span sentences: defer their calculation to verified full clauses.
        return candidates.stream().filter(s->!s.matches(".*(元|%|工资|赔偿|婚假|产假|病假|医疗期).*" )).filter(s->Arrays.stream(focus).anyMatch(s::contains)).findFirst().orElse("");
    }
    private static String focusQuestion(String question,String topic){
        if(topic.equals("年假"))return "我们先看和你有关的部分。你想了解按工龄能有几天年假，还是准备申请休假？";
        if(topic.equals("出差住宿"))return city(question).isBlank()?"我们先看目的地。你这次去哪个城市出差？":question.matches(".*(员工|中层干部|副总监).*" )?"地点和岗位已经明确了，但当前材料还不足以可靠匹配金额。我把依据留在下方，可请 HR 核对这一档标准。":"好的，先按"+city(question)+"来核对。你这次按普通员工还是中层干部标准报销？";
        if(topic.equals("借款"))return question.matches(".*[0-9]+.*" )?"金额已经明确了，当前找到的审批表还需要核对业务类型。你说的是个人员工借款，还是业务备用金？":"我们先看这笔借款。你打算借多少金额？";
        if(question.matches(".*(报销|支付).*"))return "我们先看你这笔费用。具体要报销或支付的是什么费用？";
        if(question.contains("病假"))return "你是准备申请病假，还是想先了解需要哪些材料？我按你眼前要办的事说明。";
        if(question.matches(".*(产假|婚假|育儿假|陪产假).*"))return city(question).isBlank()?"好呀，我们先看工作地的安排。你在哪个城市工作呢？":"好的，我们按"+city(question)+"来看。你想先了解假期天数，还是申请要准备什么？";
        if(question.matches(".*(加班|调休).*"))return "先确认一下，你说的是工作日下班后加班、周末加班，还是法定节假日加班？";
        return "我找到相关规定了。你现在是想了解适用条件，还是正准备办理？我们一次看一个重点。";
    }
    private static ChatAction say(String label,String query) {return new ChatAction(label,"message",query);}
    private static MessageResponse reply(String answer,List<ChatAction> actions) {return new MessageResponse(answer,"conversation-guide",UUID.randomUUID().toString(),actions);}
}
