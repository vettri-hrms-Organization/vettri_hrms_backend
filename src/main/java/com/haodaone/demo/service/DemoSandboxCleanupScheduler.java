package com.haodaone.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every hour: removes customer demo workspaces whose demo period has ended (see DemoSandboxService.cleanupExpired). */
@Component
public class DemoSandboxCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DemoSandboxCleanupScheduler.class);

    private final DemoSandboxService sandboxes;

    public DemoSandboxCleanupScheduler(DemoSandboxService sandboxes) {
        this.sandboxes = sandboxes;
    }

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Kolkata")
    public void cleanUpExpiredDemos() {
        int cleaned = sandboxes.cleanupExpired();
        if (cleaned > 0) log.info("Cleaned up {} expired demo workspace(s)", cleaned);
    }
}
