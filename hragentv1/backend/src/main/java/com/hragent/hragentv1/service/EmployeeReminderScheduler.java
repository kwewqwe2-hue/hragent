package com.hragent.hragentv1.service;

import com.hragent.hragentv1.repo.UserAccountRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableScheduling
public class EmployeeReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(EmployeeReminderScheduler.class);
    private final UserAccountRepository users;
    private final EmployeeReminderService reminders;
    public EmployeeReminderScheduler(UserAccountRepository users, EmployeeReminderService reminders) {
        this.users = users; this.reminders = reminders;
    }
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Shanghai")
    public void refresh() {
        users.findAll().forEach(user -> {
            try { reminders.mine(user); }
            catch (RuntimeException ex) { log.error("Unable to refresh reminders for employee {}", user.getId(), ex); }
        });
    }
}
