package com.haodaone.loan;

import com.haodaone.salary.adjustment.PayrollAdjustment;
import com.haodaone.salary.adjustment.PayrollAdjustmentProvider;
import com.haodaone.salary.adjustment.PayrollDeductionProvider;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/** Connects loans to payroll: EMIs are post-tax deductions, pay-outs through payroll are non-taxable earnings. */
final class LoanPayrollHooks {
    private LoanPayrollHooks() { }

    @Component
    static class Emis implements PayrollDeductionProvider {
        private final LoanService loans;

        Emis(LoanService loans) {
            this.loans = loans;
        }

        @Override
        public List<PayrollDeduction> deductionsFor(Long companyId, Long employeeId, YearMonth period) {
            return loans.emisDue(companyId, employeeId, period);
        }

        @Override
        public Set<String> sourceTypes() {
            return Set.of(LoanService.SOURCE_EMI, LoanService.SOURCE_OUTSTANDING);
        }

        @Override
        public void markSettled(List<PayrollDeduction> deductions, Long payrollItemId) {
            loans.settleEmis(deductions, payrollItemId);
        }

        @Override
        public List<PayrollDeduction> settlementRecoveries(Long companyId, Long employeeId) {
            return loans.outstandingForSettlement(companyId, employeeId);
        }

        @Override
        public void markRecovered(List<PayrollDeduction> recoveries, Long settlementMarker) {
            loans.recoverInSettlement(recoveries, settlementMarker);
        }
    }

    @Component
    static class Payouts implements PayrollAdjustmentProvider {
        private final LoanService loans;

        Payouts(LoanService loans) {
            this.loans = loans;
        }

        @Override
        public List<PayrollAdjustment> adjustmentsFor(Long companyId, Long employeeId, YearMonth period) {
            return loans.payoutsDue(companyId, employeeId, period);
        }

        @Override
        public Set<String> sourceTypes() {
            return Set.of(LoanService.SOURCE_DISBURSAL);
        }

        @Override
        public void markSettled(List<PayrollAdjustment> adjustments, Long payrollItemId) {
            loans.settlePayouts(adjustments, payrollItemId);
        }
    }
}
