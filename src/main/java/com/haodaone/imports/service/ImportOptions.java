package com.haodaone.imports.service;

import java.util.Locale;

/**
 * Per-import switches chosen in the wizard.
 *
 * @param replace   overwrite rows imported earlier for the same key (leave balances, opening YTD) instead of rejecting them
 * @param leaveMode ADJUSTMENT (default: a balance adjustment so the balance equals the opening figure today) or
 *                  CARRY_FORWARD (opening figure becomes the year's carried-forward days; accrual continues on top)
 */
public record ImportOptions(boolean replace, String leaveMode) {

    public static final ImportOptions DEFAULT = new ImportOptions(false, null);

    public static ImportOptions of(Boolean replace, String leaveMode) {
        String mode = leaveMode == null || leaveMode.isBlank() ? null : leaveMode.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return new ImportOptions(Boolean.TRUE.equals(replace), mode);
    }

    public boolean carryForward() {
        return "CARRY_FORWARD".equals(leaveMode);
    }

    /** Human-readable form stored on the batch. */
    public String describe(ImportType type) {
        StringBuilder sb = new StringBuilder();
        if (replace) sb.append("Replace existing");
        if (type == ImportType.LEAVE_BALANCES) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(carryForward() ? "As carried-forward days" : "As balance adjustment");
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
