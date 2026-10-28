package com.haodaone.salary.statutory;

import com.haodaone.salary.statutory.IncomeTaxRates.Regime;
import com.haodaone.salary.statutory.IncomeTaxRates.RegimeRates;
import com.haodaone.salary.statutory.IncomeTaxRates.Slab;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Annual income tax on salary income for one financial year, in either
 * regime. Pure function over {@link IncomeTaxRates}; used for the monthly
 * TDS projection, the employee's regime comparison and Form 16 Part B.
 *
 * Old regime deductions applied: standard deduction, HRA exemption u/s
 * 10(13A) (least of actual HRA, rent paid - 10% of basic, 50%/40% of basic
 * for metro/non-metro), professional tax u/s 16(iii), home loan interest
 * u/s 24(b) (self-occupied, max 2L, set off against salary), 80C (max 1.5L),
 * 80D (capped at 1L - the combined self+senior-parents maximum), 80CCD(1B)
 * (max 50k) and "other" Chapter VI-A deductions as declared (uncapped -
 * HR verifies proofs). New regime: standard deduction only.
 *
 * Rounding: taxable income to the nearest Rs 10 (s.288A), final tax
 * including cess to the nearest Rs 10 (s.288B).
 */
public final class IncomeTaxCalculator {

    public static final BigDecimal CAP_80C = BigDecimal.valueOf(150_000);
    public static final BigDecimal CAP_80D = BigDecimal.valueOf(100_000);
    public static final BigDecimal CAP_80CCD_1B = BigDecimal.valueOf(50_000);
    public static final BigDecimal CAP_24B = BigDecimal.valueOf(200_000);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TEN = BigDecimal.TEN;

    private IncomeTaxCalculator() {
    }

    /** Annual inputs. Any null amount is treated as zero; old-regime-only fields are ignored for NEW. */
    public record TaxInput(int financialYear, Regime regime, BigDecimal grossSalary,
                           BigDecimal annualBasic, BigDecimal annualHra, BigDecimal rentPaid, boolean metroCity,
                           BigDecimal professionalTax, BigDecimal section80C, BigDecimal section80D,
                           BigDecimal homeLoanInterest, BigDecimal nps80ccd1b, BigDecimal otherDeductions) {

        /** Salary only, no declarations - what a NEW regime employee (or an employee who declared nothing) looks like. */
        public static TaxInput simple(int financialYear, Regime regime, BigDecimal grossSalary) {
            return new TaxInput(financialYear, regime, grossSalary, null, null, null, false,
                    null, null, null, null, null, null);
        }
    }

    public record TaxResult(Regime regime, int financialYear, BigDecimal grossSalary, BigDecimal standardDeduction,
                            BigDecimal hraExemption, BigDecimal professionalTax, BigDecimal homeLoanInterest,
                            BigDecimal section80C, BigDecimal section80D, BigDecimal nps80ccd1b,
                            BigDecimal otherDeductions, BigDecimal chapterVIATotal,
                            BigDecimal taxableIncome, BigDecimal taxOnIncome, BigDecimal rebate87A,
                            BigDecimal taxAfterRebate, BigDecimal cess, BigDecimal totalTax) {
    }

    public static TaxResult compute(TaxInput in) {
        Regime regime = in.regime() == null ? Regime.NEW : in.regime();
        RegimeRates rates = IncomeTaxRates.forYear(in.financialYear(), regime);
        BigDecimal gross = nz(in.grossSalary()).max(BigDecimal.ZERO);

        BigDecimal standard = rates.standardDeduction().min(gross);
        BigDecimal hra = BigDecimal.ZERO, pt = BigDecimal.ZERO, homeLoan = BigDecimal.ZERO;
        BigDecimal c80 = BigDecimal.ZERO, d80 = BigDecimal.ZERO, nps = BigDecimal.ZERO, other = BigDecimal.ZERO;
        if (regime == Regime.OLD) {
            hra = hraExemption(nz(in.annualBasic()), nz(in.annualHra()), nz(in.rentPaid()), in.metroCity());
            pt = nz(in.professionalTax()).min(BigDecimal.valueOf(2_500));
            homeLoan = nz(in.homeLoanInterest()).min(CAP_24B);
            c80 = nz(in.section80C()).min(CAP_80C);
            d80 = nz(in.section80D()).min(CAP_80D);
            nps = nz(in.nps80ccd1b()).min(CAP_80CCD_1B);
            other = nz(in.otherDeductions());
        }
        BigDecimal chapterVIA = c80.add(d80).add(nps).add(other);
        BigDecimal taxable = gross.subtract(standard).subtract(hra).subtract(pt).subtract(homeLoan).subtract(chapterVIA)
                .max(BigDecimal.ZERO);
        taxable = roundToTen(taxable);

        BigDecimal tax = slabTax(taxable, rates);
        BigDecimal rebate = BigDecimal.ZERO;
        if (taxable.compareTo(rates.rebateIncomeLimit()) <= 0) {
            rebate = tax.min(rates.rebateMax());
        } else if (rates.marginalRelief()) {
            // Tax may not exceed the income earned above the rebate limit.
            BigDecimal excess = taxable.subtract(rates.rebateIncomeLimit());
            if (tax.compareTo(excess) > 0) rebate = tax.subtract(excess);
        }
        BigDecimal afterRebate = tax.subtract(rebate);
        BigDecimal cess = afterRebate.multiply(rates.cessPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal total = roundToTen(afterRebate.add(cess));

        return new TaxResult(regime, in.financialYear(), gross.setScale(2, RoundingMode.HALF_UP), standard, hra, pt, homeLoan,
                c80, d80, nps, other, chapterVIA, taxable, tax, rebate, afterRebate, cess, total);
    }

    /** Least of: actual HRA received, rent paid minus 10% of basic, 50% (metro) / 40% of basic. */
    public static BigDecimal hraExemption(BigDecimal annualBasic, BigDecimal annualHra, BigDecimal rentPaid, boolean metro) {
        if (annualHra.signum() <= 0 || rentPaid.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal rentExcess = rentPaid.subtract(annualBasic.multiply(BigDecimal.valueOf(10)).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        BigDecimal basicShare = annualBasic.multiply(BigDecimal.valueOf(metro ? 50 : 40)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return annualHra.min(rentExcess).min(basicShare).max(BigDecimal.ZERO);
    }

    static BigDecimal slabTax(BigDecimal taxable, RegimeRates rates) {
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (Slab slab : rates.slabs()) {
            if (taxable.compareTo(lower) <= 0) break;
            BigDecimal upper = slab.upTo() == null ? taxable : slab.upTo().min(taxable);
            BigDecimal band = upper.subtract(lower);
            if (band.signum() > 0) {
                tax = tax.add(band.multiply(slab.ratePercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP));
            }
            if (slab.upTo() == null) break;
            lower = slab.upTo();
        }
        return tax;
    }

    static BigDecimal roundToTen(BigDecimal value) {
        return value.divide(TEN, 0, RoundingMode.HALF_UP).multiply(TEN).setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
