package com.haodaone.salary.dto;

import com.haodaone.salary.statutory.IncomeTaxCalculator.TaxResult;

import java.math.BigDecimal;

/**
 * Side-by-side annual tax estimate in both regimes for an employee and FY,
 * from salary already paid this FY plus the current structure for the rest.
 * {@code estimatedMonthlyTds} = (tax in the selected regime - TDS so far) /
 * months still to be paid.
 */
public record TaxProjectionDTO(int financialYear, String financialYearLabel, String selectedRegime,
                               boolean hasSalaryStructure, int monthsPaid, int monthsProjected,
                               BigDecimal tdsDeductedSoFar, BigDecimal estimatedMonthlyTds,
                               TaxResult newRegime, TaxResult oldRegime, String recommendedRegime,
                               com.haodaone.salary.service.OpeningYtdProvider.OpeningYtd openingYtd) {

    /** Without imported opening YTD figures (openingYtd = null). tdsDeductedSoFar already includes opening TDS when present. */
    public TaxProjectionDTO(int financialYear, String financialYearLabel, String selectedRegime,
                            boolean hasSalaryStructure, int monthsPaid, int monthsProjected,
                            BigDecimal tdsDeductedSoFar, BigDecimal estimatedMonthlyTds,
                            TaxResult newRegime, TaxResult oldRegime, String recommendedRegime) {
        this(financialYear, financialYearLabel, selectedRegime, hasSalaryStructure, monthsPaid, monthsProjected,
                tdsDeductedSoFar, estimatedMonthlyTds, newRegime, oldRegime, recommendedRegime, null);
    }
}
