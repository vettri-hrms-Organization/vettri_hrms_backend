package com.haodaone.leave.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/** e.g. "Casual Leave", "Sick Leave", "Earned Leave". defaultDaysPerYear seeds new LeaveBalance rows; each employee's actual allocation can still be adjusted individually. */
@Entity
/** Codes are unique per company (ux_leave_type_company_code, V38). */
@Table(name = "leave_type")
public class LeaveType extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(name = "default_days_per_year", nullable = false)
    private double defaultDaysPerYear;

    @Column(name = "carry_forward", nullable = false)
    private boolean carryForward = false;

    @Column(name = "auto_approve", nullable = false)
    private boolean autoApprove = false;

    @Column(nullable = false)
    private boolean active = true;

    /** False for unpaid leave: approved days are deducted as loss of pay by PayrollService (V40). */
    @Column(nullable = false)
    private boolean paid = true;

    // ---- Policy (V45). See LeaveAccrualCalculator / LeaveBalanceService for how these are applied. ----

    /** YEARLY_UPFRONT (whole entitlement on Jan 1), MONTHLY (1/12 on the 1st of each month) or QUARTERLY. */
    @Column(name = "accrual_mode", nullable = false, length = 20)
    private String accrualMode = "YEARLY_UPFRONT";

    /** HALF_DAY rounds the cumulative accrual to the nearest 0.5 day; EXACT keeps two decimals. */
    @Column(name = "accrual_rounding", nullable = false, length = 20)
    private String accrualRounding = "HALF_DAY";

    /** YEARLY_UPFRONT only: a mid-year joiner gets (remaining months / 12) of the entitlement. MONTHLY/QUARTERLY always start from joining. */
    @Column(name = "prorate_on_joining", nullable = false)
    private boolean prorateOnJoining = false;

    /** When set (e.g. 15), joining after this day of the month earns nothing for the joining month. */
    @Column(name = "joining_cutoff_day")
    private Integer joiningCutoffDay;

    /** Balance never accrues above this many days (null = no cap). */
    @Column(name = "max_balance_days")
    private Double maxBalanceDays;

    /** Accrual starts only this many days after joining (null/0 = from joining). */
    @Column(name = "probation_days")
    private Integer probationDays;

    /** Leave may be applied/approved until the balance reaches -N days (null/0 = no negative balance). */
    @Column(name = "negative_balance_days")
    private Double negativeBalanceDays;

    /** Year-end carry-forward ceiling when carryForward is on (null = carry everything). */
    @Column(name = "max_carry_forward_days")
    private Double maxCarryForwardDays;

    /** Carried days not used within this many months of the new leave year lapse (null = never). */
    @Column(name = "carry_forward_expiry_months")
    private Integer carryForwardExpiryMonths;

    @Column(nullable = false)
    private boolean encashable = false;

    @Column(name = "max_encashable_days_per_year")
    private Double maxEncashableDaysPerYear;

    /** BASIC or GROSS - which monthly figure of the active salary structure an encashed day is worth 1/30 of. */
    @Column(name = "encashment_basis", nullable = false, length = 10)
    private String encashmentBasis = "BASIC";

    /** True for types the system owns (Compensatory Off, code CO): credited by approvals, not accrual. */
    @Column(name = "system_managed", nullable = false)
    private boolean systemManaged = false;

    /** Comp-off: days after the worked date that a credit stays usable (null = 90). */
    @Column(name = "credit_expiry_days")
    private Integer creditExpiryDays;

    /** Sandwich rule (V67): weekly offs and holidays between two leave days are counted as leave. */
    @Column(name = "sandwich_rule", nullable = false)
    private boolean sandwichRule = false;

    public String getAccrualMode() { return accrualMode; }
    public void setAccrualMode(String accrualMode) { this.accrualMode = accrualMode; }
    public String getAccrualRounding() { return accrualRounding; }
    public void setAccrualRounding(String accrualRounding) { this.accrualRounding = accrualRounding; }
    public boolean isProrateOnJoining() { return prorateOnJoining; }
    public void setProrateOnJoining(boolean prorateOnJoining) { this.prorateOnJoining = prorateOnJoining; }
    public Integer getJoiningCutoffDay() { return joiningCutoffDay; }
    public void setJoiningCutoffDay(Integer joiningCutoffDay) { this.joiningCutoffDay = joiningCutoffDay; }
    public Double getMaxBalanceDays() { return maxBalanceDays; }
    public void setMaxBalanceDays(Double maxBalanceDays) { this.maxBalanceDays = maxBalanceDays; }
    public Integer getProbationDays() { return probationDays; }
    public void setProbationDays(Integer probationDays) { this.probationDays = probationDays; }
    public Double getNegativeBalanceDays() { return negativeBalanceDays; }
    public void setNegativeBalanceDays(Double negativeBalanceDays) { this.negativeBalanceDays = negativeBalanceDays; }
    public Double getMaxCarryForwardDays() { return maxCarryForwardDays; }
    public void setMaxCarryForwardDays(Double maxCarryForwardDays) { this.maxCarryForwardDays = maxCarryForwardDays; }
    public Integer getCarryForwardExpiryMonths() { return carryForwardExpiryMonths; }
    public void setCarryForwardExpiryMonths(Integer carryForwardExpiryMonths) { this.carryForwardExpiryMonths = carryForwardExpiryMonths; }
    public boolean isEncashable() { return encashable; }
    public void setEncashable(boolean encashable) { this.encashable = encashable; }
    public Double getMaxEncashableDaysPerYear() { return maxEncashableDaysPerYear; }
    public void setMaxEncashableDaysPerYear(Double maxEncashableDaysPerYear) { this.maxEncashableDaysPerYear = maxEncashableDaysPerYear; }
    public String getEncashmentBasis() { return encashmentBasis; }
    public void setEncashmentBasis(String encashmentBasis) { this.encashmentBasis = encashmentBasis; }
    public boolean isSystemManaged() { return systemManaged; }
    public void setSystemManaged(boolean systemManaged) { this.systemManaged = systemManaged; }
    public boolean isSandwichRule() { return sandwichRule; }
    public void setSandwichRule(boolean sandwichRule) { this.sandwichRule = sandwichRule; }
    public Integer getCreditExpiryDays() { return creditExpiryDays; }
    public void setCreditExpiryDays(Integer creditExpiryDays) { this.creditExpiryDays = creditExpiryDays; }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public double getDefaultDaysPerYear() {
        return defaultDaysPerYear;
    }

    public void setDefaultDaysPerYear(double defaultDaysPerYear) {
        this.defaultDaysPerYear = defaultDaysPerYear;
    }

    public boolean isCarryForward() {
        return carryForward;
    }

    public void setCarryForward(boolean carryForward) {
        this.carryForward = carryForward;
    }

    public boolean isAutoApprove() {
        return autoApprove;
    }

    public void setAutoApprove(boolean autoApprove) {
        this.autoApprove = autoApprove;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
}
