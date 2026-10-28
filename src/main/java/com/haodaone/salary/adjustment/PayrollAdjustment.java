package com.haodaone.salary.adjustment;

import java.math.BigDecimal;

/**
 * One extra earnings line a {@link PayrollAdjustmentProvider} wants paid in
 * a payroll period.
 *
 * @param code       short machine code, e.g. OVERTIME, LEAVE_ENCASHMENT, REIMBURSEMENT
 * @param label      what the payslip shows, e.g. "Overtime (12.5 h)"
 * @param amount     rupees, positive (a zero or negative amount is ignored by payroll)
 * @param taxable    whether it counts towards TDS / Form 16 taxable salary
 * @param sourceType the provider's record type, used to route {@link PayrollAdjustmentProvider#markSettled}
 * @param sourceId   id of the provider's record (overtime entry, encashment request, expense claim...)
 */
public record PayrollAdjustment(String code, String label, BigDecimal amount, boolean taxable,
                                String sourceType, Long sourceId) {
}
