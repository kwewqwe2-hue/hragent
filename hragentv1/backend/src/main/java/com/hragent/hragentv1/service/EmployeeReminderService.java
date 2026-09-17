package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class EmployeeReminderService {
    private final EmployeeServiceTaskRepository tasks;
    private final EmployeeServiceProfileService profiles;
    private final LeaveBalanceRepository balances;
    private final AuditService audit;
    private final LifecycleRequestRepository lifecycle;
    public EmployeeReminderService(EmployeeServiceTaskRepository tasks, EmployeeServiceProfileService profiles,
            LeaveBalanceRepository balances, AuditService audit, LifecycleRequestRepository lifecycle) {
        this.tasks = tasks; this.profiles = profiles; this.balances = balances; this.audit = audit;
        this.lifecycle = lifecycle;
    }
    // Serialized reconciliation plus a database unique key keeps polling and the scheduler idempotent.
    public synchronized List<EmployeeServiceTask> mine(UserAccount employee) {
        var profile = profiles.profile(employee);
        var current = tasks.findByTenantIdAndEmployeeIdOrderByDueDateAsc(employee.getTenantId(), employee.getId());
        var expected = new HashSet<String>();
        if (employee.isActive() && employee.getRole() != Role.NEW_HIRE
                && (employee.getEmployeeStatus() == null || employee.getEmployeeStatus() == EmployeeStatus.ACTIVE)) {
            for (var request : lifecycle.findByTenantIdAndEmployeeIdOrderByUpdatedAtDesc(employee.getTenantId(), employee.getId())) {
                if ("EXIT".equals(request.kind) && "APPROVED".equals(request.status)) {
                    reconcile(employee, current, expected, "EXIT_HANDOVER-" + request.id, "离职交接准备", request.dueDate, 30,
                            "服务单 #" + request.id + " 已审核，按约定日期核对工作、设备、资料和结算交接。具体安排以 HR 回复为准。", "/employee-experience?section=onboarding");
                }
            }
            reconcile(employee, current, expected, "PROBATION", "转正答辩准备", profile.probationEndDate(), 30,
                    "核对答辩时间、试用期总结和主管要求；办理结果由 HR 确认。", null);
            reconcile(employee, current, expected, "CONTRACT", "劳动合同续签", profile.contractEndDate(), 60,
                    "合同即将到期，请联系 HR 核对续签意向和签署安排。", null);
            reconcile(employee, current, expected, "MEDICAL", "体检预约", profile.medicalCheckDeadline(), 30,
                    "在截止日前完成预约；预约入口由 HR 维护。", profile.medicalBookingUrl());
            var annual = balances.findByTenantIdAndEmployeeIdOrderByLeaveTypeAsc(employee.getTenantId(), employee.getId())
                    .stream().filter(b -> b.getLeaveType() == LeaveType.ANNUAL).findFirst();
            if (annual.isPresent() && annual.get().remainingDays().signum() > 0) {
                reconcile(employee, current, expected, "ANNUAL_LEAVE", "年假到期预警", profile.annualLeaveExpiresAt(), 60,
                        "当前年假余额 " + annual.get().remainingDays() + " 天；到期日由 HR 维护，请安排休假或核对结转规则。", "/my-leave");
            }
        }
        for (var task : current) {
            if (!expected.contains(task.getEventKey()) && !"OBSOLETE".equals(task.getStatus())) {
                task.setStatus("OBSOLETE"); tasks.save(task);
            }
        }
        return current.stream().filter(t -> !"OBSOLETE".equals(t.getStatus()))
                .sorted(Comparator.comparing(EmployeeServiceTask::getDueDate)).toList();
    }
    private void reconcile(UserAccount employee, List<EmployeeServiceTask> current, Set<String> expected,
            String kind, String title, LocalDate due, int leadDays, String description, String actionUrl) {
        LocalDate today = today();
        if (due == null || due.isAfter(today.plusDays(leadDays))) return;
        String key = kind + ":" + due;
        expected.add(key);
        var task = current.stream().filter(t -> key.equals(t.getEventKey())).findFirst().orElse(null);
        if (task == null) {
            task = new EmployeeServiceTask(); task.setTenantId(employee.getTenantId()); task.setEmployeeId(employee.getId());
            task.setEventKey(key); task.setKind(kind); task.setDueDate(due); task.setTitle(title);
            current.add(task);
        }
        if ("OBSOLETE".equals(task.getStatus())) task.setStatus("OPEN");
        task.setDescription(description); task.setActionUrl(actionUrl);
        tasks.save(task);
    }
    public synchronized EmployeeServiceTask update(UserAccount employee, Long taskId, TaskAction input) {
        var task = tasks.findByIdAndTenantIdAndEmployeeId(taskId, employee.getTenantId(), employee.getId())
                .orElseThrow(() -> AppException.notFound("提醒不存在"));
        if ("OBSOLETE".equals(task.getStatus())) throw AppException.badRequest("事件日期已变化，请刷新提醒");
        if ("SNOOZE".equals(input.action())) {
            if (input.until() == null || !input.until().isAfter(today()) || input.until().isAfter(today().plusDays(30)))
                throw AppException.badRequest("稍后提醒日期应为未来 30 天内");
            task.setStatus("OPEN"); task.setSnoozedUntil(input.until());
        } else {
            task.setStatus(input.action()); task.setSnoozedUntil(null);
        }
        audit.log(employee, "UPDATE_SERVICE_REMINDER", "service_task", taskId, input.action());
        return tasks.save(task);
    }
    public String calendar(UserAccount employee) {
        StringBuilder result = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//HRAgent//Employee Services//CN\r\n");
        for (var task : mine(employee)) {
            if (!"OPEN".equals(task.getStatus())) continue;
            result.append("BEGIN:VEVENT\r\nUID:hragent-").append(task.getId()).append("@local\r\nDTSTAMP:")
                    .append(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()))
                    .append("\r\nDTSTART;VALUE=DATE:").append(task.getDueDate().format(DateTimeFormatter.BASIC_ISO_DATE))
                    .append("\r\nSUMMARY:").append(escape(task.getTitle()))
                    .append("\r\nBEGIN:VALARM\r\nTRIGGER:-P1D\r\nACTION:DISPLAY\r\nDESCRIPTION:员工服务提醒")
                    .append("\r\nEND:VALARM\r\nEND:VEVENT\r\n");
        }
        return result.append("END:VCALENDAR\r\n").toString();
    }
    private String escape(String value) { return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n").replace("\r", ""); }
    public static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }
}
