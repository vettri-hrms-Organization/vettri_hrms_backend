package com.haodaone.salary.entity;

import java.util.Set;

/**
 * Lifecycle of a {@link PayrollRun}: DRAFT (items generated, still
 * reviewable/hold-able) -> PROCESSED (totals locked in) -> PAID (payment
 * date recorded). CANCELLED is a dead end reachable only from DRAFT, for
 * a run created against the wrong month. Modeled as plain String constants
 * rather than a Java enum to match every other status column in this
 * codebase (see EmploymentStatus.java) - keeps the column trivially
 * readable in the database and easy to extend without a migration.
 */
public final class PayrollRunStatus {

    public static final String DRAFT = "DRAFT";
    public static final String PROCESSED = "PROCESSED";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";

    public static final Set<String> ALL = Set.of(DRAFT, PROCESSED, PAID, CANCELLED);

    private PayrollRunStatus() {
    }
}
