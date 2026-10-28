package com.haodaone.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An approved, not-yet-reimbursed expense claim that payroll can pay out.
 * Reimbursements are not income, so {@code taxable} is always false - the
 * payroll line must be excluded from gross taxable pay, PF and ESI wages.
 */
public record ReimbursementPayable(
        Long claimId,
        Long employeeId,
        String title,
        BigDecimal amount,
        LocalDateTime approvedAt,
        boolean taxable) {
}
