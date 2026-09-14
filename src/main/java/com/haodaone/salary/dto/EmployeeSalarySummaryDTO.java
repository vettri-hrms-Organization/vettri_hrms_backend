package com.haodaone.salary.dto;

import com.haodaone.employee.entity.Employee;
import com.haodaone.salary.entity.DerivedPayrollStatus;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollItemStatus;
import com.haodaone.salary.entity.SalaryStructure;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of the Employee Salary List. */
public class EmployeeSalarySummaryDTO {

    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private String profilePhotoUrl;
    private String departmentName;
    private String designationTitle;
    private String employmentStatus;
    private boolean structureConfigured;
    private BigDecimal basicSalary;
    private BigDecimal grossSalary;
    private BigDecimal netSalary;
    private String payrollStatus;
    private LocalDate lastPayrollDate;

    public static EmployeeSalarySummaryDTO build(Employee employee, SalaryStructure structure, PayrollItem latestItem) {
        EmployeeSalarySummaryDTO dto = new EmployeeSalarySummaryDTO();
        dto.employeeId = employee.getId();
        dto.employeeCode = employee.getEmployeeCode();
        dto.employeeName = employee.getFullName();
        dto.profilePhotoUrl = employee.getProfilePhotoUrl();
        dto.departmentName = employee.getDepartment() != null ? employee.getDepartment().getName() : null;
        dto.designationTitle = employee.getDesignation() != null ? employee.getDesignation().getTitle() : null;
        dto.employmentStatus = employee.getStatus();

        dto.structureConfigured = structure != null;
        dto.basicSalary = structure != null ? structure.getComponents().getBasicSalary() : BigDecimal.ZERO;
        dto.grossSalary = structure != null ? structure.getGrossSalary() : BigDecimal.ZERO;
        dto.netSalary = structure != null ? structure.getNetSalary() : BigDecimal.ZERO;

        if (latestItem != null) {
            dto.payrollStatus = latestItem.getStatus();
            dto.lastPayrollDate = PayrollItemStatus.PAID.equals(latestItem.getStatus()) ? latestItem.getPaymentDate() : null;
        } else {
            dto.payrollStatus = structure != null ? DerivedPayrollStatus.READY : DerivedPayrollStatus.NOT_CONFIGURED;
            dto.lastPayrollDate = null;
        }
        return dto;
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

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getDesignationTitle() {
        return designationTitle;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public boolean isStructureConfigured() {
        return structureConfigured;
    }

    public BigDecimal getBasicSalary() {
        return basicSalary;
    }

    public BigDecimal getGrossSalary() {
        return grossSalary;
    }

    public BigDecimal getNetSalary() {
        return netSalary;
    }

    public String getPayrollStatus() {
        return payrollStatus;
    }

    public LocalDate getLastPayrollDate() {
        return lastPayrollDate;
    }
}
