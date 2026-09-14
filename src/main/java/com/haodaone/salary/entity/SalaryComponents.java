package com.haodaone.salary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * The earning/deduction line items common to both {@link SalaryStructure}
 * (an employee's current/historical pay definition) and {@link PayrollItem}
 * (a frozen snapshot of those same numbers at the moment a payroll run
 * processed them). Embedding the same shape in both means a later change to
 * an employee's structure never rewrites history sitting in an already-run
 * payroll - exactly the same "snapshot, don't reference-and-hope" principle
 * the offer letter workflow uses for candidate data captured at send time.
 *
 * All amounts are monthly figures in the org's base currency; this module
 * does not do multi-currency conversion.
 */
@Embeddable
public class SalaryComponents {

    @Column(name = "basic_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "hra", nullable = false, precision = 14, scale = 2)
    private BigDecimal hra = BigDecimal.ZERO;

    @Column(name = "special_allowance", nullable = false, precision = 14, scale = 2)
    private BigDecimal specialAllowance = BigDecimal.ZERO;

    @Column(name = "medical_allowance", nullable = false, precision = 14, scale = 2)
    private BigDecimal medicalAllowance = BigDecimal.ZERO;

    @Column(name = "travel_allowance", nullable = false, precision = 14, scale = 2)
    private BigDecimal travelAllowance = BigDecimal.ZERO;

    @Column(name = "bonus", nullable = false, precision = 14, scale = 2)
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(name = "incentives", nullable = false, precision = 14, scale = 2)
    private BigDecimal incentives = BigDecimal.ZERO;

    @Column(name = "overtime", nullable = false, precision = 14, scale = 2)
    private BigDecimal overtime = BigDecimal.ZERO;

    @Column(name = "pf", nullable = false, precision = 14, scale = 2)
    private BigDecimal pf = BigDecimal.ZERO;

    @Column(name = "esi", nullable = false, precision = 14, scale = 2)
    private BigDecimal esi = BigDecimal.ZERO;

    @Column(name = "professional_tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal professionalTax = BigDecimal.ZERO;

    @Column(name = "tds", nullable = false, precision = 14, scale = 2)
    private BigDecimal tds = BigDecimal.ZERO;

    @Column(name = "other_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    /** Sum of every earning component. This is "Gross Salary". */
    public BigDecimal grossSalary() {
        return nz(basicSalary).add(nz(hra)).add(nz(specialAllowance)).add(nz(medicalAllowance))
                .add(nz(travelAllowance)).add(nz(bonus)).add(nz(incentives)).add(nz(overtime));
    }

    /** Sum of every deduction component. */
    public BigDecimal totalDeductions() {
        return nz(pf).add(nz(esi)).add(nz(professionalTax)).add(nz(tds)).add(nz(otherDeductions));
    }

    /** Gross Salary minus Total Deductions. */
    public BigDecimal netSalary() {
        return grossSalary().subtract(totalDeductions());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public BigDecimal getBasicSalary() {
        return basicSalary;
    }

    public void setBasicSalary(BigDecimal basicSalary) {
        this.basicSalary = nz(basicSalary);
    }

    public BigDecimal getHra() {
        return hra;
    }

    public void setHra(BigDecimal hra) {
        this.hra = nz(hra);
    }

    public BigDecimal getSpecialAllowance() {
        return specialAllowance;
    }

    public void setSpecialAllowance(BigDecimal specialAllowance) {
        this.specialAllowance = nz(specialAllowance);
    }

    public BigDecimal getMedicalAllowance() {
        return medicalAllowance;
    }

    public void setMedicalAllowance(BigDecimal medicalAllowance) {
        this.medicalAllowance = nz(medicalAllowance);
    }

    public BigDecimal getTravelAllowance() {
        return travelAllowance;
    }

    public void setTravelAllowance(BigDecimal travelAllowance) {
        this.travelAllowance = nz(travelAllowance);
    }

    public BigDecimal getBonus() {
        return bonus;
    }

    public void setBonus(BigDecimal bonus) {
        this.bonus = nz(bonus);
    }

    public BigDecimal getIncentives() {
        return incentives;
    }

    public void setIncentives(BigDecimal incentives) {
        this.incentives = nz(incentives);
    }

    public BigDecimal getOvertime() {
        return overtime;
    }

    public void setOvertime(BigDecimal overtime) {
        this.overtime = nz(overtime);
    }

    public BigDecimal getPf() {
        return pf;
    }

    public void setPf(BigDecimal pf) {
        this.pf = nz(pf);
    }

    public BigDecimal getEsi() {
        return esi;
    }

    public void setEsi(BigDecimal esi) {
        this.esi = nz(esi);
    }

    public BigDecimal getProfessionalTax() {
        return professionalTax;
    }

    public void setProfessionalTax(BigDecimal professionalTax) {
        this.professionalTax = nz(professionalTax);
    }

    public BigDecimal getTds() {
        return tds;
    }

    public void setTds(BigDecimal tds) {
        this.tds = nz(tds);
    }

    public BigDecimal getOtherDeductions() {
        return otherDeductions;
    }

    public void setOtherDeductions(BigDecimal otherDeductions) {
        this.otherDeductions = nz(otherDeductions);
    }
}
