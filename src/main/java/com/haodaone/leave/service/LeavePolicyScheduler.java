package com.haodaone.leave.service;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Leave-policy jobs (all idempotent, run per company with the tenant set):
 * <ul>
 *   <li>Jan 1, 00:30 IST - year-end for the previous year: writes the new year's carriedForwardDays.</li>
 *   <li>1st of every month, 01:15 IST - materialises accrued-to-date allocations into LeaveBalance rows
 *       (audit/visibility only; balances are always computed live by LeaveBalanceService).</li>
 * </ul>
 */
@Component
public class LeavePolicyScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeavePolicyScheduler.class);

    private final CompanyRepository companyRepository;
    private final LeaveBalanceService leaveBalanceService;

    public LeavePolicyScheduler(CompanyRepository companyRepository, LeaveBalanceService leaveBalanceService) {
        this.companyRepository = companyRepository;
        this.leaveBalanceService = leaveBalanceService;
    }

    @Scheduled(cron = "0 30 0 1 1 *", zone = "Asia/Kolkata")
    public void yearEnd() {
        int closingYear = LeaveBalanceService.today().getYear() - 1;
        forEachCompany("year-end " + closingYear, companyId -> leaveBalanceService.runYearEnd(companyId, closingYear));
    }

    @Scheduled(cron = "0 15 1 1 * *", zone = "Asia/Kolkata")
    public void monthlyAccrual() {
        LocalDate today = LeaveBalanceService.today();
        forEachCompany("accrual " + today, companyId -> leaveBalanceService.materialiseAccruals(companyId, today));
    }

    private void forEachCompany(String jobName, java.util.function.Consumer<Long> job) {
        for (Company company : companyRepository.findAll()) {
            if (company.isDeleted()) continue;
            Long previous = TenantContext.getCurrentTenant();
            try {
                TenantContext.setCurrentTenant(company.getId());
                job.accept(company.getId());
            } catch (RuntimeException ex) {
                log.error("Leave policy job '{}' failed for company {}", jobName, company.getId(), ex);
            } finally {
                if (previous != null) TenantContext.setCurrentTenant(previous); else TenantContext.clear();
            }
        }
    }
}
