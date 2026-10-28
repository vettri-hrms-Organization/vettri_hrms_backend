package com.haodaone.attendance.shift.service;

import com.haodaone.attendance.shift.entity.WorkShift;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Immutable snapshot of a shift's timing rules - what {@link ShiftEvaluator} needs, decoupled from JPA.
 * A shift whose end is not after its start is overnight: it ends on the next calendar day.
 */
public record ShiftRule(Long shiftId, String name, String code, String color,
                        LocalTime start, LocalTime end, int breakMinutes,
                        int graceInMinutes, int graceOutMinutes,
                        BigDecimal halfDayHours, BigDecimal fullDayHours,
                        boolean overtimeEnabled, int minOvertimeMinutes) {

    public static final LocalTime GENERAL_START = LocalTime.of(9, 30);
    public static final LocalTime GENERAL_END = LocalTime.of(18, 30);

    /** Hours before the shift start from which punches count towards that shift's day. */
    public static final int WINDOW_LEAD_HOURS = 4;

    public static ShiftRule from(WorkShift shift) {
        return new ShiftRule(shift.getId(), shift.getName(), shift.getCode(), shift.getColor(),
                shift.getStartTime(), shift.getEndTime(), shift.getBreakMinutes(),
                shift.getGraceInMinutes(), shift.getGraceOutMinutes(),
                shift.getHalfDayHours(), shift.getFullDayHours(),
                shift.isOvertimeEnabled(), shift.getMinOvertimeMinutes());
    }

    /** The built-in "General shift 09:30-18:30" used when a company has no shift configured yet. */
    public static ShiftRule general(int graceInMinutes) {
        return new ShiftRule(null, "General shift", "GEN", "#2563eb", GENERAL_START, GENERAL_END, 60,
                Math.max(0, graceInMinutes), 0, new BigDecimal("4"), new BigDecimal("8"), false, 30);
    }

    public boolean overnight() {
        return !end.isAfter(start);
    }

    public LocalDateTime startOn(LocalDate date) {
        return date.atTime(start);
    }

    public LocalDateTime endOn(LocalDate date) {
        return overnight() ? date.plusDays(1).atTime(end) : date.atTime(end);
    }

    /** Punches in [windowStart, windowEnd) belong to this date: 4h before the shift start, for 24 hours. */
    public LocalDateTime windowStart(LocalDate date) {
        return startOn(date).minusHours(WINDOW_LEAD_HOURS);
    }

    public LocalDateTime windowEnd(LocalDate date) {
        return windowStart(date).plusHours(24);
    }

    public int spanMinutes() {
        return (int) Duration.between(startOn(LocalDate.of(2000, 1, 3)), endOn(LocalDate.of(2000, 1, 3))).toMinutes();
    }

    /** Scheduled working minutes: shift span minus the break. */
    public int scheduledMinutes() {
        return Math.max(0, spanMinutes() - Math.max(0, breakMinutes));
    }

    public int halfDayMinutes() {
        return halfDayHours == null ? 0 : halfDayHours.multiply(BigDecimal.valueOf(60)).intValue();
    }

    public int fullDayMinutes() {
        return fullDayHours == null ? 0 : fullDayHours.multiply(BigDecimal.valueOf(60)).intValue();
    }
}
