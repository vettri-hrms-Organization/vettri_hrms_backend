package com.haodaone.billing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily subscription lifecycle sweep (all companies): renewal invoices at
 * T-7, reminders T-7/T-3/T-1, trial expiry, PAST_DUE on the renewal date,
 * SUSPENDED after the grace window, prorated seat add-ons. Idempotent, so a
 * re-run (or a manual run from platform admin) on the same day is harmless.
 */
@Component
public class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);

    private final BillingLifecycleService lifecycle;

    public BillingScheduler(BillingLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    /** 06:15 IST every day. */
    @Scheduled(cron = "0 15 6 * * *", zone = "Asia/Kolkata")
    public void runDailyLifecycle() {
        try {
            int processed = lifecycle.runDaily(BillingLifecycleService.today());
            log.info("Billing lifecycle processed {} subscription(s)", processed);
        } catch (RuntimeException ex) {
            log.error("Billing lifecycle sweep failed", ex);
        }
    }
}
