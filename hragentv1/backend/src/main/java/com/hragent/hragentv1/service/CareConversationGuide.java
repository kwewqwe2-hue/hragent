package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.WebChatDtos.ChatAction;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived conversation stages only. No diagnoses, emotional scores or stored employee statements. */
final class CareConversationGuide {
    enum Stage { EMOTION, REST, PAUSE, LEAVE, NO_LEAVE, ALTERNATIVES, HESITANT, WORKLOAD, TASK_PLAN, NEXT_STEP, MANAGER, PUSHBACK, CONFLICT, CRITICISM, LISTEN, HEAR, SLEEP, FACTS, NEED, PLAN, PRIVACY, SUPPORT, HR, BETTER, CLOSE }
    record Result(String answer,List<ChatAction> actions) {}
    private record Key(Long tenant,Long employee,String conversation) {}
    private record Context(Stage stage,boolean noLeave,long expires) {}
    private final Map<Key,Context> contexts=new ConcurrentHashMap<>();
    private final Clock clock;
    CareConversationGuide(){this(Clock.systemUTC());}
    CareConversationGuide(Clock clock){this.clock=clock;}
    private Key key(UserAccount u,String cid){return cid==null||cid.isBlank()?null:new Key(u.getTenantId(),u.getId(),cid);}
    static boolean isCareTurn(String message){return explicit(message)!=null;}
    private static boolean policyOrBusiness(String q){
        return ErSafety.has(q,"查询","查看","帮我查","查一下","核对余额","申请请假","我要请假","我想请假","办理","政策","规定","制度","条文","法规","原文","多少钱","多少天","有几天","工资条","证明","天气","新闻","换个话题","新话题","你能做什么","翻译","帮我写代码","搜索")
            ||q.matches(".*(可以|能不能|能否).*(请事假|请病假|申请调休).*" );
    }
    private static boolean noLeave(String q){
        if(policyOrBusiness(q)||ErSafety.has(q,"还没用完","没有用完","没休完","还没休完","不是没假","还有假","尚未用完","未用完"))return false;
        return q.matches(".*(没|没有|不剩|请不了|休不了).{0,4}(假期|年假|假|调休).*" )
            ||q.matches(".*(假期|年假|年休假|调休).{0,5}(用完|休完|没有了|没了|不够|不足|为零|是零).*" );
    }
    private static Stage explicit(String message){
        String q=ErSafety.normalize(message);
        if(!ErSafety.risk(message).equals("NONE"))return null;
        if(noLeave(q))return Stage.NO_LEAVE;
        if(!policyOrBusiness(q) && ErSafety.has(q,"不想麻烦别人","怕麻烦别人","不好意思开口"))return Stage.HESITANT;
        if(Set.of("我想休息","想休息","我需要休息","想歇一会","我想歇一会","我想休息一下","让我休息一下","先休息一下").contains(q))return Stage.REST;
        if(Set.of("先缓一缓","我想先缓一缓","先歇几分钟","短暂休息","先缓缓").contains(q))return Stage.PAUSE;
        if(Set.of("先了解休假安排","我想先了解如何安排休假休息","想了解请假休息").contains(q))return Stage.LEAVE;
        if(Set.of("联系心理支持","我想了解心理支持渠道","想找专业支持","找心理咨询","eap","心理咨询预约").contains(q))return Stage.SUPPORT;
        if(Set.of("我想联系hr沟通","我想找hr协助沟通","希望hr协助沟通").contains(q))return Stage.HR;
        if(Set.of("先不聊了","先不说了","不想说了","到这里吧","不用了谢谢").contains(q))return Stage.CLOSE;
        if(Set.of("我想整理一下具体发生的事","先整理事实").contains(q))return Stage.FACTS;
        if(ErSafety.has(q,"公司太垃圾","这公司真垃圾","领导故意针对我","主管故意针对我","公司故意整我","我要报复公司","帮我编造公司黑料"))return Stage.FACTS;
        return switch(WorkplaceSupport.intent(message)){
            case "EMOTION","BURNOUT","ADJUSTMENT" -> Stage.EMOTION;
            case "WORKLOAD" -> Stage.WORKLOAD;
            case "MANAGER" -> Stage.MANAGER;
            case "PUSHBACK" -> Stage.PUSHBACK;
            case "CONFLICT" -> Stage.CONFLICT;
            case "CRITICISM" -> Stage.CRITICISM;
            case "LISTEN" -> Stage.LISTEN;
            case "SLEEP" -> Stage.SLEEP;
            default -> null;
        };
    }
    Optional<Result> reply(UserAccount user,String message,String cid){
        var key=key(user,cid);long now=clock.millis();
        contexts.entrySet().removeIf(e->e.getValue().expires<=now);
        var previous=key==null?null:contexts.get(key);
        Stage stage=explicit(message);String q=ErSafety.normalize(message);
        boolean noLeave=stage==Stage.NO_LEAVE || previous!=null && previous.noLeave;
        if(ErSafety.has(q,"我还有假","我又有假了","原来还有假","刚确认还有年假")){noLeave=false;if(previous!=null)stage=Stage.LEAVE;}
        if(previous!=null && !policyOrBusiness(q) && ErSafety.risk(message).equals("NONE")){
            if(ErSafety.has(q,"不想麻烦","怕麻烦","不敢说","怕被觉得","怕影响","怕领导","怕主管","不好意思开口"))stage=Stage.HESITANT;
            if(Set.of("那怎么办","怎么办呢","还有别的办法吗","还能怎么办","那我怎么办","怎么缓缓","怎么休息").contains(q))stage=Stage.ALTERNATIVES;
        }
        if(previous!=null && Set.of(Stage.LISTEN,Stage.HEAR).contains(previous.stage) && stage!=null && Set.of(Stage.EMOTION,Stage.NO_LEAVE,Stage.HESITANT).contains(stage))stage=Stage.HEAR;
        if(stage==null && previous!=null && ErSafety.risk(message).equals("NONE")){
            stage=switch(q){
                case "工作量","任务多","事情太多了" -> Stage.WORKLOAD;
                case "人际关系","同事之间" -> Stage.CONFLICT;
                case "想休息","休息","歇会儿" -> Stage.REST;
                case "想请假休息","请假休息","休假休息" -> Stage.LEAVE;
                case "担心领导不同意","担心主管不同意","不敢开口","不知道怎么说" -> Stage.MANAGER;
                case "不同意","他不答应","还是不行","没用" -> Stage.PUSHBACK;
                case "只想说说","听我说","不想听建议","先听我说" -> Stage.LISTEN;
                case "不想说","不方便说","先不说" -> Stage.CLOSE;
                case "好一点了","好多了","缓过来了" -> Stage.BETTER;
                case "会告诉领导吗","会告诉主管吗","会保密吗","公司会知道吗" -> Stage.PRIVACY;
                case "谢谢","谢谢你" -> Stage.CLOSE;
                case "嗯","好","好的","可以" -> previous.stage==Stage.REST?Stage.PAUSE:Set.of(Stage.PLAN,Stage.HESITANT,Stage.ALTERNATIVES).contains(previous.stage)?Stage.MANAGER:Stage.LISTEN;
                default -> null;
            };
            // In an active care conversation, an unfamiliar reply is not a policy search.
            // Explicit factual/business questions still leave the care flow immediately.
            if(stage==null && !policyOrBusiness(q))
                stage=Set.of(Stage.LISTEN,Stage.HEAR).contains(previous.stage)?Stage.HEAR:previous.stage==Stage.WORKLOAD?Stage.TASK_PLAN:Set.of(Stage.TASK_PLAN,Stage.NEXT_STEP).contains(previous.stage)?Stage.NEXT_STEP:previous.stage==Stage.NEED?Stage.PLAN:Stage.NEED;
        }
        if(stage==null){if(key!=null)contexts.remove(key);return Optional.empty();}
        if(key!=null){
            if(stage==Stage.CLOSE)contexts.remove(key);
            else {if(contexts.size()>=2000)contexts.clear();contexts.put(key,new Context(stage,noLeave,now+15*60*1000L));}
        }
        String answer=switch(stage){
            case EMOTION -> "听起来你现在有些难受。我们先不急着把所有问题解决，可以先让自己缓一缓。\n\n你现在更需要休息一下，还是想说说发生了什么？";
            case REST -> "可以，先照顾好自己的状态。你想先歇几分钟缓一缓，还是想安排一段请假休息？我可以陪你把下一步理清。";
            case PAUSE -> "那就先给自己一点缓冲，找个舒服的位置坐一会儿，喝点水，不必马上做决定。如果正在值守或操作设备，先请人接替，确保安全。\n\n你可以先安静待一会儿；想继续时，再告诉我是工作量还是别的事情让你累。";
            case LEAVE -> "那我们慢慢想，不用现在就把所有安排定下来。你是想休息一两天，还是先给今天留一点喘息的时间？";
            case NO_LEAVE -> "想休息，却发现没有假可用了，确实会有些无奈。我们先不急着想请假的事。\n\n你现在是累得需要先缓一会儿，还是担心接下来一直没有休息的机会？";
            case ALTERNATIVES -> "先把目标放小一点：今天能不能找一个合适的空档缓一缓，或者和负责人商量调整一项不太急的任务？\n\n如果难在开口，我可以陪你想一句自然的说法。";
            case HESITANT -> "你也在顾虑别人的感受，开口确实不容易。表达自己需要一点支持，不一定要说很多，也不必一下子提出很大的调整。\n\n要不要先从一句“我最近有点累，想和您商量一下今天的安排”开始？";
            case WORKLOAD -> "任务堆在一起，确实容易让人喘不过气。先挑出最急的一件，写清截止时间和卡点，再请负责人一起确认优先级。\n\n可以这样说：“我想把这几项做好，目前时间有冲突，能否一起确认先做哪项、哪项可以调整？”\n\n现在最赶的是哪项任务？";
            case TASK_PLAN -> ErSafety.has(q,"平台","系统","网站","软件")
                ? "做平台要顾到的事情很多，觉得累很正常，不用逼自己一下子全部做完。\n\n今天先挑一条最重要的流程，把‘进入页面—完成操作—看到结果’走通；其他功能先记进待办。如果时间紧，可以把这版必须完成的内容和负责人对齐。\n\n先完成一个能演示的小版本，就已经是在往前走了。"
                : "这项任务压在心上，确实不容易。先别把整个任务都扛在这一刻。\n\n可以先写下这次必须交付的结果，再选一个半小时左右能推进的小步骤；暂时做不了的部分记下来，和负责人确认优先级或需要的支持。\n\n完成一小步后给自己一点缓冲，不必靠一直硬撑来证明自己。";
            case NEXT_STEP -> ErSafety.has(q,"时间","来不及","截止","明天","今天")
                ? "时间紧会让压力更明显。先列出‘这次必须完成’和‘可以后补’两部分，把当前进度、剩余工作和需要的支持告诉负责人，一起确认交付范围。\n\n可以这样开口：“我想把核心部分做好，目前时间比较紧，想和您确认这次优先交付哪些内容。”你不需要一个人默默扛下所有安排。"
                : ErSafety.has(q,"不会","卡住","技术","报错","不知道怎么做")
                ? "卡住不代表你做不好。先把问题缩到一个具体步骤，记下想达到的结果、现在出现的情况和已经试过的方法，再请熟悉这部分的同事一起看。\n\n先找到一个能继续往下走的办法，比要求自己立刻解决所有问题更有帮助。"
                : "那就先从最小的一步开始：写一句‘这一小步做完后，我能看到什么结果’，只处理和它直接相关的事情。遇到需要别人确认的地方先记下来，集中沟通。\n\n你已经在认真想办法了，不必一边做事一边责怪自己。需要缓一缓时，也可以先停几分钟。";
            case MANAGER -> "可以试着这样说：“我最近有些累，想和您商量一下工作安排。我会先说清当前进度和交接事项，看看有没有能调整的地方。”\n\n不用一次把话说得很完美，先让对方知道你的需要就好。";
            case PUSHBACK -> "没被理解，会让人更难受。不用急着再解释，我们先看看对方的顾虑是什么。\n\n他当时是怎么回应的？如果你不想独自沟通，也可以考虑请 HR 协助。";
            case CONFLICT -> "合作中的摩擦会让人很消耗。先从一件具体发生的事说起，区分事实和自己的感受，也给对方解释的机会。\n\n可以用“发生了什么、对工作有什么影响、希望怎样配合”来表达。最近哪一次沟通最让你在意？";
            case CRITICISM -> "被批评后难受是可以理解的，一次反馈不等于对你整个人的评价。等缓一缓，可以请对方说明具体需要改进的地方和完成标准。\n\n如果表达方式让你受到伤害，也可以提出希望私下、具体地沟通。你更在意事情本身，还是对方说话的方式？";
            case LISTEN -> "好，我们先不急着给建议。你只讲愿意讲的部分就可以，不需要把事情说得很完整。我在听。";
            case HEAR -> "我在听。你可以按自己的节奏继续说，也可以先停一会儿，不需要现在就找解决办法。";
            case SLEEP -> "睡不好还要应付工作，会更辛苦。先把今晚需要操心的事记下来，给自己留一点安静休息的时间，不要求马上解决所有问题。\n\n如果持续或明显影响日常生活，可以找专业人员进一步了解。你最近主要是难以入睡，还是经常醒来？";
            case FACTS -> "听得出你对这件事很不满。先把实际发生的事、带来的影响和希望得到的处理分开说，会更有助于解决问题；目前还不能据此判断公司或他人的动机。\n\n我们可以一起整理真实情况，选择沟通或正式反馈渠道。你最希望先解决的具体问题是什么？";
            case NEED -> "我们先不急着找答案。刚才说到的这件事，对你最大的影响是什么？你可以慢慢说。";
            case PLAN -> "那就先从你最希望调整的那一点谈起，不必一次解决所有问题。需要我陪你想想，下一步怎么开口沟通会更舒服一些吗？";
            case PRIVACY -> "普通关怀对话不会自动发给主管，也不会自动提交工单；聊天历史可能保存在当前设备。涉及紧急风险或需人工处理的情况，会按已有支持流程处理。\n\n如果联系 EAP，可以先问清服务方的保密范围，再决定分享哪些内容。";
            case SUPPORT -> "可以，专业支持也是一个选择。下方可以查看企业当前提供的 EAP 联系方式和公共求助渠道，再由你决定是否联系。保密范围和服务安排以提供方说明为准。";
            case HR -> "可以请 HR 帮忙协调。先准备一两件具体事实、对你的影响和希望获得的支持，按你愿意的范围说明情况即可。\n\n下方可查看联系与反馈渠道；目前还没有替你发送消息或创建工单。";
            case BETTER -> "缓过来一点就好，不必马上把节奏拉满。先挑一件能做到的小事；如果还需要休息或协助，也可以继续说。";
            case CLOSE -> "好，我们先聊到这里。你不需要勉强自己继续说，想聊的时候再来就好。";
        };
        if(noLeave && stage==Stage.LEAVE)answer="你刚才说没有假可用了，我们就先不绕回请假。可以先看看今天有没有短暂休息或调整任务的空间。你更想先缓一缓，还是聊聊怎么开口？";
        if(noLeave && stage==Stage.REST)answer="我记得，你说目前没有假可用了。先不急着安排长时间休息，我们看看今天能不能有一小段喘息的时间。你想先缓一缓，还是聊聊手头最累的那件事？";
        if(ErSafety.has(q,"编造","报复"))answer="我不能帮你编造材料或报复他人。我们可以把真实发生的事整理清楚，通过合适渠道表达诉求。\n\n"+answer;
        if(ErSafety.has(q,"抑郁","诊断","用药","吃什么药"))answer+="\n\n仅凭聊天不能判断是否患有心理疾病或决定用药，需要由有资质的专业人员评估。";
        List<ChatAction> actions=switch(stage){
            case EMOTION -> List.of(say("我想休息","我想休息"),say("先听我说","只想倾诉，不想听建议"));
            case REST -> List.of(say("先缓一缓","先缓一缓"),say("了解休假安排","先了解休假安排"));
            case LEAVE -> List.of(say("查假期余额","查询我的年假余额"),say("聊聊怎么开口","怎么和主管沟通休息安排"),say("开始请假申请","我要申请请假"));
            case NO_LEAVE -> List.of(say("先缓一缓","先缓一缓"),say("想想其他办法","还有别的办法吗"));
            case PUSHBACK -> List.of(say("希望 HR 协助","我想找HR协助沟通"));
            case SUPPORT -> List.of(panel("查看支持渠道","/employee-experience?section=support"));
            case HR -> List.of(panel("联系 HR","/directory"),panel("查看反馈渠道","/employee-experience?section=compliance"));
            case SLEEP,BETTER -> List.of(say("了解专业支持","我想了解心理支持渠道"),say("先休息一下","我想休息"));
            default -> List.of();
        };
        if(noLeave && Set.of(Stage.LEAVE,Stage.REST).contains(stage))actions=List.of(say("先缓一缓","先缓一缓"),say("聊聊怎么开口","怎么和主管沟通休息安排"));
        return Optional.of(new Result(answer,actions));
    }
    private static ChatAction say(String label,String text){return new ChatAction(label,"message",text);}
    private static ChatAction panel(String label,String path){return new ChatAction(label,"panel",path);}
}
