package com.haodaone.attendance.shift;

import com.haodaone.attendance.shift.entity.AttendanceShiftSettings;
import com.haodaone.attendance.shift.service.DailyAttendanceService;
import com.haodaone.attendance.shift.service.OvertimeService;
import com.haodaone.attendance.shift.service.ShiftCalendar;
import com.haodaone.attendance.shift.service.ShiftRule;
import com.haodaone.company.service.WorkingWeek;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftCalendarAndPricingTest {

    private static ShiftRule rule(long id, String code, int startHour) {
        return new ShiftRule(id, code, code, null, LocalTime.of(startHour, 0), LocalTime.of(startHour, 0).plusHours(9),
                60, 10, 0, new BigDecimal("4"), new BigDecimal("8"), false, 30);
    }

    private static final ShiftRule GENERAL = rule(1, "GEN", 9);
    private static final ShiftRule DEPT = rule(2, "DEP", 7);
    private static final ShiftRule EMP = rule(3, "EMP", 10);
    private static final ShiftRule ROSTER = rule(4, "ROS", 14);

    private ShiftCalendar calendar() {
        ShiftCalendar calendar = new ShiftCalendar(GENERAL,
                Map.of(1L, GENERAL, 2L, DEPT, 3L, EMP, 4L, ROSTER), WorkingWeek.parse("SUNDAY,SATURDAY#2"));
        calendar.addDepartmentAssignment(50L, 2L, LocalDate.of(2026, 10, 1));
        calendar.addEmployeeAssignment(7L, 3L, LocalDate.of(2026, 10, 15));
        calendar.addRosterCell(7L, LocalDate.of(2026, 10, 20), 4L, false);
        calendar.addRosterCell(7L, LocalDate.of(2026, 10, 21), null, true);
        calendar.addRosterCell(7L, LocalDate.of(2026, 10, 25), 4L, false); // a Sunday rostered to work
        return calendar;
    }

    @Test
    void resolutionOrderRosterEmployeeDepartmentCompany() {
        ShiftCalendar c = calendar();
        assertThat(c.resolve(8L, null, LocalDate.of(2026, 10, 20)).source()).isEqualTo(ShiftCalendar.SOURCE_COMPANY);
        assertThat(c.resolve(8L, 50L, LocalDate.of(2026, 9, 30)).shift().code()).isEqualTo("GEN"); // before dept effective
        assertThat(c.resolve(8L, 50L, LocalDate.of(2026, 10, 1)).shift().code()).isEqualTo("DEP");
        assertThat(c.resolve(7L, 50L, LocalDate.of(2026, 10, 14)).shift().code()).isEqualTo("DEP"); // before employee effective
        assertThat(c.resolve(7L, 50L, LocalDate.of(2026, 10, 15)).source()).isEqualTo(ShiftCalendar.SOURCE_EMPLOYEE);
        ShiftCalendar.Resolved rostered = c.resolve(7L, 50L, LocalDate.of(2026, 10, 20));
        assertThat(rostered.shift().code()).isEqualTo("ROS");
        assertThat(rostered.source()).isEqualTo(ShiftCalendar.SOURCE_ROSTER);
    }

    @Test
    void weekOffComesFromRosterThenWorkingWeek() {
        ShiftCalendar c = calendar();
        ShiftCalendar.Resolved off = c.resolve(7L, 50L, LocalDate.of(2026, 10, 21));
        assertThat(off.weekOff()).isTrue();
        assertThat(off.shift().code()).isEqualTo("EMP"); // falls back for the punch window
        assertThat(c.resolve(7L, 50L, LocalDate.of(2026, 10, 25)).weekOff()).isFalse(); // rostered Sunday
        assertThat(c.resolve(8L, 50L, LocalDate.of(2026, 10, 25)).weekOff()).isTrue();  // Sunday
        assertThat(c.resolve(8L, 50L, LocalDate.of(2026, 10, 10)).weekOff()).isTrue();  // 2nd Saturday
        assertThat(c.resolve(8L, 50L, LocalDate.of(2026, 10, 3)).weekOff()).isFalse();  // 1st Saturday
    }

    @Test
    void hourlyRateAndOvertimeAmount() {
        // 52,000 / (26 days x 8h) = 250.00 per hour
        BigDecimal hourly = OvertimeService.hourlyRate(new BigDecimal("52000"), 26, 480);
        assertThat(hourly).isEqualByComparingTo("250.00");
        assertThat(OvertimeService.amount(AttendanceShiftSettings.RATE_MULTIPLIER, new BigDecimal("1.5"), null, hourly, 90))
                .isEqualByComparingTo("562.50");
        assertThat(OvertimeService.amount(AttendanceShiftSettings.RATE_MULTIPLIER, new BigDecimal("2"), null, hourly, 60))
                .isEqualByComparingTo("500.00");
        assertThat(OvertimeService.amount(AttendanceShiftSettings.RATE_FLAT, BigDecimal.ONE, new BigDecimal("200"), null, 45))
                .isEqualByComparingTo("150.00");
        assertThat(OvertimeService.amount(AttendanceShiftSettings.RATE_FLAT, BigDecimal.ONE, new BigDecimal("200"), null, 0))
                .isEqualByComparingTo("0");
    }

    @Test
    void latePenaltyEveryNMarks() {
        BigDecimal half = new BigDecimal("0.5");
        assertThat(DailyAttendanceService.penaltyDays(2, 3, half)).isEqualByComparingTo("0");
        assertThat(DailyAttendanceService.penaltyDays(3, 3, half)).isEqualByComparingTo("0.5");
        assertThat(DailyAttendanceService.penaltyDays(7, 3, half)).isEqualByComparingTo("1.0");
        assertThat(DailyAttendanceService.penaltyDays(0, 3, half)).isEqualByComparingTo("0");
    }
}
