package com.haodaone.settlement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/**
 * Gratuity under the Payment of Gratuity Act, 1972. Pure functions, unit-tested.
 *
 * <ul>
 *   <li>Service = date of joining to last working date, both inclusive.</li>
 *   <li>Eligibility (s.4(1)): at least 5 years of continuous service. Optional s.2A reading
 *       ({@code partialYearDays} = 240 for 6-day-week establishments, 190 for 5-day weeks): 4 completed
 *       years plus at least that many days in the 5th year counts as 5 years. Death or disablement waives
 *       the 5-year condition.</li>
 *   <li>Covered under the Act (s.4(2)): 15 x last drawn monthly (basic + DA) x years / 26, where the
 *       last year counts as a full year when it runs for MORE than six months (6 months and 1 day+).</li>
 *   <li>Not covered (paid voluntarily; s.10(10)(iii) style): 15/30 of a month's wage per COMPLETED year,
 *       no rounding of the last year.</li>
 *   <li>Result is rounded to the nearest rupee and capped at the ceiling (Rs 20,00,000 by default).</li>
 * </ul>
 */
public final class GratuityCalculator {

    public static final BigDecimal DEFAULT_CEILING = new BigDecimal("2000000.00");

    private GratuityCalculator() {
    }

    public record Rules(boolean applicable, boolean coveredUnderAct, BigDecimal ceiling, int partialYearDays) {
        public static Rules defaults() {
            return new Rules(true, true, DEFAULT_CEILING, 0);
        }
    }

    /** Completed years/months/days of service, plus days worked since the last completed year. */
    public record ServicePeriod(int years, int months, int days, long daysIntoCurrentYear) {
    }

    public record Result(boolean eligible, ServicePeriod service, int yearsCounted, BigDecimal monthlyWage,
                         BigDecimal formulaAmount, BigDecimal amount, boolean capped, String explanation) {
    }

    public static ServicePeriod service(LocalDate dateOfJoining, LocalDate lastWorkingDate) {
        if (dateOfJoining == null || lastWorkingDate == null || lastWorkingDate.isBefore(dateOfJoining)) {
            return new ServicePeriod(0, 0, 0, 0);
        }
        LocalDate endExclusive = lastWorkingDate.plusDays(1);
        Period p = Period.between(dateOfJoining, endExclusive);
        long intoYear = ChronoUnit.DAYS.between(dateOfJoining.plusYears(p.getYears()), endExclusive);
        return new ServicePeriod(p.getYears(), p.getMonths(), p.getDays(), intoYear);
    }

    public static Result compute(LocalDate dateOfJoining, LocalDate lastWorkingDate, BigDecimal monthlyBasicPlusDa,
                                 Rules rules, boolean deathOrDisablement) {
        Rules r = rules == null ? Rules.defaults() : rules;
        BigDecimal wage = monthlyBasicPlusDa == null ? BigDecimal.ZERO : monthlyBasicPlusDa.max(BigDecimal.ZERO);
        ServicePeriod s = service(dateOfJoining, lastWorkingDate);
        if (!r.applicable()) {
            return new Result(false, s, 0, wage, BigDecimal.ZERO, BigDecimal.ZERO, false, "Gratuity is not applicable for this company.");
        }
        boolean eligible = s.years() >= 5
                || (r.partialYearDays() > 0 && s.years() == 4 && s.daysIntoCurrentYear() >= r.partialYearDays())
                || deathOrDisablement;
        if (!eligible) {
            return new Result(false, s, 0, wage, BigDecimal.ZERO, BigDecimal.ZERO, false,
                    "Not eligible: " + describe(s) + " of service (5 years required).");
        }
        int years;
        BigDecimal formula;
        String basis;
        if (r.coveredUnderAct()) {
            boolean roundUp = s.months() > 6 || (s.months() == 6 && s.days() > 0);
            years = s.years() + (roundUp ? 1 : 0);
            formula = wage.multiply(BigDecimal.valueOf(15L * years)).divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);
            basis = "15 x " + wage.toPlainString() + " x " + years + " / 26";
        } else {
            years = s.years();
            formula = wage.multiply(BigDecimal.valueOf(15L * years)).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
            basis = "15 x " + wage.toPlainString() + " x " + years + " / 30 (not covered under the Act)";
        }
        BigDecimal rounded = formula.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal ceiling = r.ceiling() == null || r.ceiling().signum() <= 0 ? DEFAULT_CEILING : r.ceiling();
        boolean capped = rounded.compareTo(ceiling) > 0;
        BigDecimal amount = capped ? ceiling.setScale(2, RoundingMode.HALF_UP) : rounded;
        String explanation = describe(s) + " of service, " + years + " year(s) counted: " + basis
                + (capped ? ", capped at " + ceiling.toPlainString() : "")
                + (deathOrDisablement && s.years() < 5 ? " (death/disablement - 5-year condition waived)" : "");
        return new Result(true, s, years, wage, formula, amount, capped, explanation);
    }

    static String describe(ServicePeriod s) {
        return s.years() + "y " + s.months() + "m " + s.days() + "d";
    }
}
