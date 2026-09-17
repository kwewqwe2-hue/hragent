package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.LeaveBalanceRepository;
import com.hragent.hragentv1.repo.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import java.util.*;

/** Personal queries always use the authenticated employee and live business records. */
@Service
public class PersonalAssistantService {
    private final LeaveBalanceRepository balances;
    private final LeaveRequestRepository requests;
    private final EmploymentCertificateService certificates;
    public PersonalAssistantService(LeaveBalanceRepository balances, LeaveRequestRepository requests,
                                    EmploymentCertificateService certificates) {
        this.balances = balances; this.requests = requests; this.certificates = certificates;
    }
    public Optional<String> reply(UserAccount user, String message) {
        String text = message == null ? "" : message.replaceAll("\\s+", "");
        if (text.startsWith("【系统已完成网页附件解析")) return Optional.empty();
        boolean personal = has(text, "我", "本人", "余额", "剩余", "还剩", "还有");
        boolean rules = has(text, "制度", "政策", "规定", "工龄", "法定", "怎么算", "如何计算");
        if (personal && !rules && has(text, "年假", "年休假", "病假", "婚假", "事假", "假期", "调休")
                && has(text, "多少", "几天", "余额", "剩", "可用", "还能休", "能休", "查", "总额", "已用", "用掉")) {
            if (text.contains("调休")) return Optional.of("目前还没有接入你的调休余额，请 HR 帮你核对。");
            LeaveType type = Arrays.stream(LeaveType.values()).filter(t -> text.contains(t.getLabel())
                    || (t == LeaveType.ANNUAL && text.contains("年休假"))).findFirst().orElse(null);
            var values = balances.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(user.getTenantId(), user.getId())
                    .stream().filter(b -> type == null || b.getLeaveType() == type).toList();
            if (values.isEmpty()) return Optional.of("还没查到你的" + (type == null ? "假期" : type.getLabel()) + "额度，请 HR 核对后补充。");
            boolean used = has(text, "已用", "用掉", "用了");
            boolean total = has(text, "总额", "一共", "总共");
            return Optional.of(String.join("\n", values.stream().map(b -> "你的" + b.getLeaveType().getLabel()
                    + (used ? "已用 " : total ? "总额度是 " : "还剩 ")
                    + (used ? b.getUsedDays() : total ? b.getTotalDays() : b.remainingDays()).stripTrailingZeros().toPlainString() + " 天。") .toList()));
        }
        if (!rules && has(text, "请假", "休假", "年假申请") && has(text, "进度", "状态", "结果", "记录", "通过", "批了吗", "批了没", "批到哪")) {
            var mine = requests.findByTenantIdAndEmployeeIdOrderBySubmittedAtDesc(user.getTenantId(), user.getId());
            if (mine.isEmpty()) return Optional.of("你目前没有请假记录。可以到工作台「我的请假」提交申请。");
            return Optional.of("你的最近请假记录：\n" + String.join("\n", mine.stream().limit(5).map(r -> "- "
                    + r.getLeaveType().getLabel() + " · " + r.getStartDate() + " 至 " + r.getEndDate()
                    + " · " + r.getStatus().getLabel()).toList()));
        }
        if (!rules && has(text, "证明") && has(text, "进度", "状态", "结果", "我的申请", "下载", "好了没")) {
            var mine = certificates.mine(user);
            return Optional.of(mine.isEmpty() ? "你目前没有证明申请。可以到工作台「在职证明」提交。"
                    : "你的最近证明申请：\n" + String.join("\n", mine.stream().limit(5).map(c -> "- "
                    + c.certificateTypeLabel() + " · " + c.statusLabel()).toList()) + "\n已生成的文件可在工作台「在职证明」下载。");
        }
        if (personal && !rules && has(text, "个人信息", "个人资料", "员工信息", "工号", "部门", "岗位", "入职时间", "入职日期", "什么时候入职")) {
            var rows = new ArrayList<String>();
            boolean all = has(text, "个人信息", "个人资料", "员工信息");
            if (all || text.contains("工号")) rows.add("工号：" + display(user.getEmployeeNo()));
            if (all || text.contains("部门")) rows.add("部门：" + display(user.getDepartment()));
            if (all || text.contains("岗位")) rows.add("岗位：" + display(user.getTitle()));
            if (all || text.contains("入职")) rows.add("入职日期：" + display(user.getEntryDate()));
            return Optional.of(String.join("\n", rows));
        }
        return Optional.empty();
    }
    private static String display(Object value) { return value == null || value.toString().isBlank() ? "暂未维护" : value.toString(); }
    private static boolean has(String text, String... terms) { return Arrays.stream(terms).anyMatch(text::contains); }
}
