package com.haodaone.salary.statutory;

import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.StatutorySettings;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure PF / ESI arithmetic and the "deductions never exceed earnings" cap.
 *
 * PF (EPF & MP Act):
 *  - PF wages = basic (+DA; this payroll has no separate DA component, so
 *    DA paid should be folded into basic) for the month, already prorated
 *    for LOP. Capped at the wage ceiling (Rs 15,000) unless the company
 *    opts to contribute on full basic.
 *  - Employee share = PF wages x 12%. Employer share = PF wages x 12%, of
 *    which EPS = min(wage, 15,000) x 8.33% and EPF = the remainder.
 *  - EPS/EDLI wages are always capped at the ceiling. Employees aged 58+
 *    (no EPS) and voluntary higher EPS are not modelled.
 *  - Amounts rounded to the nearest rupee, as the ECR expects.
 *
 * ESI: contributions on the month's wages, each rounded UP to the next
 * rupee (ESI (Central) Rules, r.31). The exemption of the employee share
 * for an average daily wage up to Rs 176 is not modelled.
 */
public final class StatutoryMath {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private StatutoryMath() {
    }

    public record PfResult(BigDecimal pfWages, BigDecimal epsWages, BigDecimal edliWages,
                           BigDecimal employeeShare, BigDecimal employerEps, BigDecimal employerEpf) {
        public static final PfResult ZERO = new PfResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public record EsiResult(boolean applicable, BigDecimal wages, BigDecimal employeeShare, BigDecimal employerShare) {
        public static final EsiResult NOT_APPLICABLE = new EsiResult(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public static PfResult pf(BigDecimal basic, StatutorySettings s) {
        BigDecimal wage = basic == null ? BigDecimal.ZERO : basic.max(BigDecimal.ZERO);
        if (wage.signum() == 0) return PfResult.ZERO;
        BigDecimal ceiling = s.getPfWageCeiling();
        BigDecimal capped = ceiling != null && ceiling.signum() > 0 ? wage.min(ceiling) : wage;
        BigDecimal pfWages = s.isPfOnFullBasic() ? wage : capped;
        BigDecimal employee = pct(pfWages, s.getPfEmployeeRate(), RoundingMode.HALF_UP);
        BigDecimal employerTotal = pct(pfWages, s.getPfEmployerRate(), RoundingMode.HALF_UP);
        BigDecimal eps = pct(capped, s.getEpsRate(), RoundingMode.HALF_UP).min(employerTotal);
        BigDecimal epf = employerTotal.subtract(eps);
        return new PfResult(scale2(pfWages), scale2(capped), scale2(capped), employee, eps, epf);
    }

    /**
     * @param eligible whether the employee is covered this month (monthly gross at or below the threshold,
     *                 or still inside a contribution period they were covered in)
     * @param wages    the month's ESI wages (actual gross earned, excluding non-wage reimbursements)
     */
    public static EsiResult esi(boolean eligible, BigDecimal wages, StatutorySettings s) {
        if (!eligible || wages == null || wages.signum() <= 0) return EsiResult.NOT_APPLICABLE;
        return new EsiResult(true, scale2(wages),
                pct(wages, s.getEsiEmployeeRate(), RoundingMode.CEILING),
                pct(wages, s.getEsiEmployerRate(), RoundingMode.CEILING));
    }

    /** Coverage test: the employee's regular (full-month) gross is at or below the ESI threshold. */
    public static boolean esiCovered(BigDecimal monthlyGross, StatutorySettings s) {
        return monthlyGross != null && monthlyGross.signum() > 0
                && s.getEsiWageThreshold() != null && monthlyGross.compareTo(s.getEsiWageThreshold()) <= 0;
    }

    /** First month (1-12) of the ESI contribution period containing {@code month}: April or October. */
    public static int esiContributionPeriodStart(int month) {
        return (month >= 4 && month <= 9) ? 4 : 10;
    }

    /**
     * Trims deductions so they never exceed {@code earnings}: other deductions first, then TDS, PT, ESI
     * and finally PF (the same order PayrollProration uses).
     */
    public static void capDeductions(SalaryComponents c, BigDecimal earnings) {
        BigDecimal excess = c.totalDeductions().subtract(earnings == null ? BigDecimal.ZERO : earnings);
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

    private static BigDecimal pct(BigDecimal amount, BigDecimal ratePercent, RoundingMode mode) {
        if (ratePercent == null || ratePercent.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        return amount.multiply(ratePercent).divide(HUNDRED, 0, mode).setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
