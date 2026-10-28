package com.haodaone.onboarding.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Daily 09:15 IST sweep over every company: reminds task owners about
 * overdue onboarding / offboarding tasks. Idempotent per day (see
 * OnboardingService#sendOverdueReminders).
 */
@Component
public class OnboardingReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OnboardingReminderScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OnboardingService onboardingService;

    public OnboardingReminderScheduler(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @Scheduled(cron = "0 15 9 * * *", zone = "Asia/Kolkata")
    public void remindOverdueTasks() {
        try {
            int reminded = onboardingService.sendOverdueReminders(LocalDate.now(IST));
            if (reminded > 0) log.info("Checklist reminder sweep covered {} overdue task(s)", reminded);
        } catch (RuntimeException ex) {
            log.error("Checklist reminder sweep failed", ex);
        }
    }
}
