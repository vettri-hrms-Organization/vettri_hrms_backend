package com.haodaone.attendance.shift.service;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Nightly overtime generation: evaluates yesterday (and the day before, to catch late-synced
 * biometric punches and overnight shifts) for every company and creates PENDING overtime entries.
 * Idempotent - see {@link OvertimeService#generateForDate}.
 */
@Component
public class OvertimeScheduler {

    private static final Logger log = LoggerFactory.getLogger(OvertimeScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OvertimeService overtimeService;
    private final CompanyRepository companyRepository;

    public OvertimeScheduler(OvertimeService overtimeService, CompanyRepository companyRepository) {
        this.overtimeService = overtimeService;
        this.companyRepository = companyRepository;
    }

    /** 02:30 IST daily. */
    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Kolkata")
    public void generateOvertime() {
        LocalDate today = LocalDate.now(IST);
        for (Company company : companyRepository.findAll()) {
            if (company.isDeleted()) continue;
            for (int back = 2; back >= 1; back--) {
                LocalDate date = today.minusDays(back);
                try {
                    int created = overtimeService.generateForDate(company.getId(), date);
                    if (created > 0) {
                        log.info("Generated {} overtime entr(ies) for company {} on {}", created, company.getId(), date);
                    }
                } catch (RuntimeException ex) {
                    log.error("Overtime generation failed for company {} on {}", company.getId(), date, ex);
                }
            }
        }
    }
}
