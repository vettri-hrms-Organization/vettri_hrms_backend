package com.haodaone.salary.lwf;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * A state's Labour Welfare Fund rule for one company (V63). The employee share is a fixed amount, or a percentage
 * of monthly wages with an optional cap; the employer share is a fixed amount or a multiple of the employee share.
 * Deducted only in {@code deductionMonths} ("ALL" or e.g. "6,12"), for wages within the optional band.
 */
@Entity
@Table(name = "lwf_rule")
public class LwfRule extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "state_code", nullable = false, length = 5)
    private String stateCode;

    @Column(name = "state_name", nullable = false, length = 60)
    private String stateName;

    @Column(name = "deduction_months", nullable = false, length = 40)
    private String deductionMonths;

    @Column(name = "employee_fixed", nullable = false, precision = 10, scale = 2)
    private BigDecimal employeeFixed = BigDecimal.ZERO;

    @Column(name = "employee_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal employeePercent = BigDecimal.ZERO;

    @Column(name = "employee_max", precision = 10, scale = 2)
    private BigDecimal employeeMax;

    @Column(name = "employer_fixed", nullable = false, precision = 10, scale = 2)
    private BigDecimal employerFixed = BigDecimal.ZERO;

    @Column(name = "employer_multiplier", nullable = false, precision = 6, scale = 3)
    private BigDecimal employerMultiplier = BigDecimal.ZERO;

    @Column(name = "min_wages", precision = 14, scale = 2)
    private BigDecimal minWages;

    @Column(name = "max_wages", precision = 14, scale = 2)
    private BigDecimal maxWages;

    @Column(nullable = false)
    private boolean active = true;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public String getStateName() { return stateName; }
    public void setStateName(String stateName) { this.stateName = stateName; }
    public String getDeductionMonths() { return deductionMonths; }
    public void setDeductionMonths(String deductionMonths) { this.deductionMonths = deductionMonths; }
    public BigDecimal getEmployeeFixed() { return employeeFixed; }
    public void setEmployeeFixed(BigDecimal employeeFixed) { this.employeeFixed = employeeFixed; }
    public BigDecimal getEmployeePercent() { return employeePercent; }
    public void setEmployeePercent(BigDecimal employeePercent) { this.employeePercent = employeePercent; }
    public BigDecimal getEmployeeMax() { return employeeMax; }
    public void setEmployeeMax(BigDecimal employeeMax) { this.employeeMax = employeeMax; }
    public BigDecimal getEmployerFixed() { return employerFixed; }
    public void setEmployerFixed(BigDecimal employerFixed) { this.employerFixed = employerFixed; }
    public BigDecimal getEmployerMultiplier() { return employerMultiplier; }
    public void setEmployerMultiplier(BigDecimal employerMultiplier) { this.employerMultiplier = employerMultiplier; }
    public BigDecimal getMinWages() { return minWages; }
    public void setMinWages(BigDecimal minWages) { this.minWages = minWages; }
    public BigDecimal getMaxWages() { return maxWages; }
    public void setMaxWages(BigDecimal maxWages) { this.maxWages = maxWages; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
