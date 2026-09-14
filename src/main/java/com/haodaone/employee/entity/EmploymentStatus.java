package com.haodaone.employee.entity;

import java.util.Locale;
import java.util.Set;

/**
 * The employee.employment_status column is shared with HaodaAsset, whose
 * EmploymentStatus.java (com.vikkash.assetmanagementv1.entity) defines and
 * writes these values in Title Case: "Active", "On Leave", "Notice Period",
 * "Resigned", "Terminated" (plus legacy sub-stages "Exit Clearance" and
 * "Assets Returned", which HaodaAsset buckets under Notice Period).
 *
 * HaodaOne must read and write the SAME casing - per the "adapt our code to
 * the existing database" rule, not the other way around. An earlier version
 * of this module used an ACTIVE/ON_LEAVE/... vocabulary and a migration that
 * rewrote existing rows to match it; that rewrote data HaodaAsset itself
 * depends on (e.g. its EmployeeRepository does
 * "WHERE e.employmentStatus = 'Resigned'", and its startup migration
 * re-enables login for anyone not exactly matching "Resigned"/"Terminated").
 * See V3__revert_employment_status_casing.sql for the one-time repair of
 * rows already rewritten, and keep every status literal in this module
 * pointed at these constants instead of hand-typed uppercase strings.
 */
public final class EmploymentStatus {

    public static final String ACTIVE        = "Active";
    public static final String ON_LEAVE      = "On Leave";
    public static final String NOTICE_PERIOD = "Notice Period";
    public static final String RESIGNED      = "Resigned";
    public static final String TERMINATED    = "Terminated";

    /** Legacy HaodaAsset sub-stages that can still appear in the column; treated as Notice Period wherever HaodaOne needs a top-level status. */
    public static final String EXIT_CLEARANCE  = "Exit Clearance";
    public static final String ASSETS_RETURNED = "Assets Returned";

    /** The 5 statuses HaodaOne's own filters/updateStatus endpoint accept. */
    public static final Set<String> VALID_STATUSES = Set.of(ACTIVE, ON_LEAVE, NOTICE_PERIOD, RESIGNED, TERMINATED);

    private EmploymentStatus() {
    }

    public static String normalize(String status) {
        if (status == null) {
            return ACTIVE;
        }

        String trimmed = status.trim();
        if (trimmed.isEmpty()) {
            return ACTIVE;
        }

        String normalized = trimmed.replace('_', ' ').replace('-', ' ');
        String compact = normalized.replaceAll("\\s+", " ").trim();
        String upper = compact.toUpperCase(Locale.ROOT);

        return switch (upper) {
            case "ACTIVE" -> ACTIVE;
            case "ON LEAVE" -> ON_LEAVE;
            case "NOTICE PERIOD" -> NOTICE_PERIOD;
            case "RESIGNED" -> RESIGNED;
            case "TERMINATED" -> TERMINATED;
            case "EXIT CLEARANCE" -> EXIT_CLEARANCE;
            case "ASSETS RETURNED" -> ASSETS_RETURNED;
            default -> compact;
        };
    }

    public static Set<String> aliases(String status) {
        String normalized = normalize(status);
        return switch (normalized) {
            case ACTIVE -> Set.of(ACTIVE, "ACTIVE");
            case ON_LEAVE -> Set.of(ON_LEAVE, "ON LEAVE", "ON_LEAVE", "ONLEAVE");
            case NOTICE_PERIOD -> Set.of(NOTICE_PERIOD, "NOTICE PERIOD", "NOTICE_PERIOD", "NOTICEPERIOD");
            case RESIGNED -> Set.of(RESIGNED, "RESIGNED");
            case TERMINATED -> Set.of(TERMINATED, "TERMINATED");
            case EXIT_CLEARANCE -> Set.of(EXIT_CLEARANCE, "EXIT CLEARANCE", "EXIT_CLEARANCE");
            case ASSETS_RETURNED -> Set.of(ASSETS_RETURNED, "ASSETS RETURNED", "ASSETS_RETURNED");
            default -> Set.of(normalized);
        };
    }
}
