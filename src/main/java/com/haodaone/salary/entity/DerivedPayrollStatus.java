package com.haodaone.salary.entity;

/**
 * The "Payroll Status" shown per employee on the Employee Salary List is
 * derived, not stored: it's either one of {@link PayrollItemStatus}'s
 * values (taken from that employee's most recent payroll line) or one of
 * these two states for an employee who has never been through a payroll
 * run yet.
 */
public final class DerivedPayrollStatus {

    /** No active salary structure has been defined for this employee. */
    public static final String NOT_CONFIGURED = "NOT_CONFIGURED";

    /** A salary structure exists but the employee has never appeared in a payroll run. */
    public static final String READY = "READY";

    private DerivedPayrollStatus() {
    }
}
