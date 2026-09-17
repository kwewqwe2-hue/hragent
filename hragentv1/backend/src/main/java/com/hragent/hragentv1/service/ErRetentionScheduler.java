package com.hragent.hragentv1.service;
import com.hragent.hragentv1.repo.ErSignalRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

@Service
public class ErRetentionScheduler {
    private final ErSignalRepository signals;
    public ErRetentionScheduler(ErSignalRepository signals) { this.signals=signals; }
    @Scheduled(cron="0 15 2 * * *", zone="Asia/Shanghai")
    @Transactional
    public void clean() {
        signals.deleteByWeekBefore(EmployeeReminderService.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(7));
    }
}
