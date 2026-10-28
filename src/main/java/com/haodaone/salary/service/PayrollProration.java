package com.haodaone.salary.service;

import com.haodaone.salary.entity.SalaryComponents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Loss-of-pay / mid-month-joiner proration for one payroll line. Pure
 * functions (no repositories) so the math is unit-testable on its own.
 *
 * Rules, kept deliberately simple:
 *  - The base is calendar days D in the month (the usual Indian convention),
 *    so a monthly salary / D is one day's pay regardless of weekends.
 *  - Payable days = D - days before dateOfJoining (if they joined this month)
 *    - LOP days, never below zero.
 *  - LOP days = approved days of unpaid leave types inside the month. A
 *    request wholly inside the month counts its own {@code days} (which
 *    already excludes weekends/holidays and handles half days). One that
 *    spans a month boundary is split by the share of its Mon-Fri days that
 *    fall inside the month, rounded to the nearest half day; holidays are
 *    ignored for that split.
 *  - Every earning scales by payableDays / D. Deductions stay exactly as
 *    configured (PF/ESI/PT/TDS are set on the structure) but are trimmed,
 *    last-listed first, so total deductions never exceed prorated earnings.
 */
public final class PayrollProration {

    private PayrollProration() {
    }

    /** One approved unpaid leave request, reduced to what the math needs. */
    public record LeaveSpan(LocalDate startDate, LocalDate endDate, double days) {
    }

    public record Result(int daysInPeriod, BigDecimal lopDays, BigDecimal paidDays) {
        public boolean isFullPeriod() {
            return paidDays.compareTo(BigDecimal.valueOf(daysInPeriod)) == 0;
        }
    }

    public static Result compute(YearMonth period, LocalDate dateOfJoining, List<LeaveSpan> unpaidLeaves) {
        int daysInPeriod = period.lengthOfMonth();
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();

        LocalDate employedFrom = periodStart;
        if (dateOfJoining != null && dateOfJoining.isAfter(periodStart)) {
            employedFrom = dateOfJoining.isAfter(periodEnd) ? periodEnd.plusDays(1) : dateOfJoining;
        }
        int daysBeforeJoining = (int) ChronoUnit.DAYS.between(periodStart, employedFrom);
        BigDecimal employedDays = BigDecimal.valueOf(daysInPeriod - daysBeforeJoining);

        BigDecimal lop = BigDecimal.ZERO;
        if (unpaidLeaves != null && employedFrom.isBefore(periodEnd.plusDays(1))) {
            for (LeaveSpan leave : unpaidLeaves) {
                lop = lop.add(lopDaysWithin(leave, employedFrom, periodEnd));
            }
        }
        lop = lop.min(employedDays).setScale(1, RoundingMode.HALF_UP);
        BigDecimal paid = employedDays.subtract(lop).max(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP);
        return new Result(daysInPeriod, lop, paid);
    }

    /** The part of {@code leave} that falls inside [from, to], in leave days. */
    static BigDecimal lopDaysWithin(LeaveSpan leave, LocalDate from, LocalDate to) {
        if (leave.startDate() == null || leave.endDate() == null || leave.days() <= 0) return BigDecimal.ZERO;
        LocalDate overlapStart = leave.startDate().isAfter(from) ? leave.startDate() : from;
        LocalDate overlapEnd = leave.endDate().isBefore(to) ? leave.endDate() : to;
        if (overlapStart.isAfter(overlapEnd)) return BigDecimal.ZERO;

        BigDecimal days = BigDecimal.valueOf(leave.days());
        if (overlapStart.equals(leave.startDate()) && overlapEnd.equals(leave.endDate())) {
            return days;
        }
        long totalWeekdays = weekdays(leave.startDate(), leave.endDate());
        if (totalWeekdays == 0) return BigDecimal.ZERO;
        BigDecimal share = days.multiply(BigDecimal.valueOf(weekdays(overlapStart, overlapEnd)))
                .divide(BigDecimal.valueOf(totalWeekdays), 4, RoundingMode.HALF_UP);
        // Nearest half day.
        return share.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(2), 1, RoundingMode.UNNECESSARY);
    }

    static long weekdays(LocalDate from, LocalDate to) {
        long count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) count++;
        }
        return count;
    }

    /** A new components copy with earnings scaled to paidDays / daysInPeriod and deductions capped at gross. */
    public static SalaryComponents prorate(SalaryComponents full, BigDecimal paidDays, int daysInPeriod) {
        BigDecimal days = BigDecimal.valueOf(daysInPeriod);
        SalaryComponents c = new SalaryComponents();
        c.setBasicSalary(scale(full.getBasicSalary(), paidDays, days));
        c.setHra(scale(full.getHra(), paidDays, days));
        c.setSpecialAllowance(scale(full.getSpecialAllowance(), paidDays, days));
        c.setMedicalAllowance(scale(full.getMedicalAllowance(), paidDays, days));
        c.setTravelAllowance(scale(full.getTravelAllowance(), paidDays, days));
        c.setBonus(scale(full.getBonus(), paidDays, days));
        c.setIncentives(scale(full.getIncentives(), paidDays, days));
        c.setOvertime(scale(full.getOvertime(), paidDays, days));
        c.setPf(full.getPf());
        c.setEsi(full.getEsi());
        c.setProfessionalTax(full.getProfessionalTax());
        c.setTds(full.getTds());
        c.setOtherDeductions(full.getOtherDeductions());
        capDeductions(c);
        return c;
    }

    private static BigDecimal scale(BigDecimal amount, BigDecimal paidDays, BigDecimal days) {
        if (amount == null) return BigDecimal.ZERO;
        return amount.multiply(paidDays).divide(days, 2, RoundingMode.HALF_UP);
    }

    private static void capDeductions(SalaryComponents c) {
        BigDecimal excess = c.totalDeductions().subtract(c.grossSalary());
        if (excess.signum() <= 0) return;
        BigDecimal cut = c.getOtherDeductions().min(excess);
        c.setOtherDeductions(c.getOtherDeductions().subtract(cut));
        excess = excess.subtract(cut);
        cut = c.getTds().min(excess);
        c.setTds(c.getTds().subtract(cut));
        excess = excess.subtract(cut);
        cut = c.getProfessionalTax().min(excess);
        c.setProfessionalTax(c.getProfessionalTax().subtract(cut));
        excess = excess.subtract(cut);
        cut = c.getEsi().min(excess);
        c.setEsi(c.getEsi().subtract(cut));
        excess = excess.subtract(cut);
        cut = c.getPf().min(excess);
        c.setPf(c.getPf().subtract(cut));
    }
}
