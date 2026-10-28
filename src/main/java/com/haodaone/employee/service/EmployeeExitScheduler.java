package com.haodaone.employee.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Daily offboarding sweep: once a Resigned / Terminated employee's last
 * working date has passed, their login is disabled and refresh tokens are
 * revoked (see EmployeeService#disableLoginsPastLastWorkingDate). Scheduling
 * is enabled globally by RemoteDesktopSchedulingConfig (@EnableScheduling).
 */
@Component
public class EmployeeExitScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmployeeExitScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final EmployeeService employeeService;

    public EmployeeExitScheduler(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /** 00:10 IST every day - the day after the last working date. */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Kolkata")
    public void disableExitedLogins() {
        try {
            int disabled = employeeService.disableLoginsPastLastWorkingDate(LocalDate.now(IST));
            if (disabled > 0) {
                log.info("Offboarding sweep disabled {} login(s) past their last working date", disabled);
            }
        } catch (RuntimeException ex) {
            log.error("Offboarding sweep failed", ex);
        }
    }
}
