package com.haodaone.salary.revision;

import com.haodaone.salary.adjustment.PayrollAdjustment;
import com.haodaone.salary.adjustment.PayrollAdjustmentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/** Wires salary revisions into payroll (arrears as a taxable earning) and applies future-dated revisions daily. */
final class SalaryRevisionHooks {
    private SalaryRevisionHooks() { }

    @Component
    static class Arrears implements PayrollAdjustmentProvider {
        private final SalaryRevisionService service;

        Arrears(SalaryRevisionService service) {
            this.service = service;
        }

        @Override
        public List<PayrollAdjustment> adjustmentsFor(Long companyId, Long employeeId, YearMonth period) {
            return service.arrearsDue(companyId, employeeId, period);
        }

        @Override
        public Set<String> sourceTypes() {
            return Set.of(SalaryRevisionService.SOURCE_ARREARS);
        }

        @Override
        public void markSettled(List<PayrollAdjustment> adjustments, Long payrollItemId) {
            service.arrearsPaid(adjustments, payrollItemId);
        }
    }

    @Component
    static class Scheduler {
        private static final Logger log = LoggerFactory.getLogger(Scheduler.class);
        private final SalaryRevisionService service;

        Scheduler(SalaryRevisionService service) {
            this.service = service;
        }

        /** 00:20 IST: future-dated revisions become the active salary on their effective date. */
        @Scheduled(cron = "0 20 0 * * *", zone = "Asia/Kolkata")
        public void applyScheduledRevisions() {
            try {
                int applied = service.applyDue(LocalDate.now(ZoneId.of("Asia/Kolkata")));
                if (applied > 0) log.info("Applied {} scheduled salary revision(s)", applied);
            } catch (RuntimeException ex) {
                log.error("Applying scheduled salary revisions failed", ex);
            }
        }
    }
}
