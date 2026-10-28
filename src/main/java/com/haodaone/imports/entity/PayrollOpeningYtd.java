package com.haodaone.imports.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Payroll figures already paid in a financial year before the company moved its payroll to Vettri (and,
 * optionally, previous-employer income/TDS declared via Form 12B). One live row per employee per FY (V51);
 * feeds the TDS projection and Form 16 through OpeningYtdService.
 */
@Entity
@Table(name = "payroll_opening_ytd")
public class PayrollOpeningYtd extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private DataImportBatch batch;

    /** FY start year: 2026 = April 2026 - March 2027. */
    @Column(name = "financial_year", nullable = false)
    private int financialYear;

    @Column(name = "gross_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossSalary = BigDecimal.ZERO;

    @Column(name = "taxable_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableSalary = BigDecimal.ZERO;

    @Column(name = "pf_employee", nullable = false, precision = 14, scale = 2)
    private BigDecimal pfEmployee = BigDecimal.ZERO;

    @Column(name = "professional_tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal professionalTax = BigDecimal.ZERO;

    @Column(name = "tds_deducted", nullable = false, precision = 14, scale = 2)
    private BigDecimal tdsDeducted = BigDecimal.ZERO;

    @Column(name = "months_covered", nullable = false)
    private int monthsCovered;

    @Column(name = "previous_employer_income", nullable = false, precision = 14, scale = 2)
    private BigDecimal previousEmployerIncome = BigDecimal.ZERO;

    @Column(name = "previous_employer_tds", nullable = false, precision = 14, scale = 2)
    private BigDecimal previousEmployerTds = BigDecimal.ZERO;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public DataImportBatch getBatch() { return batch; }
    public void setBatch(DataImportBatch batch) { this.batch = batch; }
    public int getFinancialYear() { return financialYear; }
    public void setFinancialYear(int financialYear) { this.financialYear = financialYear; }
    public BigDecimal getGrossSalary() { return grossSalary; }
    public void setGrossSalary(BigDecimal grossSalary) { this.grossSalary = nz(grossSalary); }
    public BigDecimal getTaxableSalary() { return taxableSalary; }
    public void setTaxableSalary(BigDecimal taxableSalary) { this.taxableSalary = nz(taxableSalary); }
    public BigDecimal getPfEmployee() { return pfEmployee; }
    public void setPfEmployee(BigDecimal pfEmployee) { this.pfEmployee = nz(pfEmployee); }
    public BigDecimal getProfessionalTax() { return professionalTax; }
    public void setProfessionalTax(BigDecimal professionalTax) { this.professionalTax = nz(professionalTax); }
    public BigDecimal getTdsDeducted() { return tdsDeducted; }
    public void setTdsDeducted(BigDecimal tdsDeducted) { this.tdsDeducted = nz(tdsDeducted); }
    public int getMonthsCovered() { return monthsCovered; }
    public void setMonthsCovered(int monthsCovered) { this.monthsCovered = monthsCovered; }
    public BigDecimal getPreviousEmployerIncome() { return previousEmployerIncome; }
    public void setPreviousEmployerIncome(BigDecimal previousEmployerIncome) { this.previousEmployerIncome = nz(previousEmployerIncome); }
    public BigDecimal getPreviousEmployerTds() { return previousEmployerTds; }
    public void setPreviousEmployerTds(BigDecimal previousEmployerTds) { this.previousEmployerTds = nz(previousEmployerTds); }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
