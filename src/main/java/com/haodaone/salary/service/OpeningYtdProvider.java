package com.haodaone.salary.service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Opening year-to-date payroll figures for an employee whose salary was paid outside Vettri for part of a
 * financial year (company switched payroll mid-year, or salary from a previous employer declared via Form 12B).
 * Implemented by the data-import module (com.haodaone.imports.service.OpeningYtdService); optional, so the
 * statutory engine and Form 16 behave exactly as before when no bean / no row exists.
 */
public interface OpeningYtdProvider {

    /** All amounts are FY totals already paid/deducted before Vettri payroll took over. */
    record OpeningYtd(BigDecimal grossSalary, BigDecimal taxableSalary, BigDecimal pfEmployee,
                      BigDecimal professionalTax, BigDecimal tdsDeducted, int monthsCovered,
                      BigDecimal previousEmployerIncome, BigDecimal previousEmployerTds) {

        /** Taxable salary paid by this employer before go-live (falls back to gross when taxable was not given). */
        public BigDecimal employerTaxable() {
            return taxableSalary != null ? taxableSalary : nz(grossSalary);
        }

        /** Income that must be added to the FY's taxable salary: this employer's opening taxable + previous employer income. */
        public BigDecimal totalTaxable() {
            return employerTaxable().add(nz(previousEmployerIncome));
        }

        /** TDS already deducted this FY outside Vettri (this employer before go-live + previous employer). */
        public BigDecimal totalTds() {
            return nz(tdsDeducted).add(nz(previousEmployerTds));
        }

        private static BigDecimal nz(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    Optional<OpeningYtd> openingFor(Long companyId, Long employeeId, int financialYear);
}
