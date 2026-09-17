package com.hragent.hragentv1.service;

import java.util.*;
import java.util.regex.Pattern;

/** Deterministic triage before retrieval, models and workflow tools. Never a clinical diagnosis. */
public final class ErSafety {
    private ErSafety() { }
    public static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}\\p{Cf}]", "");
    }
    public static boolean has(String text, String... words) { return Arrays.stream(words).anyMatch(text::contains); }
    public static String risk(String message) {
        String text = normalize(message);
        if (has(text, "跳楼", "自残", "自杀", "不想活", "结束生命", "伤害自己", "活不下去", "轻生",
                "吞药", "割腕", "killmyself", "suicide", "selfharm", "深度抑郁", "重度抑郁", "撑不下去")) return "URGENT";
        if (has(text, "劳动仲裁", "集体维权", "被辞退无赔偿", "辞退不给赔偿", "开除不给赔偿",
                "性骚扰", "职场霸凌", "被霸凌", "被骚扰", "崩溃", "举报", "投诉", "申诉")) return "HUMAN";
        return "NONE";
    }
    public static boolean informational(String message) {
        String text = normalize(message);
        return !has(text,"我想死","我要跳楼","不想活","伤害自己","我被","对我","我要举报","我要投诉")
                && Pattern.compile("是什么|什么意思|如何预防|预防.*|科普|热线是多少|申诉入口|如何申诉|怎么举报|词库|机制|流程是什么|(?:申诉|举报|投诉)(?:的)?(?:期限|时限|材料|程序|流程|渠道|规定)").matcher(text).find();
    }
    public static boolean sensitive(String message) {
        return !risk(message).equals("NONE") || has(normalize(message),"焦虑","抑郁","压力","倦怠","失眠","心理","eap","离职访谈");
    }
    public static String topic(String message) {
        String text = normalize(message);
        if (has(text,"离职","辞职","离职补偿","公积金提取")) return "离职与交接";
        if (has(text,"加班","调休")) return "加班与调休";
        if (has(text,"考勤","打卡")) return "考勤制度";
        if (has(text,"差旅","出差","报销")) return "差旅与报销";
        if (has(text,"年假","休假","病假","产假")) return "休假政策";
        if (has(text,"证明")) return "证明办理";
        if (has(text,"竞业","工伤","调岗","调薪","劳动法")) return "劳动关系咨询";
        if (has(text,"入职","新人","黑话","权限")) return "入职与协作";
        if (has(text,"社保","公积金","落户")) return "城市办事";
        return "其他制度问题";
    }
}
