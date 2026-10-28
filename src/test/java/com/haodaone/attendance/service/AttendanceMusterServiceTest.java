package com.haodaone.attendance.service;

import com.haodaone.attendance.service.AttendanceMusterService.DayStatus;
import com.haodaone.attendance.service.AttendanceMusterService.LeaveSlice;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceMusterServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    private DayStatus compute(boolean holiday, boolean weeklyOff, List<LeaveSlice> leaves, boolean present, boolean wfh) {
        return AttendanceMusterService.computeDay(DAY, TODAY, true, holiday, weeklyOff, leaves, present, wfh);
    }

    @Test
    void futureDaysAndDaysOutsideEmploymentAreBlank() {
        assertThat(AttendanceMusterService.computeDay(TODAY.plusDays(1), TODAY, true, false, false, List.of(), false, false).code())
                .isEmpty();
        assertThat(AttendanceMusterService.computeDay(DAY, TODAY, false, false, false, List.of(), true, false).code())
                .isEmpty();
    }

    @Test
    void presenceAbsenceAndWfh() {
        DayStatus present = compute(false, false, List.of(), true, false);
        assertThat(present.code()).isEqualTo("P");
        assertThat(present.present()).isEqualTo(1.0);

        DayStatus wfh = compute(false, false, List.of(), true, true);
        assertThat(wfh.code()).isEqualTo("WFH");
        assertThat(wfh.present()).isEqualTo(1.0);
        assertThat(wfh.wfh()).isEqualTo(1.0);

        DayStatus absent = compute(false, false, null, false, false);
        assertThat(absent.code()).isEqualTo("A");
        assertThat(absent.absent()).isEqualTo(1.0);
    }

    @Test
    void holidayAndWeeklyOffTakePrecedence() {
        assertThat(compute(true, false, List.of(), true, false).code()).isEqualTo("H");
        assertThat(compute(true, false, List.of(), false, false).holiday()).isTrue();
        DayStatus weeklyOff = compute(false, true, List.of(new LeaveSlice("CL", false, true)), false, false);
        assertThat(weeklyOff.code()).isEqualTo("WO");
        assertThat(weeklyOff.paidLeave()).isZero();
    }

    @Test
    void fullDayLeaveShowsTypeCodeAndSplitsPaidAndUnpaid() {
        DayStatus paid = compute(false, false, List.of(new LeaveSlice("CL", false, true)), false, false);
        assertThat(paid.code()).isEqualTo("CL");
        assertThat(paid.paidLeave()).isEqualTo(1.0);

        DayStatus unpaid = compute(false, false, List.of(new LeaveSlice("LOP", false, false)), false, false);
        assertThat(unpaid.code()).isEqualTo("LOP");
        assertThat(unpaid.unpaidLeave()).isEqualTo(1.0);

        DayStatus noCode = compute(false, false, List.of(new LeaveSlice(null, false, true)), false, false);
        assertThat(noCode.code()).isEqualTo("L");
    }

    @Test
    void halfDayLeaveIsHdWithTheOtherHalfPresentOrAbsent() {
        DayStatus worked = compute(false, false, List.of(new LeaveSlice("CL", true, true)), true, false);
        assertThat(worked.code()).isEqualTo("HD");
        assertThat(worked.present()).isEqualTo(0.5);
        assertThat(worked.paidLeave()).isEqualTo(0.5);
        assertThat(worked.absent()).isZero();

        DayStatus notWorked = compute(false, false, List.of(new LeaveSlice("LOP", true, false)), false, false);
        assertThat(notWorked.code()).isEqualTo("HD");
        assertThat(notWorked.absent()).isEqualTo(0.5);
        assertThat(notWorked.unpaidLeave()).isEqualTo(0.5);
    }

    @Test
    void twoHalvesMakeAFullLeaveDay() {
        DayStatus both = compute(false, false,
                List.of(new LeaveSlice("CL", true, true), new LeaveSlice("LOP", true, false)), false, false);
        assertThat(both.code()).isEqualTo("CL");
        assertThat(both.paidLeave()).isEqualTo(0.5);
        assertThat(both.unpaidLeave()).isEqualTo(0.5);
        assertThat(both.absent()).isZero();
    }

    @Test
    void shiftHalfDayAttendanceSplitsPresentAndAbsent() {
        DayStatus half = AttendanceMusterService.computeDay(DAY, TODAY, true, false, false, List.of(), true, false, true);
        assertThat(half.code()).isEqualTo("HD");
        assertThat(half.present()).isEqualTo(0.5);
        assertThat(half.absent()).isEqualTo(0.5);
        assertThat(half.paidLeave()).isZero();

        // A half-day leave keeps its own split; the attendance flag does not double count.
        DayStatus withLeave = AttendanceMusterService.computeDay(DAY, TODAY, true, false, false,
                List.of(new LeaveSlice("CL", true, true)), true, false, true);
        assertThat(withLeave.present()).isEqualTo(0.5);
        assertThat(withLeave.paidLeave()).isEqualTo(0.5);
        assertThat(withLeave.absent()).isZero();
    }
}
