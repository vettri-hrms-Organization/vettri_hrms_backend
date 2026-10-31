package com.haodaone.salary.dto;

import com.haodaone.salary.entity.PayrollItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

public class PayrollItemDTO {

    private Long id;
    private Long payrollRunId;
    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private String departmentName;
    private String designationTitle;
    private SalaryComponentsDTO components;
    private BigDecimal grossSalary;
    private BigDecimal totalDeductions;
    private BigDecimal netSalary;
    private String status;
    private LocalDate paymentDate;
    private String remarks;
    private BigDecimal lopDays;
    private BigDecimal paidDays;
    private int daysInPeriod;
    private int periodMonth;
    private int periodYear;
    private String periodLabel;

    public static PayrollItemDTO from(PayrollItem i) {
        PayrollItemDTO dto = new PayrollItemDTO();
        dto.id = i.getId();
        dto.payrollRunId = i.getPayrollRun().getId();
        dto.employeeId = i.getEmployee().getId();
        dto.employeeCode = i.getEmployee().getEmployeeCode();
        dto.employeeName = i.getEmployee().getFullName();
        dto.departmentName = i.getEmployee().getDepartment() != null ? i.getEmployee().getDepartment().getName() : null;
        dto.designationTitle = i.getEmployee().getDesignation() != null ? i.getEmployee().getDesignation().getTitle() : null;
        dto.components = SalaryComponentsDTO.from(i.getComponents());
        dto.grossSalary = i.getGrossSalary();
        dto.totalDeductions = i.getTotalDeductions();
        dto.netSalary = i.getNetSalary();
        dto.status = i.getStatus();
        dto.paymentDate = i.getPaymentDate();
        dto.remarks = i.getRemarks();
        dto.periodMonth = i.getPayrollRun().getPeriodMonth();
        dto.periodYear = i.getPayrollRun().getPeriodYear();
        YearMonth period = YearMonth.of(dto.periodYear, dto.periodMonth);
        dto.periodLabel = period.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + dto.periodYear;
        dto.daysInPeriod = period.lengthOfMonth();
        dto.lopDays = i.getLopDays();
        // Pre-V40 items were never prorated: they were paid for the whole month.
        dto.paidDays = i.getPaidDays() != null ? i.getPaidDays() : BigDecimal.valueOf(dto.daysInPeriod);
        dto.statutoryAuto = i.isStatutoryAuto();
        dto.pfWages = i.getPfWages();
        dto.employerEpf = i.getEmployerEpf();
        dto.employerEps = i.getEmployerEps();
        dto.employerEsi = i.getEmployerEsi();
        dto.employerContributions = i.employerContributionsTotal();
        dto.ptState = i.getPtState();
        dto.adjustmentsTotal = i.getAdjustmentsTotal();
        dto.taxableGross = i.effectiveTaxableGross();
        dto.adjustments = i.getAdjustments().stream()
                .filter(a -> !a.isDeleted())
                .map(a -> new AdjustmentLine(a.getId(), a.getCode(), a.getLabel(), a.getAmount(), a.isTaxable(),
                        a.getSourceType(), a.getSourceId(), a.isSettled()))
                .toList();
        dto.deductionLines = i.getDeductionLines().stream()
                .filter(d -> !d.isDeleted())
                .map(d -> new DeductionLine(d.getId(), d.getCode(), d.getLabel(), d.getAmount(), d.getSourceType(), d.getSourceId()))
                .toList();
        return dto;
    }

    /** An extra earnings line (overtime, encashment, reimbursement...) on this item. */
    public record AdjustmentLine(Long id, String code, String label, BigDecimal amount, boolean taxable,
                                 String sourceType, Long sourceId, boolean settled) {
    }

    /** A post-tax deduction line (loan EMI, advance recovery) on this item. */
    public record DeductionLine(Long id, String code, String label, BigDecimal amount, String sourceType, Long sourceId) {
    }

    private java.util.List<DeductionLine> deductionLines = java.util.List.of();
    public java.util.List<DeductionLine> getDeductionLines() { return deductionLines; }

    private boolean statutoryAuto;
    private BigDecimal pfWages;
    private BigDecimal employerEpf;
    private BigDecimal employerEps;
    private BigDecimal employerEsi;
    private BigDecimal employerContributions;
    private String ptState;
    private BigDecimal adjustmentsTotal;
    private BigDecimal taxableGross;
    private java.util.List<AdjustmentLine> adjustments = java.util.List.of();

    public boolean isStatutoryAuto() { return statutoryAuto; }
    public BigDecimal getPfWages() { return pfWages; }
    public BigDecimal getEmployerEpf() { return employerEpf; }
    public BigDecimal getEmployerEps() { return employerEps; }
    public BigDecimal getEmployerEsi() { return employerEsi; }
    public BigDecimal getEmployerContributions() { return employerContributions; }
    public String getPtState() { return ptState; }
    public BigDecimal getAdjustmentsTotal() { return adjustmentsTotal; }
    public BigDecimal getTaxableGross() { return taxableGross; }
    public java.util.List<AdjustmentLine> getAdjustments() { return adjustments; }

    public BigDecimal getLopDays() { return lopDays; }
    public BigDecimal getPaidDays() { return paidDays; }
    public int getDaysInPeriod() { return daysInPeriod; }
    public int getPeriodMonth() { return periodMonth; }
    public int getPeriodYear() { return periodYear; }
    public String getPeriodLabel() { return periodLabel; }

    public Long getId() {
        return id;
    }

    public Long getPayrollRunId() {
        return payrollRunId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getDesignationTitle() {
        return designationTitle;
    }

    public SalaryComponentsDTO getComponents() {
        return components;
    }

    public BigDecimal getGrossSalary() {
        return grossSalary;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public BigDecimal getNetSalary() {
        return netSalary;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public String getRemarks() {
        return remarks;
    }
}
