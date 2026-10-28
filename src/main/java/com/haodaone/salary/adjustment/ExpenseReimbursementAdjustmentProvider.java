package com.haodaone.salary.adjustment;

import com.haodaone.expense.service.ExpenseClaimService;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * Pays approved expense claims through payroll when the company's expense setting is
 * "reimburse through payroll" (ExpenseClaimService returns nothing otherwise).
 * Reimbursements are not salary: non-taxable, and kept out of PF/ESI wages.
 */
@Component
public class ExpenseReimbursementAdjustmentProvider implements PayrollAdjustmentProvider {

    static final String SOURCE_TYPE = "EXPENSE_CLAIM";

    private final ExpenseClaimService expenseClaimService;

    public ExpenseReimbursementAdjustmentProvider(ExpenseClaimService expenseClaimService) {
        this.expenseClaimService = expenseClaimService;
    }

    @Override
    public List<PayrollAdjustment> adjustmentsFor(Long companyId, Long employeeId, YearMonth period) {
        return expenseClaimService.approvedUnreimbursed(companyId, employeeId, period).stream()
                .filter(payable -> payable.amount() != null && payable.amount().signum() > 0)
                .map(payable -> new PayrollAdjustment("REIMBURSEMENT", "Reimbursement - " + payable.title(),
                        payable.amount(), false, SOURCE_TYPE, payable.claimId()))
                .toList();
    }

    @Override
    public Set<String> sourceTypes() {
        return Set.of(SOURCE_TYPE);
    }

    @Override
    public void markSettled(List<PayrollAdjustment> adjustments, Long payrollItemId) {
        expenseClaimService.markPaid(adjustments.stream().map(PayrollAdjustment::sourceId).toList(), payrollItemId);
    }
}
