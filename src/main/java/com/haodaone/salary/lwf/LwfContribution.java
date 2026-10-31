package com.haodaone.salary.lwf;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

/** An LWF contribution actually deducted in a processed payroll (V63), with the employer share for the return. */
@Entity
@Table(name = "lwf_contribution")
public class LwfContribution extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "payroll_item_id", nullable = false)
    private Long payrollItemId;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "state_code", nullable = false, length = 5)
    private String stateCode;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal wages;

    @Column(name = "employee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal employeeAmount;

    @Column(name = "employer_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal employerAmount;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public Long getPayrollItemId() { return payrollItemId; }
    public void setPayrollItemId(Long payrollItemId) { this.payrollItemId = payrollItemId; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public BigDecimal getWages() { return wages; }
    public void setWages(BigDecimal wages) { this.wages = wages; }
    public BigDecimal getEmployeeAmount() { return employeeAmount; }
    public void setEmployeeAmount(BigDecimal employeeAmount) { this.employeeAmount = employeeAmount; }
    public BigDecimal getEmployerAmount() { return employerAmount; }
    public void setEmployerAmount(BigDecimal employerAmount) { this.employerAmount = employerAmount; }
}
