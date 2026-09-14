package com.haodaone.salary.entity;

import java.util.Set;

/**
 * Per-employee status within a single {@link PayrollRun}. PENDING is the
 * default when a run is generated; HR can move an item to ON_HOLD before
 * processing (e.g. an employee under a pay dispute that month) instead of
 * pulling them out of the run entirely. PROCESSED/PAID mirror the run's own
 * PROCESSED/PAID transition but are tracked per-item since a held item does
 * not advance with the rest of the run.
 */
public final class PayrollItemStatus {

    public static final String PENDING = "PENDING";
    public static final String ON_HOLD = "ON_HOLD";
    public static final String PROCESSED = "PROCESSED";
    public static final String PAID = "PAID";

    public static final Set<String> ALL = Set.of(PENDING, ON_HOLD, PROCESSED, PAID);

    private PayrollItemStatus() {
    }
}
