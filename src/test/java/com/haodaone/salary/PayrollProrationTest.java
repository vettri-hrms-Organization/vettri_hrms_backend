package com.haodaone.salary;

import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.service.PayrollProration;
import com.haodaone.salary.service.PayrollProration.LeaveSpan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure proration math - no Spring context. */
class PayrollProrationTest {

    private static final YearMonth OCT_2026 = YearMonth.of(2026, 10); // 31 days
    private static final YearMonth NOV_2026 = YearMonth.of(2026, 11); // 30 days

    @Test
    void fullMonthWhenJoinedEarlierAndNoUnpaidLeave() {
        var result = PayrollProration.compute(OCT_2026, LocalDate.of(2025, 1, 1), List.of());

        assertTrue(result.isFullPeriod());
        assertEquals(new BigDecimal("31.0"), result.paidDays());
        assertEquals(new BigDecimal("0.0"), result.lopDays());
    }

    @Test
    void midMonthJoinerIsPaidFromJoiningDateOnly() {
        // Joined 16 Oct: 1-15 Oct (15 days) are not payable -> 16 of 31 days.
        var result = PayrollProration.compute(OCT_2026, LocalDate.of(2026, 10, 16), List.of());

        assertFalse(result.isFullPeriod());
        assertEquals(31, result.daysInPeriod());
        assertEquals(new BigDecimal("16.0"), result.paidDays());
        assertEquals(new BigDecimal("0.0"), result.lopDays());

        SalaryComponents full = components("31000.00", "6200.00", "1800.00");
        SalaryComponents prorated = PayrollProration.prorate(full, result.paidDays(), result.daysInPeriod());
        assertEquals(new BigDecimal("16000.00"), prorated.getBasicSalary());
        assertEquals(new BigDecimal("3200.00"), prorated.getHra());
        assertEquals(new BigDecimal("19200.00"), prorated.grossSalary());
        // Statutory deductions are kept as configured.
        assertEquals(new BigDecimal("1800.00"), prorated.getPf());
        assertEquals(new BigDecimal("17400.00"), prorated.netSalary());
    }

    @Test
    void approvedUnpaidLeaveInsideTheMonthIsLossOfPay() {
        // 9-11 Nov 2026 (Mon-Wed), 3 days of unpaid leave -> 27 of 30 days paid.
        var leave = new LeaveSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 11), 3);
        var result = PayrollProration.compute(NOV_2026, LocalDate.of(2024, 4, 1), List.of(leave));

        assertEquals(new BigDecimal("3.0"), result.lopDays());
        assertEquals(new BigDecimal("27.0"), result.paidDays());

        SalaryComponents prorated = PayrollProration.prorate(components("30000.00", "0", "0"), result.paidDays(), result.daysInPeriod());
        assertEquals(new BigDecimal("27000.00"), prorated.getBasicSalary());
    }

    @Test
    void unpaidLeaveSpanningMonthsIsSplitByWeekdaysInsideThePeriod() {
        // Thu 29 Oct - Tue 3 Nov: 4 weekdays (29, 30 Oct, 2, 3 Nov), 2 of them in November.
        var leave = new LeaveSpan(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 3), 4);

        assertEquals(new BigDecimal("2.0"), PayrollProration.compute(NOV_2026, null, List.of(leave)).lopDays());
        assertEquals(new BigDecimal("2.0"), PayrollProration.compute(OCT_2026, null, List.of(leave)).lopDays());
    }

    @Test
    void halfDayUnpaidLeaveCountsAsHalf() {
        var leave = new LeaveSpan(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10), 0.5);
        var result = PayrollProration.compute(NOV_2026, null, List.of(leave));

        assertEquals(new BigDecimal("0.5"), result.lopDays());
        assertEquals(new BigDecimal("29.5"), result.paidDays());
    }

    @Test
    void deductionsAreTrimmedSoTheyNeverExceedProratedEarnings() {
        // Joined on the last day: 1 paid day of 31 -> basic 1,000 against 2,000 of deductions.
        var result = PayrollProration.compute(OCT_2026, LocalDate.of(2026, 10, 31), List.of());
        SalaryComponents full = components("31000.00", "0", "1800.00");
        full.setProfessionalTax(new BigDecimal("200.00"));

        SalaryComponents prorated = PayrollProration.prorate(full, result.paidDays(), result.daysInPeriod());

        assertEquals(new BigDecimal("1000.00"), prorated.grossSalary());
        assertEquals(0, prorated.totalDeductions().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, prorated.getProfessionalTax().signum());
        assertEquals(0, prorated.netSalary().signum());
    }

    private static SalaryComponents components(String basic, String hra, String pf) {
        SalaryComponents c = new SalaryComponents();
        c.setBasicSalary(new BigDecimal(basic));
        c.setHra(new BigDecimal(hra));
        c.setPf(new BigDecimal(pf));
        return c;
    }
}
