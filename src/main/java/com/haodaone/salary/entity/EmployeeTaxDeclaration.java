package com.haodaone.salary.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An employee's tax regime choice and annual investment declaration for one
 * financial year ({@link #financialYear} = starting calendar year, 2026 =
 * FY 2026-27). All amounts are annual. Old-regime fields are kept even when
 * the employee picks NEW, so switching back doesn't lose what they typed.
 */
@Entity
@Table(name = "employee_tax_declaration",
        uniqueConstraints = @UniqueConstraint(name = "uk_tax_declaration_employee_fy", columnNames = {"employee_id", "financial_year"}))
public class EmployeeTaxDeclaration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "financial_year", nullable = false)
    private int financialYear;

    /** NEW (default) or OLD. */
    @Column(nullable = false, length = 10)
    private String regime = "NEW";

    @Column(name = "section_80c", nullable = false, precision = 14, scale = 2)
    private BigDecimal section80C = BigDecimal.ZERO;

    @Column(name = "section_80d", nullable = false, precision = 14, scale = 2)
    private BigDecimal section80D = BigDecimal.ZERO;

    /** Annual rent paid, for the HRA exemption. */
    @Column(name = "rent_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal rentPaid = BigDecimal.ZERO;

    @Column(name = "metro_city", nullable = false)
    private boolean metroCity = false;

    @Column(name = "home_loan_interest", nullable = false, precision = 14, scale = 2)
    private BigDecimal homeLoanInterest = BigDecimal.ZERO;

    @Column(name = "nps_80ccd_1b", nullable = false, precision = 14, scale = 2)
    private BigDecimal nps80ccd1b = BigDecimal.ZERO;

    @Column(name = "other_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    @Column(name = "other_deductions_note", length = 500)
    private String otherDeductionsNote;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public int getFinancialYear() { return financialYear; }
    public void setFinancialYear(int financialYear) { this.financialYear = financialYear; }
    public String getRegime() { return regime; }
    public void setRegime(String regime) { this.regime = regime; }
    public BigDecimal getSection80C() { return section80C; }
    public void setSection80C(BigDecimal section80C) { this.section80C = nz(section80C); }
    public BigDecimal getSection80D() { return section80D; }
    public void setSection80D(BigDecimal section80D) { this.section80D = nz(section80D); }
    public BigDecimal getRentPaid() { return rentPaid; }
    public void setRentPaid(BigDecimal rentPaid) { this.rentPaid = nz(rentPaid); }
    public boolean isMetroCity() { return metroCity; }
    public void setMetroCity(boolean metroCity) { this.metroCity = metroCity; }
    public BigDecimal getHomeLoanInterest() { return homeLoanInterest; }
    public void setHomeLoanInterest(BigDecimal homeLoanInterest) { this.homeLoanInterest = nz(homeLoanInterest); }
    public BigDecimal getNps80ccd1b() { return nps80ccd1b; }
    public void setNps80ccd1b(BigDecimal nps80ccd1b) { this.nps80ccd1b = nz(nps80ccd1b); }
    public BigDecimal getOtherDeductions() { return otherDeductions; }
    public void setOtherDeductions(BigDecimal otherDeductions) { this.otherDeductions = nz(otherDeductions); }
    public String getOtherDeductionsNote() { return otherDeductionsNote; }
    public void setOtherDeductionsNote(String otherDeductionsNote) { this.otherDeductionsNote = otherDeductionsNote; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
