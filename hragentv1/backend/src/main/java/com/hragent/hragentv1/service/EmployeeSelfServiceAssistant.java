package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.LeaveBalanceRepository;
import org.springframework.stereotype.Service;
import java.util.*;

/** Read-only business intents are resolved with the authenticated employee, before remote orchestration. */
@Service
public class EmployeeSelfServiceAssistant {
    private final PolicyCopilotService policies;
    private final EmployeeReminderService reminders;
    private final LeaveBalanceRepository balances;
    private final EmploymentCertificateService certificates;
    public EmployeeSelfServiceAssistant(PolicyCopilotService policies, EmployeeReminderService reminders,
            LeaveBalanceRepository balances, EmploymentCertificateService certificates) {
        this.policies = policies; this.reminders = reminders; this.balances = balances; this.certificates = certificates;
    }
    public Optional<String> quickReply(UserAccount user, String message) {
        String text = message == null ? "" : message.trim();
        if (text.startsWith("【系统已完成网页附件解析")) return Optional.empty();
        if (has(text, "提醒", "待办", "人事事项") && !has(text, "政策", "制度", "规定", "审批权限")) {
            var open = reminders.mine(user).stream().filter(t -> "OPEN".equals(t.getStatus()))
                    .filter(t -> t.getSnoozedUntil() == null || !t.getSnoozedUntil().isAfter(EmployeeReminderService.today())).toList();
            return Optional.of(open.isEmpty() ? "目前系统里没有需要你处理的提醒。你也可以继续问我‘请假申请到哪一步了’或‘我还有多少年假’。"
                    : "你目前需要关注的提醒：\n\n" + String.join("\n\n", open.stream().map(t -> "- **" + t.getTitle()
                            + "** · " + t.getDueDate() + "\n" + t.getDescription()).toList()));
        }
        if (has(text, "入职第一天", "入职当天") && has(text, "事项", "做什么", "流程", "完成", "安排")) {
            return Optional.of("入职第一天，建议先完成这几件事：\n\n"
                    + "1. 和报到联系人确认办公地点、报到时间及待补材料。\n"
                    + "2. 领取设备，检查账号能否登录；权限问题联系 IT。\n"
                    + "3. 找到直属主管或导师，确认今天的安排和可以请教的同事。\n"
                    + "4. 确认打卡、请假和协作工具的入口。\n\n"
                    + "可以在[入职与成长导航](http://localhost:5173/employee-experience?section=onboarding)查看任务并记录进度。你现在卡在哪一步？");
        }
        if (has(text, "请假前", "申请请假前") && has(text, "检查", "确认", "材料", "信息", "准备")) {
            var values = balances.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(user.getTenantId(), user.getId());
            String balance = values.isEmpty() ? "目前还没查到你的假期额度，请 HR 核对。"
                    : "你当前的可用额度：" + String.join("、", values.stream().map(b -> b.getLeaveType().getLabel() + " "
                            + b.remainingDays().stripTrailingZeros().toPlainString() + " 天").toList()) + "。";
            return Optional.of("请假前先确认：\n\n"
                    + "1. 请什么假、起止日期及天数。\n2. 额度是否够用，工作由谁接手。\n"
                    + "3. 申请表要求哪些材料，谁负责审批；材料要求按假别确认。\n\n"
                    + balance + "\n\n到[我的请假](http://localhost:5173/my-leave)提交。你打算请哪种假、休多久？");
        }
        if (text.equals("请用简洁、清晰的方式帮我理解公司制度中最需要注意的内容。"))
            return PolicyCatalog.reply("有哪些政策知识");
        return Optional.empty();
    }
    public Optional<String> reply(UserAccount user, String message) {
        String text = message == null ? "" : message.trim();
        if (text.startsWith("【系统已完成网页附件解析")) return Optional.empty();
        var quick = quickReply(user, text);
        if (quick.isPresent()) return quick;
        var catalog=PolicyCatalog.reply(text);
        if (catalog.isPresent()) return catalog;
        if (text.matches("(?i)^(你好|您好|嗨|hello|hi|在吗)[！!？?。,.，]*$")
                || has(text, "能做什么", "服务范围", "能问什么")) {
            return Optional.of("你好，" + user.getName() + "！想查年假、办证明，还是了解公司制度？直接告诉我就好。");
        }
        if (has(text, "年假", "假期", "调休") && (has(text, "余额", "剩") || (has(text, "我") && has(text, "几天", "多少天"))) && !has(text, "制度", "规定", "工龄", "累计")) {
            var values = balances.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(user.getTenantId(), user.getId());
            return Optional.of(values.isEmpty() ? "尚未维护你的假期余额，请 HR 核对额度。"
                    : "你目前可用的假期：\n\n"
                    + String.join("\n", values.stream().map(b -> "- " + b.getLeaveType().getLabel() + "："
                            + b.remainingDays().stripTrailingZeros().toPlainString() + " 天").toList()));
        }
        if (has(text, "提醒", "待办", "到期", "失效", "人事事项") && !has(text, "政策", "制度", "规定")) {
            var open = reminders.mine(user).stream().filter(t -> "OPEN".equals(t.getStatus()))
                    .filter(t -> t.getSnoozedUntil() == null || !t.getSnoozedUntil().isAfter(EmployeeReminderService.today())).toList();
            return Optional.of(open.isEmpty() ? "你目前没有到期待办。"
                    : "你的待办提醒：\n\n" + String.join("\n\n", open.stream().map(t -> "- **" + t.getTitle()
                            + "** · " + t.getDueDate() + "\n" + t.getDescription()).toList())
                    );
        }
        if (has(text, "证明")) {
            if (has(text, "进度", "下载", "状态", "我的申请")) {
                var mine = certificates.mine(user);
                return Optional.of(mine.isEmpty() ? "你还没有证明申请。到工作台的「在职证明」填写用途后就能提交。"
                        : "你的证明申请：\n\n" + String.join("\n", mine.stream().limit(5).map(c -> "- #" + c.id()
                                + " " + c.certificateTypeLabel() + " · " + c.statusLabel()).toList())
                        + "\n\n已生成的文件可在工作台「在职证明」下载。");
            }
            if (has(text, "申请", "开具", "开一", "办理", "我要", "怎么", "如何"))
                return Optional.of("到工作台的「在职证明」填写用途并提交，HR 核对后就能下载。需要盖章的话，再联系 HR 办理。");
        }
        if (has(text, "社保", "公积金", "落户") && has(text, "转移", "材料", "清单", "办理", "怎么", "如何"))
            return Optional.of(policies.answer(user, text).answer());
        if (has(text, "申请", "提交", "撤销", "取消", "审批", "进度", "入职")
                && !has(text, "制度", "政策", "规定", "标准", "条件", "怎么", "如何", "材料")) return Optional.empty();
        if (has(text, "制度", "政策", "考勤", "打卡", "年假", "加班", "调休", "差旅", "出差", "报销", "福利",
                "社保", "公积金", "落户", "产假", "生育假", "生育津贴", "流产", "病假", "婚假", "合同", "转正", "体检", "工时", "劳动法", "赔偿", "补偿", "工资", "薪资", "退休"))
            return Optional.of(policies.answer(user, text).answer());
        return Optional.empty();
    }
    private boolean has(String value, String... terms) { return Arrays.stream(terms).anyMatch(value::contains); }
}
