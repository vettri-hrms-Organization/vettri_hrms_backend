package com.haodaone.salary.dto;

import com.haodaone.salary.entity.SalaryComponents;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Wire shape for {@link SalaryComponents} - used both to submit a new structure and to render one back (or a payroll item's frozen snapshot). */
public class SalaryComponentsDTO {

    @NotNull(message = "Basic salary is required")
    @DecimalMin(value = "0", message = "Basic salary cannot be negative")
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0", message = "HRA cannot be negative")
    private BigDecimal hra = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0", message = "Special allowance cannot be negative")
    private BigDecimal specialAllowance = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0", message = "Medical allowance cannot be negative")
    private BigDecimal medicalAllowance = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0", message = "Travel allowance cannot be negative")
    private BigDecimal travelAllowance = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0", message = "Bonus cannot be negative")
    private BigDecimal bonus = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0", message = "Incentives cannot be negative")
    private BigDecimal incentives = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0", message = "Overtime cannot be negative")
    private BigDecimal overtime = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0", message = "PF cannot be negative")
    private BigDecimal pf = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0", message = "ESI cannot be negative")
    private BigDecimal esi = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0", message = "Professional tax cannot be negative")
    private BigDecimal professionalTax = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0", message = "TDS cannot be negative")
    private BigDecimal tds = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0", message = "Other deductions cannot be negative")
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    public static SalaryComponentsDTO from(SalaryComponents c) {
        SalaryComponentsDTO dto = new SalaryComponentsDTO();
        dto.basicSalary = c.getBasicSalary();
        dto.hra = c.getHra();
        dto.specialAllowance = c.getSpecialAllowance();
        dto.medicalAllowance = c.getMedicalAllowance();
        dto.travelAllowance = c.getTravelAllowance();
        dto.bonus = c.getBonus();
        dto.incentives = c.getIncentives();
        dto.overtime = c.getOvertime();
        dto.pf = c.getPf();
        dto.esi = c.getEsi();
        dto.professionalTax = c.getProfessionalTax();
        dto.tds = c.getTds();
        dto.otherDeductions = c.getOtherDeductions();
        return dto;
    }

    /** Copies this DTO's values onto an entity-owned embeddable. */
    public SalaryComponents toEntity() {
        SalaryComponents c = new SalaryComponents();
        c.setBasicSalary(basicSalary);
        c.setHra(hra);
        c.setSpecialAllowance(specialAllowance);
        c.setMedicalAllowance(medicalAllowance);
        c.setTravelAllowance(travelAllowance);
        c.setBonus(bonus);
        c.setIncentives(incentives);
        c.setOvertime(overtime);
        c.setPf(pf);
        c.setEsi(esi);
        c.setProfessionalTax(professionalTax);
        c.setTds(tds);
        c.setOtherDeductions(otherDeductions);
        return c;
    }

    public BigDecimal getBasicSalary() {
        return basicSalary;
    }

    public void setBasicSalary(BigDecimal basicSalary) {
        this.basicSalary = basicSalary;
    }

    public BigDecimal getHra() {
        return hra;
    }

    public void setHra(BigDecimal hra) {
        this.hra = hra;
    }

    public BigDecimal getSpecialAllowance() {
        return specialAllowance;
    }

    public void setSpecialAllowance(BigDecimal specialAllowance) {
        this.specialAllowance = specialAllowance;
    }

    public BigDecimal getMedicalAllowance() {
        return medicalAllowance;
    }

    public void setMedicalAllowance(BigDecimal medicalAllowance) {
        this.medicalAllowance = medicalAllowance;
    }

    public BigDecimal getTravelAllowance() {
        return travelAllowance;
    }

    public void setTravelAllowance(BigDecimal travelAllowance) {
        this.travelAllowance = travelAllowance;
    }

    public BigDecimal getBonus() {
        return bonus;
    }

    public void setBonus(BigDecimal bonus) {
        this.bonus = bonus;
    }

    public BigDecimal getIncentives() {
        return incentives;
    }

    public void setIncentives(BigDecimal incentives) {
        this.incentives = incentives;
    }

    public BigDecimal getOvertime() {
        return overtime;
    }

    public void setOvertime(BigDecimal overtime) {
        this.overtime = overtime;
    }

    public BigDecimal getPf() {
        return pf;
    }

    public void setPf(BigDecimal pf) {
        this.pf = pf;
    }

    public BigDecimal getEsi() {
        return esi;
    }

    public void setEsi(BigDecimal esi) {
        this.esi = esi;
    }

    public BigDecimal getProfessionalTax() {
        return professionalTax;
    }

    public void setProfessionalTax(BigDecimal professionalTax) {
        this.professionalTax = professionalTax;
    }

    public BigDecimal getTds() {
        return tds;
    }

    public void setTds(BigDecimal tds) {
        this.tds = tds;
    }

    public BigDecimal getOtherDeductions() {
        return otherDeductions;
    }

    public void setOtherDeductions(BigDecimal otherDeductions) {
        this.otherDeductions = otherDeductions;
    }
}
