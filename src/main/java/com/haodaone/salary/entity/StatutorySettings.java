package com.haodaone.salary.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * One row per company: how PF, ESI, professional tax and TDS are
 * auto-calculated for salary structures that opt in
 * ({@link SalaryStructure#isAutoStatutory()}). A company with no row uses
 * the defaults below (the statutory rates in force), so auto-calculation
 * works out of the box. Rates are percentages (12.00 = 12%).
 */
@Entity
@Table(name = "payroll_statutory_settings")
public class StatutorySettings extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(name = "pf_enabled", nullable = false)
    private boolean pfEnabled = true;

    @Column(name = "pf_employee_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal pfEmployeeRate = new BigDecimal("12.00");

    /** Total employer share; EPS takes {@link #epsRate} of the (capped) wage and EPF gets the remainder. */
    @Column(name = "pf_employer_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal pfEmployerRate = new BigDecimal("12.00");

    @Column(name = "eps_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal epsRate = new BigDecimal("8.33");

    @Column(name = "pf_wage_ceiling", nullable = false, precision = 14, scale = 2)
    private BigDecimal pfWageCeiling = new BigDecimal("15000.00");

    /** When true, EE/ER PF is computed on the full basic instead of min(basic, ceiling). EPS/EDLI stay capped. */
    @Column(name = "pf_on_full_basic", nullable = false)
    private boolean pfOnFullBasic = false;

    @Column(name = "esi_enabled", nullable = false)
    private boolean esiEnabled = true;

    @Column(name = "esi_employee_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal esiEmployeeRate = new BigDecimal("0.75");

    @Column(name = "esi_employer_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal esiEmployerRate = new BigDecimal("3.25");

    @Column(name = "esi_wage_threshold", nullable = false, precision = 14, scale = 2)
    private BigDecimal esiWageThreshold = new BigDecimal("21000.00");

    @Column(name = "pt_enabled", nullable = false)
    private boolean ptEnabled = true;

    /** ProfessionalTaxSlabs code (KA, MH, TN, ... NONE). Null = derive from the first active office location's state. */
    @Column(name = "pt_state", length = 10)
    private String ptState;

    /** When true every auto-calculated employee pays {@link #ptFixedAmount} per month instead of the state slab. */
    @Column(name = "pt_fixed_override", nullable = false)
    private boolean ptFixedOverride = false;

    @Column(name = "pt_fixed_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal ptFixedAmount = BigDecimal.ZERO;

    @Column(name = "tds_enabled", nullable = false)
    private boolean tdsEnabled = true;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public boolean isPfEnabled() { return pfEnabled; }
    public void setPfEnabled(boolean pfEnabled) { this.pfEnabled = pfEnabled; }
    public BigDecimal getPfEmployeeRate() { return pfEmployeeRate; }
    public void setPfEmployeeRate(BigDecimal pfEmployeeRate) { this.pfEmployeeRate = pfEmployeeRate; }
    public BigDecimal getPfEmployerRate() { return pfEmployerRate; }
    public void setPfEmployerRate(BigDecimal pfEmployerRate) { this.pfEmployerRate = pfEmployerRate; }
    public BigDecimal getEpsRate() { return epsRate; }
    public void setEpsRate(BigDecimal epsRate) { this.epsRate = epsRate; }
    public BigDecimal getPfWageCeiling() { return pfWageCeiling; }
    public void setPfWageCeiling(BigDecimal pfWageCeiling) { this.pfWageCeiling = pfWageCeiling; }
    public boolean isPfOnFullBasic() { return pfOnFullBasic; }
    public void setPfOnFullBasic(boolean pfOnFullBasic) { this.pfOnFullBasic = pfOnFullBasic; }
    public boolean isEsiEnabled() { return esiEnabled; }
    public void setEsiEnabled(boolean esiEnabled) { this.esiEnabled = esiEnabled; }
    public BigDecimal getEsiEmployeeRate() { return esiEmployeeRate; }
    public void setEsiEmployeeRate(BigDecimal esiEmployeeRate) { this.esiEmployeeRate = esiEmployeeRate; }
    public BigDecimal getEsiEmployerRate() { return esiEmployerRate; }
    public void setEsiEmployerRate(BigDecimal esiEmployerRate) { this.esiEmployerRate = esiEmployerRate; }
    public BigDecimal getEsiWageThreshold() { return esiWageThreshold; }
    public void setEsiWageThreshold(BigDecimal esiWageThreshold) { this.esiWageThreshold = esiWageThreshold; }
    public boolean isPtEnabled() { return ptEnabled; }
    public void setPtEnabled(boolean ptEnabled) { this.ptEnabled = ptEnabled; }
    public String getPtState() { return ptState; }
    public void setPtState(String ptState) { this.ptState = ptState; }
    public boolean isPtFixedOverride() { return ptFixedOverride; }
    public void setPtFixedOverride(boolean ptFixedOverride) { this.ptFixedOverride = ptFixedOverride; }
    public BigDecimal getPtFixedAmount() { return ptFixedAmount; }
    public void setPtFixedAmount(BigDecimal ptFixedAmount) { this.ptFixedAmount = ptFixedAmount; }
    public boolean isTdsEnabled() { return tdsEnabled; }
    public void setTdsEnabled(boolean tdsEnabled) { this.tdsEnabled = tdsEnabled; }
}
