package com.haodaone.settlement.service;

import com.haodaone.settlement.entity.FinalSettlementLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/** Pure Full & Final arithmetic (unit-tested). Amounts are rupees, 2 decimals. */
public final class SettlementMath {

    /** s.10(10AA)(ii) limit for leave encashment at retirement/resignation (non-government), from 1 Apr 2023. */
    public static final BigDecimal LEAVE_ENCASHMENT_EXEMPTION = new BigDecimal("2500000.00");
    /** s.10(10)(ii)/(iii) gratuity exemption limit. */
    public static final BigDecimal GRATUITY_EXEMPTION = new BigDecimal("2000000.00");
    /** Day rate divisor for notice recovery (monthly / 30, the common Indian convention). */
    public static final BigDecimal DAY_DIVISOR = BigDecimal.valueOf(30);

    private SettlementMath() {
    }

    public record Notice(int servedDays, int shortfallDays) {
    }

    /**
     * Notice served = resignation date to last working date, both inclusive (no resignation date =
     * treated as fully served). Shortfall = notice period - served, never negative; 0 when waived.
     * {@code overrideShortfall} (HR-entered) wins when not null.
     */
    public static Notice notice(LocalDate resignationDate, LocalDate lastWorkingDate, int noticePeriodDays,
                                boolean waived, Integer overrideShortfall) {
        int period = Math.max(0, noticePeriodDays);
        int served = resignationDate == null || lastWorkingDate == null || lastWorkingDate.isBefore(resignationDate)
                ? period
                : (int) ChronoUnit.DAYS.between(resignationDate, lastWorkingDate.plusDays(1));
        int shortfall = overrideShortfall != null ? Math.max(0, overrideShortfall) : Math.max(0, period - served);
        if (waived) shortfall = 0;
        return new Notice(served, shortfall);
    }

    /** Shortfall days x (monthly basis / 30). */
    public static BigDecimal noticeRecovery(int shortfallDays, BigDecimal monthlyBasis) {
        if (shortfallDays <= 0 || monthlyBasis == null || monthlyBasis.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        return monthlyBasis.multiply(BigDecimal.valueOf(shortfallDays)).divide(DAY_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    /** Days of {@code month} after the last working date (0 when the employee worked to month end). */
    public static int daysAfterExit(YearMonth month, LocalDate lastWorkingDate) {
        LocalDate end = month.atEndOfMonth();
        if (lastWorkingDate == null || !lastWorkingDate.isBefore(end)) return 0;
        if (lastWorkingDate.isBefore(month.atDay(1))) return month.lengthOfMonth();
        return (int) ChronoUnit.DAYS.between(lastWorkingDate, end);
    }

    /** Taxable part of leave encashment paid at exit, given how much of the lifetime exemption is still unused. */
    public static BigDecimal taxableLeaveEncashment(BigDecimal amount, BigDecimal exemptionLeft) {
        BigDecimal a = nz(amount);
        BigDecimal left = exemptionLeft == null ? LEAVE_ENCASHMENT_EXEMPTION : exemptionLeft.max(BigDecimal.ZERO);
        return a.subtract(a.min(left)).max(BigDecimal.ZERO);
    }

    /** Taxable part of gratuity: anything above the Rs 20 lakh exemption. */
    public static BigDecimal taxableGratuity(BigDecimal amount) {
        return nz(amount).subtract(GRATUITY_EXEMPTION).max(BigDecimal.ZERO);
    }

    public record Totals(BigDecimal earnings, BigDecimal deductions, BigDecimal taxable, BigDecimal net) {
    }

    /** Sums the lines: net = earnings - deductions (negative = amount recoverable from the employee). */
    public static Totals totals(Collection<FinalSettlementLine> lines) {
        BigDecimal earnings = BigDecimal.ZERO, deductions = BigDecimal.ZERO, taxable = BigDecimal.ZERO;
        for (FinalSettlementLine line : lines) {
            if (line.isDeleted()) continue;
            if (line.isEarning()) {
                earnings = earnings.add(nz(line.getAmount()));
                taxable = taxable.add(nz(line.getTaxableAmount()));
            } else {
                deductions = deductions.add(nz(line.getAmount()));
            }
        }
        return new Totals(scale(earnings), scale(deductions), scale(taxable), scale(earnings.subtract(deductions)));
    }

    static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
