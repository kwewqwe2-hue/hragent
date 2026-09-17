package com.hragent.hragentv1.service;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
final class WorkJourney {
    private WorkJourney() {}
    static boolean onboarding(LocalDate entry,LocalDate today) { return entry!=null && !entry.isBefore(today.minusDays(29)); }
    static List<String[]> weekly(LocalDate today) {
        String prefix="weekly-"+today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))+"-";
        return List.of(
            new String[]{prefix+"priorities","本周","明确本周重点","列出本周最重要的三件事，确认优先级、交付结果和截止时间。","-1"},
            new String[]{prefix+"schedule","本周","安排工作与休息","为重点任务预留时间，检查会议冲突，为临时事项和休息留出空间。","-1"},
            new String[]{prefix+"sync","本周","同步进展与困难","向协作同事同步进度；遇到资源或时间冲突时，及时讨论取舍和支持。","-1"},
            new String[]{prefix+"admin","本周","处理待办事项","检查待审批、报销和资料归档等事项，仅处理本周实际需要办理的内容。","-1"},
            new String[]{prefix+"review","本周","完成一次周复盘","回顾本周完成的事情、遇到的障碍及下周第一步，也记录值得肯定的进步。","-1"});
    }
}
