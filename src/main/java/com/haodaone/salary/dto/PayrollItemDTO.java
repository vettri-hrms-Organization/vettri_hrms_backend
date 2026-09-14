package com.haodaone.salary.dto;

import com.haodaone.salary.entity.PayrollItem;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        return dto;
    }

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
