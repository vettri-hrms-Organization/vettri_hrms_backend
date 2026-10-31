package com.haodaone.salary.adjustment;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * Extension point for modules that recover money through payroll after tax (loan EMIs, salary advance
 * recovery...). Mirrors {@link PayrollAdjustmentProvider}:
 *  1. PayrollService.createRun calls {@link #deductionsFor} per included employee after statutory
 *     deductions are known. A line is attached only if the employee's net pay still covers it in full;
 *     otherwise it is left off (never a negative payslip) and will be offered again next run.
 *  2. PayrollService.process calls {@link #markSettled} with the attached lines of each processed item.
 */
public interface PayrollDeductionProvider {

    record PayrollDeduction(String code, String label, BigDecimal amount, String sourceType, Long sourceId) { }

    List<PayrollDeduction> deductionsFor(Long companyId, Long employeeId, YearMonth period);

    Set<String> sourceTypes();

    default void markSettled(List<PayrollDeduction> deductions, Long payrollItemId) {
    }

    /** Full & final settlement: everything a leaving employee still owes (e.g. a loan's whole outstanding). */
    default List<PayrollDeduction> settlementRecoveries(Long companyId, Long employeeId) {
        return List.of();
    }

    /** Called when a settlement that recovers these amounts is approved; settlementMarker = -settlementId. */
    default void markRecovered(List<PayrollDeduction> recoveries, Long settlementMarker) {
    }
}
