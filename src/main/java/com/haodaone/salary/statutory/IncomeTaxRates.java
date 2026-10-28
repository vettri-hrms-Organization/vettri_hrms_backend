package com.haodaone.salary.statutory;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Income tax rates for salaried individuals (resident, below 60), keyed by
 * financial year. A new Union Budget is a data change: append a row with
 * the first FY it applies to; {@link #forYear} picks the latest row whose
 * {@code effectiveFromFy} is not after the requested FY, so a year without
 * its own row keeps using the most recent rates.
 *
 * Financial years are identified by their starting calendar year:
 * 2025 means FY 2025-26 (1 Apr 2025 - 31 Mar 2026).
 *
 * Not modelled (documented simplifications): surcharge (income above
 * Rs 50 lakh), senior/super-senior citizen old-regime slabs, and special
 * rate income (capital gains etc.) - none of which appear in a salary TDS
 * projection for the SMB workforce this is built for.
 */
public final class IncomeTaxRates {

    private IncomeTaxRates() {
    }

    public enum Regime { NEW, OLD }

    /** One slab: income up to {@code upTo} (inclusive; null = no upper bound) is taxed at {@code ratePercent}. */
    public record Slab(BigDecimal upTo, BigDecimal ratePercent) {
    }

    /**
     * @param rebateIncomeLimit section 87A applies when taxable income is at or below this
     * @param rebateMax         the most 87A can rebate
     * @param marginalRelief    whether 87A marginal relief applies just above the limit (new regime from FY 2023-24)
     */
    public record RegimeRates(int effectiveFromFy, Regime regime, BigDecimal standardDeduction, List<Slab> slabs,
                              BigDecimal rebateIncomeLimit, BigDecimal rebateMax, boolean marginalRelief,
                              BigDecimal cessPercent, String note) {
    }

    private static BigDecimal r(long v) {
        return BigDecimal.valueOf(v);
    }

    private static Slab slab(Long upTo, String rate) {
        return new Slab(upTo == null ? null : r(upTo), new BigDecimal(rate));
    }

    private static final List<RegimeRates> TABLE = List.of(
            // FY 2024-25 new regime (Finance (No. 2) Act 2024): std deduction 75k, 87A up to 7L.
            new RegimeRates(2024, Regime.NEW, r(75_000), List.of(
                    slab(300_000L, "0"), slab(700_000L, "5"), slab(1_000_000L, "10"),
                    slab(1_200_000L, "15"), slab(1_500_000L, "20"), slab(null, "30")),
                    r(700_000), r(25_000), true, new BigDecimal("4"), "Finance (No. 2) Act 2024"),
            // FY 2024-25 old regime.
            new RegimeRates(2024, Regime.OLD, r(50_000), List.of(
                    slab(250_000L, "0"), slab(500_000L, "5"), slab(1_000_000L, "20"), slab(null, "30")),
                    r(500_000), r(12_500), false, new BigDecimal("4"), "Unchanged since FY 2014-15"),
            // FY 2025-26 new regime (Finance Act 2025): 4L slabs, 87A up to taxable 12L (max 60k) with marginal relief.
            new RegimeRates(2025, Regime.NEW, r(75_000), List.of(
                    slab(400_000L, "0"), slab(800_000L, "5"), slab(1_200_000L, "10"),
                    slab(1_600_000L, "15"), slab(2_000_000L, "20"), slab(2_400_000L, "25"), slab(null, "30")),
                    r(1_200_000), r(60_000), true, new BigDecimal("4"), "Finance Act 2025"),
            // FY 2025-26 old regime: unchanged.
            new RegimeRates(2025, Regime.OLD, r(50_000), List.of(
                    slab(250_000L, "0"), slab(500_000L, "5"), slab(1_000_000L, "20"), slab(null, "30")),
                    r(500_000), r(12_500), false, new BigDecimal("4"), "Finance Act 2025 (no change)")
    );

    /** The rates in force for the given FY (start year) and regime. */
    public static RegimeRates forYear(int fyStartYear, Regime regime) {
        return TABLE.stream()
                .filter(row -> row.regime() == regime && row.effectiveFromFy() <= fyStartYear)
                .max(Comparator.comparingInt(RegimeRates::effectiveFromFy))
                .orElseGet(() -> TABLE.stream().filter(row -> row.regime() == regime)
                        .min(Comparator.comparingInt(RegimeRates::effectiveFromFy)).orElseThrow());
    }
}
