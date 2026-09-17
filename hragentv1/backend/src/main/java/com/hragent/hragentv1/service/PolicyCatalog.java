package com.hragent.hragentv1.service;

import java.util.Optional;
import java.util.regex.Pattern;

/** Navigation answers describe consultation topics, never personal entitlements. */
public final class PolicyCatalog {
    private PolicyCatalog() { }
    private static final Pattern SPECIFIC = Pattern.compile("年假|年休假|产假|婚假|病假|请假|休假|考勤|打卡|加班|调休|工资|薪资|薪酬|社保|公积金|报销|差旅|出差|福利|费用|离职|入职|合同|证明|人事办理|转正|落户|工伤|竞业|关怀|合规|劳动关系|申诉|退休|生育|育儿|医疗期|适用|生效|失效|更新|最新|过期|冲突|违反");
    private static final String ANSWER = "可以咨询这几类政策：\n\n"
            + "- **休假与考勤**：年假、产假、婚假、加班和调休。\n"
            + "- **薪酬与社保**：工资、生育津贴、社保和公积金。\n"
            + "- **福利与费用**：员工福利、差旅标准和报销要求。\n"
            + "- **入职与劳动合同**：试用期、转正、续签和离职。\n"
            + "- **证明与人事办理**：在职证明、收入证明和办理材料。\n"
            + "- **员工关怀与合规**：支持资源、劳动关系和申诉渠道。\n\n"
            + "你想先了解哪一类？也可以直接问“产假有多少天”“出差住宿标准是什么”。想查个人信息，可以问“我还有多少年假”。";
    public static Optional<String> reply(String message) {
        if (message == null || message.startsWith("【系统已完成网页附件解析")) return Optional.empty();
        String text = message.replaceAll("\\s+", "");
        if (SPECIFIC.matcher(text).find()) return Optional.empty();
        boolean catalog = text.matches(".*(哪些|什么|种类|类别|分类|类型|范围|目录|清单|介绍|概览).*(政策|制度|知识).*" )
                || text.matches(".*(政策|制度|知识).*(哪些|什么|种类|类别|分类|类型|范围|目录|清单|介绍|概览).*" )
                || text.matches(".*(能问什么|能咨询什么|能回答什么|能做什么|服务范围).*" );
        return catalog ? Optional.of(ANSWER) : Optional.empty();
    }
}
