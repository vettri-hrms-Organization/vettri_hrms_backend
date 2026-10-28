package com.haodaone.attendance.shift;

import com.haodaone.attendance.shift.service.ShiftEvaluator;
import com.haodaone.attendance.shift.service.ShiftEvaluator.DayEvaluation;
import com.haodaone.attendance.shift.service.ShiftEvaluator.LeaveKind;
import com.haodaone.attendance.shift.service.ShiftRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftEvaluatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    /** 09:30-18:30, 60 min break, 10 min grace in, 5 min grace out, HD 4h, full 8h, OT >= 30 min. */
    private static ShiftRule general(boolean overtime) {
        return new ShiftRule(1L, "General", "GEN", null, LocalTime.of(9, 30), LocalTime.of(18, 30), 60,
                10, 5, new BigDecimal("4"), new BigDecimal("8"), overtime, 30);
    }

    /** 22:00-06:00 overnight, 30 min break. */
    private static ShiftRule night() {
        return new ShiftRule(2L, "Night", "NGT", null, LocalTime.of(22, 0), LocalTime.of(6, 0), 30,
                15, 0, new BigDecimal("3.5"), new BigDecimal("7.5"), true, 60);
    }

    private static DayEvaluation eval(ShiftRule rule, LocalDateTime... times) {
        return ShiftEvaluator.evaluate(rule, DAY, List.of(times), false, false, LeaveKind.NONE);
    }

    private static LocalDateTime at(int h, int m) {
        return DAY.atTime(h, m);
    }

    @Test
    void fullDayOnTime() {
        DayEvaluation e = eval(general(false), at(9, 25), at(18, 35));
        assertThat(e.status()).isEqualTo(ShiftEvaluator.PRESENT);
        assertThat(e.workedMinutes()).isEqualTo(9 * 60 + 10 - 60);
        assertThat(e.late()).isFalse();
        assertThat(e.earlyOut()).isFalse();
        assertThat(e.overtimeMinutes()).isZero(); // OT disabled
    }

    @Test
    void graceInBoundaryIsInclusive() {
        assertThat(eval(general(false), at(9, 40), at(18, 30)).late()).isFalse();
        DayEvaluation late = eval(general(false), at(9, 41), at(18, 30));
        assertThat(late.late()).isTrue();
        assertThat(late.lateInMinutes()).isEqualTo(11); // measured from shift start
    }

    @Test
    void graceOutBoundary() {
        assertThat(eval(general(false), at(9, 30), at(18, 25)).earlyOut()).isFalse();
        DayEvaluation early = eval(general(false), at(9, 30), at(18, 24));
        assertThat(early.earlyOut()).isTrue();
        assertThat(early.earlyOutMinutes()).isEqualTo(6);
    }

    @Test
    void halfDayAndBelowThreshold() {
        // 09:30-14:30 = 5h span - 1h break = 4h -> exactly half-day threshold
        assertThat(eval(general(false), at(9, 30), at(14, 30)).status()).isEqualTo(ShiftEvaluator.HALF_DAY);
        // 3h59m worked -> absent
        assertThat(eval(general(false), at(9, 30), at(14, 29)).status()).isEqualTo(ShiftEvaluator.ABSENT);
        // 7h59m worked -> half day
        assertThat(eval(general(false), at(9, 30), at(18, 29)).status()).isEqualTo(ShiftEvaluator.HALF_DAY);
    }

    @Test
    void singlePunchIsPresentWithMissingOut() {
        DayEvaluation e = eval(general(false), at(9, 31));
        assertThat(e.status()).isEqualTo(ShiftEvaluator.PRESENT);
        assertThat(e.missingOut()).isTrue();
        assertThat(e.workedMinutes()).isZero();
    }

    @Test
    void noPunchIsAbsent() {
        assertThat(eval(general(false)).status()).isEqualTo(ShiftEvaluator.ABSENT);
    }

    @Test
    void overtimeRespectsMinimum() {
        assertThat(eval(general(true), at(9, 30), at(18, 59)).overtimeMinutes()).isZero(); // 29 < 30
        assertThat(eval(general(true), at(9, 30), at(19, 0)).overtimeMinutes()).isEqualTo(30);
        assertThat(eval(general(true), at(9, 30), at(20, 15)).overtimeMinutes()).isEqualTo(105);
    }

    @Test
    void overnightShiftUsesNextDayPunches() {
        DayEvaluation e = eval(night(), at(21, 55), DAY.plusDays(1).atTime(7, 15));
        assertThat(e.firstIn()).isEqualTo(at(21, 55));
        assertThat(e.lastOut()).isEqualTo(DAY.plusDays(1).atTime(7, 15));
        assertThat(e.workedMinutes()).isEqualTo(9 * 60 + 20 - 30);
        assertThat(e.status()).isEqualTo(ShiftEvaluator.PRESENT);
        assertThat(e.late()).isFalse();
        assertThat(e.overtimeMinutes()).isEqualTo(75);
    }

    @Test
    void overnightShiftIgnoresPunchesOutsideWindow() {
        // Morning punches of DAY belong to the previous night's shift (window starts 18:00 on DAY).
        DayEvaluation e = eval(night(), at(5, 50), at(22, 20), DAY.plusDays(1).atTime(6, 0));
        assertThat(e.firstIn()).isEqualTo(at(22, 20));
        assertThat(e.late()).isTrue();
        assertThat(e.lateInMinutes()).isEqualTo(20);
        assertThat(e.earlyOut()).isFalse();
    }

    @Test
    void weekOffAndHolidayWorkIsOvertime() {
        DayEvaluation off = ShiftEvaluator.evaluate(general(true), DAY, List.of(at(10, 0), at(15, 0)),
                false, true, LeaveKind.NONE);
        assertThat(off.status()).isEqualTo(ShiftEvaluator.WEEK_OFF);
        assertThat(off.overtimeMinutes()).isEqualTo(240);
        DayEvaluation holiday = ShiftEvaluator.evaluate(general(false), DAY, List.of(at(10, 0), at(15, 0)),
                true, false, LeaveKind.NONE);
        assertThat(holiday.status()).isEqualTo(ShiftEvaluator.HOLIDAY);
        assertThat(holiday.overtimeMinutes()).isZero();
    }

    @Test
    void leaveDays() {
        assertThat(ShiftEvaluator.evaluate(general(false), DAY, List.of(), false, false, LeaveKind.FULL).status())
                .isEqualTo(ShiftEvaluator.LEAVE);
        DayEvaluation half = ShiftEvaluator.evaluate(general(false), DAY, List.of(at(13, 30), at(18, 30)),
                false, false, LeaveKind.HALF);
        assertThat(half.status()).isEqualTo(ShiftEvaluator.HALF_DAY);
        assertThat(half.late()).isFalse(); // no late mark on a half-day leave
    }
}
