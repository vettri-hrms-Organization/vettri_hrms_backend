package com.haodaone.salary;

import com.haodaone.salary.statutory.IncomeTaxCalculator;
import com.haodaone.salary.statutory.IncomeTaxCalculator.TaxInput;
import com.haodaone.salary.statutory.IncomeTaxCalculator.TaxResult;
import com.haodaone.salary.statutory.IncomeTaxRates;
import com.haodaone.salary.statutory.IncomeTaxRates.Regime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Annual income tax for FY 2025-26 (and the dated-table lookup) - pure, no Spring. */
class IncomeTaxCalculatorTest {

    private static TaxResult simple(int fy, Regime regime, long gross) {
        return IncomeTaxCalculator.compute(TaxInput.simple(fy, regime, BigDecimal.valueOf(gross)));
    }

    private static void assertAmount(long expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @ParameterizedTest(name = "NEW FY25 gross {0} -> taxable {1}, tax {2}")
    @CsvSource({
            // gross, taxable, total tax incl. cess
            "0,        0,       0",
            "500000,   425000,  0",        // 1,250 slab tax fully rebated
            "1275000,  1200000, 0",        // exactly at the 87A limit: 60,000 rebated
            "1275004,  1200000, 0",        // s.288A rounds taxable to the nearest 10
            "1285000,  1210000, 10400",    // marginal relief: tax limited to 10,000 above 12L, + 4% cess
            "1345000,  1270000, 72800",    // marginal relief: 70,500 limited to 70,000
            "1375000,  1300000, 78000",    // above break-even: 75,000 + cess, no relief
            "2075000,  2000000, 208000",   // 2,00,000 + cess
            "3075000,  3000000, 499200",   // 4,80,000 + cess (30% band)
    })
    void newRegimeFy2025(long gross, long taxable, long tax) {
        TaxResult result = simple(2025, Regime.NEW, gross);
        assertAmount(taxable, result.taxableIncome());
        assertAmount(tax, result.totalTax());
    }

    @Test
    void rebateEdgeAtTwelveLakhAndMarginalReliefBreakdown() {
        TaxResult atLimit = simple(2025, Regime.NEW, 1_275_000);
        assertAmount(60_000, atLimit.taxOnIncome());
        assertAmount(60_000, atLimit.rebate87A());
        assertAmount(0, atLimit.cess());

        TaxResult justAbove = simple(2025, Regime.NEW, 1_285_000);
        assertAmount(61_500, justAbove.taxOnIncome());
        assertAmount(51_500, justAbove.rebate87A());
        assertAmount(10_000, justAbove.taxAfterRebate());
        assertAmount(400, justAbove.cess());
    }

    @ParameterizedTest(name = "OLD FY25 gross {0} -> tax {1}")
    @CsvSource({
            "550000,  0",       // taxable 5L: 12,500 fully rebated u/s 87A
            "1050000, 117000",  // taxable 10L: 1,12,500 + cess
            "1550000, 273000",  // taxable 15L: 2,62,500 + cess
    })
    void oldRegimeWithoutDeclarations(long gross, long tax) {
        assertAmount(tax, simple(2025, Regime.OLD, gross).totalTax());
    }

    @Test
    void oldRegimeAppliesHraExemptionPtAndCappedChapterVIA() {
        TaxInput input = new TaxInput(2025, Regime.OLD, bd(1_200_000), bd(600_000), bd(300_000), bd(240_000), true,
                bd(2_400), bd(200_000), bd(25_000), null, null, null);
        TaxResult result = IncomeTaxCalculator.compute(input);

        assertAmount(50_000, result.standardDeduction());
        assertAmount(180_000, result.hraExemption());       // rent 2.4L - 10% of basic
        assertAmount(150_000, result.section80C());          // capped at 1.5L
        assertAmount(175_000, result.chapterVIATotal());
        assertAmount(792_600, result.taxableIncome());
        assertAmount(71_020, result.taxOnIncome());
        assertAmount(73_860, result.totalTax());             // 73,860.80 rounded u/s 288B
    }

    @Test
    void oldRegimeCapsHomeLoanInterestAndNps() {
        TaxInput input = new TaxInput(2025, Regime.OLD, bd(1_050_000), null, null, null, false,
                null, null, null, bd(300_000), bd(60_000), null);
        TaxResult result = IncomeTaxCalculator.compute(input);

        assertAmount(200_000, result.homeLoanInterest());
        assertAmount(50_000, result.nps80ccd1b());
        assertAmount(750_000, result.taxableIncome());
        assertAmount(65_000, result.totalTax());
    }

    @Test
    void newRegimeIgnoresOldRegimeDeductions() {
        TaxInput withDeclarations = new TaxInput(2025, Regime.NEW, bd(1_800_000), bd(900_000), bd(450_000), bd(400_000), true,
                bd(2_500), bd(150_000), bd(25_000), bd(200_000), bd(50_000), bd(10_000));
        TaxResult result = IncomeTaxCalculator.compute(withDeclarations);

        assertAmount(0, result.hraExemption());
        assertAmount(0, result.chapterVIATotal());
        assertAmount(1_725_000, result.taxableIncome());
        assertAmount(150_800, result.totalTax());
        assertEquals(simple(2025, Regime.NEW, 1_800_000).totalTax(), result.totalTax());
    }

    @Test
    void hraExemptionIsTheLeastOfTheThreeLimits() {
        assertAmount(240_000, IncomeTaxCalculator.hraExemption(bd(600_000), bd(300_000), bd(300_000), false));
        assertAmount(100_000, IncomeTaxCalculator.hraExemption(bd(600_000), bd(100_000), bd(500_000), true));
        assertAmount(0, IncomeTaxCalculator.hraExemption(bd(600_000), bd(300_000), bd(50_000), true));
        assertAmount(0, IncomeTaxCalculator.hraExemption(bd(600_000), bd(300_000), BigDecimal.ZERO, true));
    }

    @Test
    void ratesAreLookedUpByFinancialYear() {
        // FY 2024-25 new regime: 87A up to 7L with marginal relief.
        assertAmount(0, simple(2024, Regime.NEW, 775_000).totalTax());
        assertAmount(10_400, simple(2024, Regime.NEW, 785_000).totalTax());
        assertAmount(52_000, simple(2024, Regime.NEW, 1_075_000).totalTax());
        // FY 2026-27 has no row of its own yet: the FY 2025-26 rates carry forward.
        assertEquals(2025, IncomeTaxRates.forYear(2026, Regime.NEW).effectiveFromFy());
        assertAmount(0, simple(2026, Regime.NEW, 1_275_000).totalTax());
        // A year before the table starts falls back to the earliest row.
        assertEquals(2024, IncomeTaxRates.forYear(2019, Regime.OLD).effectiveFromFy());
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }
}
