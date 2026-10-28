package com.haodaone.salary.dto;

import com.haodaone.employee.entity.Employee;
import com.haodaone.salary.entity.EmployeeTaxDeclaration;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tax regime + annual investment declaration for one employee and FY. Used as
 * the PUT body (only regime and amounts are read) and as the response
 * ({@code submitted = false} means nothing has been saved yet and the values
 * are defaults).
 */
public class TaxDeclarationDTO {

    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String panNumber;
    private int financialYear;
    private String financialYearLabel;

    @Pattern(regexp = "NEW|OLD", message = "Regime must be NEW or OLD")
    private String regime = "NEW";
    @DecimalMin(value = "0", message = "Amounts cannot be negative") private BigDecimal section80C = BigDecimal.ZERO;
    @DecimalMin(value = "0", message = "Amounts cannot be negative") private BigDecimal section80D = BigDecimal.ZERO;
    @DecimalMin(value = "0", message = "Amounts cannot be negative") private BigDecimal rentPaid = BigDecimal.ZERO;
    private boolean metroCity;
    @DecimalMin(value = "0", message = "Amounts cannot be negative") private BigDecimal homeLoanInterest = BigDecimal.ZERO;
    @DecimalMin(value = "0", message = "Amounts cannot be negative") private BigDecimal nps80ccd1b = BigDecimal.ZERO;
    @DecimalMin(value = "0", message = "Amounts cannot be negative") private BigDecimal otherDeductions = BigDecimal.ZERO;
    @Size(max = 500) private String otherDeductionsNote;

    private boolean submitted;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public static TaxDeclarationDTO of(Employee employee, int fy, EmployeeTaxDeclaration d) {
        TaxDeclarationDTO dto = new TaxDeclarationDTO();
        dto.employeeId = employee.getId();
        dto.employeeName = employee.getFullName();
        dto.employeeCode = employee.getEmployeeCode();
        dto.panNumber = employee.getPanNumber();
        dto.financialYear = fy;
        dto.financialYearLabel = fyLabel(fy);
        if (d != null) {
            dto.regime = d.getRegime();
            dto.section80C = d.getSection80C();
            dto.section80D = d.getSection80D();
            dto.rentPaid = d.getRentPaid();
            dto.metroCity = d.isMetroCity();
            dto.homeLoanInterest = d.getHomeLoanInterest();
            dto.nps80ccd1b = d.getNps80ccd1b();
            dto.otherDeductions = d.getOtherDeductions();
            dto.otherDeductionsNote = d.getOtherDeductionsNote();
            dto.submitted = true;
            dto.submittedAt = d.getSubmittedAt();
            dto.updatedAt = d.getUpdatedAt();
            dto.updatedBy = d.getUpdatedBy();
        }
        return dto;
    }

    /** "FY 2026-27" for 2026. */
    public static String fyLabel(int fy) {
        return "FY " + fy + "-" + String.format("%02d", (fy + 1) % 100);
    }

    public void applyTo(EmployeeTaxDeclaration d) {
        d.setRegime(regime == null ? "NEW" : regime);
        d.setSection80C(section80C);
        d.setSection80D(section80D);
        d.setRentPaid(rentPaid);
        d.setMetroCity(metroCity);
        d.setHomeLoanInterest(homeLoanInterest);
        d.setNps80ccd1b(nps80ccd1b);
        d.setOtherDeductions(otherDeductions);
        d.setOtherDeductionsNote(otherDeductionsNote);
    }

    public Long getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getEmployeeCode() { return employeeCode; }
    public String getPanNumber() { return panNumber; }
    public int getFinancialYear() { return financialYear; }
    public String getFinancialYearLabel() { return financialYearLabel; }
    public String getRegime() { return regime; }
    public void setRegime(String regime) { this.regime = regime; }
    public BigDecimal getSection80C() { return section80C; }
    public void setSection80C(BigDecimal section80C) { this.section80C = section80C; }
    public BigDecimal getSection80D() { return section80D; }
    public void setSection80D(BigDecimal section80D) { this.section80D = section80D; }
    public BigDecimal getRentPaid() { return rentPaid; }
    public void setRentPaid(BigDecimal rentPaid) { this.rentPaid = rentPaid; }
    public boolean isMetroCity() { return metroCity; }
    public void setMetroCity(boolean metroCity) { this.metroCity = metroCity; }
    public BigDecimal getHomeLoanInterest() { return homeLoanInterest; }
    public void setHomeLoanInterest(BigDecimal homeLoanInterest) { this.homeLoanInterest = homeLoanInterest; }
    public BigDecimal getNps80ccd1b() { return nps80ccd1b; }
    public void setNps80ccd1b(BigDecimal nps80ccd1b) { this.nps80ccd1b = nps80ccd1b; }
    public BigDecimal getOtherDeductions() { return otherDeductions; }
    public void setOtherDeductions(BigDecimal otherDeductions) { this.otherDeductions = otherDeductions; }
    public String getOtherDeductionsNote() { return otherDeductionsNote; }
    public void setOtherDeductionsNote(String otherDeductionsNote) { this.otherDeductionsNote = otherDeductionsNote; }
    public boolean isSubmitted() { return submitted; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
