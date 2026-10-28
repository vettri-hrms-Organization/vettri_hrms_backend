package com.haodaone.attendance.service;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Daily privacy purge: deletes check-in selfies older than each company's retention
 * window (default 90 days) from S3 and clears the session's key. Idempotent; processes
 * up to 500 sessions per company per batch and loops until nothing is left.
 */
@Component
public class AttendanceSelfieRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSelfieRetentionJob.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_BATCHES_PER_COMPANY = 20;

    private final AttendanceSelfieService selfieService;
    private final CompanyRepository companyRepository;

    public AttendanceSelfieRetentionJob(AttendanceSelfieService selfieService, CompanyRepository companyRepository) {
        this.selfieService = selfieService;
        this.companyRepository = companyRepository;
    }

    /** 03:15 IST daily. */
    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Kolkata")
    public void purgeExpiredSelfies() {
        LocalDate today = LocalDate.now(IST);
        for (Company company : companyRepository.findAll()) {
            try {
                for (int batch = 0; batch < MAX_BATCHES_PER_COMPANY; batch++) {
                    if (selfieService.purgeExpired(company, today) == 0) break;
                }
            } catch (RuntimeException ex) {
                log.error("Selfie retention purge failed for company {}", company.getId(), ex);
            }
        }
    }
}
