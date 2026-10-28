package com.haodaone.salary.adjustment;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * Extension point for modules that add earnings to payroll (overtime, leave
 * encashment, expense reimbursement...). Register an implementation as a
 * Spring bean; PayrollService collects every bean of this type.
 *
 * Lifecycle:
 *  1. PayrollService.createRun calls {@link #adjustmentsFor} once per
 *     included employee (inside the run's transaction, tenant context set).
 *     Returned lines are stored as payroll_item_adjustment rows, added to
 *     gross (and to taxable gross when {@code taxable}), shown on the
 *     payslip, and included in the TDS projection.
 *  2. PayrollService.process calls {@link #markSettled} once per processed
 *     (non-held) item with the lines whose sourceType is in
 *     {@link #sourceTypes()}. Mark your records paid there so they are not
 *     returned again next month. Held items are not settled - their lines
 *     will be offered again by the next run.
 *  A cancelled DRAFT run never settles anything.
 *
 * Implementations should only return records that are approved and not yet
 * settled, for the given company/employee/period. Exceptions propagate and
 * abort run creation/processing (better than silently under-paying).
 */
public interface PayrollAdjustmentProvider {

    List<PayrollAdjustment> adjustmentsFor(Long companyId, Long employeeId, YearMonth period);

    /** Source types this provider emits; used to route {@link #markSettled} back to it. */
    Set<String> sourceTypes();

    /** Called when the run containing these lines is processed. Default: nothing to settle. */
    default void markSettled(List<PayrollAdjustment> adjustments, Long payrollItemId) {
    }
}
