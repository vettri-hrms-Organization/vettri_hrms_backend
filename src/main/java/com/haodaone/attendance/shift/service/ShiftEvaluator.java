package com.haodaone.attendance.shift.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Pure daily attendance evaluation - no Spring, no repositories - so every rule is unit-testable.
 *
 * Rules:
 * - Punch times (biometric IN/OUT and web/WFH session check-in/out) in the shift's 24h window
 *   (4h before the shift start onwards; see {@link ShiftRule#windowStart}) belong to the day.
 *   First-in = earliest, last-out = latest (only when it differs from first-in).
 * - Worked minutes = (last-out - first-in) - shift break, never negative.
 * - Holiday > week off > full-day leave take precedence for the status; time worked on a
 *   holiday / week off is all overtime when the shift allows overtime and it meets the minimum.
 * - Otherwise: no punch -> ABSENT; a single punch (missing out) -> PRESENT flagged missingOut;
 *   worked >= full-day hours -> PRESENT; >= half-day hours -> HALF_DAY; less -> ABSENT.
 *   With approved half-day leave the worked half is HALF_DAY when >= half-day hours.
 * - Late-in: first-in strictly after shift start + grace-in; lateInMinutes is measured from
 *   the shift start (arriving 09:55 for a 09:30 shift with 10 min grace = 25 min late).
 * - Early-out: last-out strictly before shift end - grace-out; measured to the shift end.
 * - Overtime: minutes after the shift end when overtime is enabled and they reach the minimum.
 * Late / early marks are not raised on days with half-day leave.
 */
public final class ShiftEvaluator {

    public static final String PRESENT = "PRESENT";
    public static final String HALF_DAY = "HALF_DAY";
    public static final String ABSENT = "ABSENT";
    public static final String WEEK_OFF = "WEEK_OFF";
    public static final String HOLIDAY = "HOLIDAY";
    public static final String LEAVE = "LEAVE";

    public enum LeaveKind { NONE, HALF, FULL }

    public record DayEvaluation(String status, LocalDateTime firstIn, LocalDateTime lastOut,
                                int workedMinutes, int lateInMinutes, int earlyOutMinutes,
                                int overtimeMinutes, boolean late, boolean earlyOut, boolean missingOut) {

        /** Any punch/check-in seen in the window. */
        public boolean punched() {
            return firstIn != null;
        }
    }

    private ShiftEvaluator() {}

    public static DayEvaluation evaluate(ShiftRule rule, LocalDate date, Collection<LocalDateTime> times,
                                         boolean holiday, boolean weekOff, LeaveKind leave) {
        Objects.requireNonNull(rule, "rule");
        LeaveKind leaveKind = leave == null ? LeaveKind.NONE : leave;
        LocalDateTime windowStart = rule.windowStart(date);
        LocalDateTime windowEnd = rule.windowEnd(date);
        List<LocalDateTime> inWindow = (times == null ? List.<LocalDateTime>of() : times).stream()
                .filter(Objects::nonNull)
                .filter(t -> !t.isBefore(windowStart) && t.isBefore(windowEnd))
                .sorted()
                .toList();

        LocalDateTime firstIn = inWindow.isEmpty() ? null : inWindow.get(0);
        LocalDateTime last = inWindow.isEmpty() ? null : inWindow.get(inWindow.size() - 1);
        LocalDateTime lastOut = last != null && last.isAfter(firstIn) ? last : null;
        int worked = lastOut == null ? 0
                : (int) Math.max(0, minutes(firstIn, lastOut) - Math.max(0, rule.breakMinutes()));
        boolean missingOut = firstIn != null && lastOut == null;

        if (holiday || weekOff) {
            int overtime = rule.overtimeEnabled() && worked > 0 && worked >= rule.minOvertimeMinutes() ? worked : 0;
            return new DayEvaluation(holiday ? HOLIDAY : WEEK_OFF, firstIn, lastOut, worked, 0, 0, overtime,
                    false, false, missingOut);
        }
        if (leaveKind == LeaveKind.FULL) {
            return new DayEvaluation(LEAVE, firstIn, lastOut, worked, 0, 0, 0, false, false, missingOut);
        }

        String status;
        if (firstIn == null) {
            status = ABSENT;
        } else if (leaveKind == LeaveKind.HALF) {
            status = missingOut || worked >= rule.halfDayMinutes() ? HALF_DAY : ABSENT;
        } else if (missingOut) {
            status = PRESENT;
        } else if (worked >= rule.fullDayMinutes()) {
            status = PRESENT;
        } else if (worked >= rule.halfDayMinutes()) {
            status = HALF_DAY;
        } else {
            status = ABSENT;
        }

        int lateIn = 0;
        int earlyOut = 0;
        int overtime = 0;
        boolean late = false;
        boolean early = false;
        LocalDateTime shiftStart = rule.startOn(date);
        LocalDateTime shiftEnd = rule.endOn(date);
        if (firstIn != null && leaveKind == LeaveKind.NONE) {
            long lateBy = minutes(shiftStart, firstIn);
            if (lateBy > Math.max(0, rule.graceInMinutes())) {
                late = true;
                lateIn = (int) lateBy;
            }
        }
        if (lastOut != null && leaveKind == LeaveKind.NONE) {
            long earlyBy = minutes(lastOut, shiftEnd);
            if (earlyBy > Math.max(0, rule.graceOutMinutes())) {
                early = true;
                earlyOut = (int) earlyBy;
            }
        }
        if (lastOut != null && rule.overtimeEnabled()) {
            long extra = minutes(shiftEnd, lastOut);
            if (extra > 0 && extra >= rule.minOvertimeMinutes()) {
                overtime = (int) extra;
            }
        }
        return new DayEvaluation(status, firstIn, lastOut, worked, lateIn, earlyOut, overtime, late, early, missingOut);
    }

    private static long minutes(LocalDateTime from, LocalDateTime to) {
        return Duration.between(from, to).toMinutes();
    }
}
