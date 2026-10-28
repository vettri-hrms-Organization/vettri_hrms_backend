package com.haodaone.company.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "subscription")
public class Subscription extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SubscriptionStatus status;

    @Column(name = "employee_limit")
    private Integer employeeLimit;

    @Column(name = "device_limit")
    private Integer deviceLimit;

    @Column(name = "billing_cycle", length = 20)
    private String billingCycle;

    @Column(name = "billable_employee_count")
    private Integer billableEmployeeCount;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "renewal_date")
    private LocalDate renewalDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(precision = 10, scale = 2)
    private BigDecimal rate;

    /** First day the renewal went unpaid (PAST_DUE). */
    @Column(name = "past_due_since")
    private LocalDate pastDueSince;

    /** Last day of the grace window; the company is suspended the day after. */
    @Column(name = "grace_ends_on")
    private LocalDate graceEndsOn;

    @Column(name = "suspended_at")
    private java.time.LocalDateTime suspendedAt;

    @Column(name = "suspension_reason", length = 50)
    private String suspensionReason;

    /** Plan to switch to at the next renewal (downgrades, or the plan chosen during a trial). */
    @Column(name = "pending_plan", length = 50)
    private String pendingPlan;

    @Column(name = "pending_billing_cycle", length = 20)
    private String pendingBillingCycle;

    /** Highest billable seat count already charged for in the current period (paid seats + prorated add-ons). */
    @Column(name = "period_peak_seats")
    private Integer periodPeakSeats;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd = false;

    @Column(name = "razorpay_subscription_id", length = 100)
    private String razorpaySubscriptionId;

    @Column(name = "razorpay_plan_id", length = 100)
    private String razorpayPlanId;

    public LocalDate getPastDueSince() { return pastDueSince; }
    public void setPastDueSince(LocalDate pastDueSince) { this.pastDueSince = pastDueSince; }
    public LocalDate getGraceEndsOn() { return graceEndsOn; }
    public void setGraceEndsOn(LocalDate graceEndsOn) { this.graceEndsOn = graceEndsOn; }
    public java.time.LocalDateTime getSuspendedAt() { return suspendedAt; }
    public void setSuspendedAt(java.time.LocalDateTime suspendedAt) { this.suspendedAt = suspendedAt; }
    public String getSuspensionReason() { return suspensionReason; }
    public void setSuspensionReason(String suspensionReason) { this.suspensionReason = suspensionReason; }
    public String getPendingPlan() { return pendingPlan; }
    public void setPendingPlan(String pendingPlan) { this.pendingPlan = pendingPlan; }
    public String getPendingBillingCycle() { return pendingBillingCycle; }
    public void setPendingBillingCycle(String pendingBillingCycle) { this.pendingBillingCycle = pendingBillingCycle; }
    public Integer getPeriodPeakSeats() { return periodPeakSeats; }
    public void setPeriodPeakSeats(Integer periodPeakSeats) { this.periodPeakSeats = periodPeakSeats; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) { this.cancelAtPeriodEnd = cancelAtPeriodEnd; }
    public String getRazorpaySubscriptionId() { return razorpaySubscriptionId; }
    public void setRazorpaySubscriptionId(String razorpaySubscriptionId) { this.razorpaySubscriptionId = razorpaySubscriptionId; }
    public String getRazorpayPlanId() { return razorpayPlanId; }
    public void setRazorpayPlanId(String razorpayPlanId) { this.razorpayPlanId = razorpayPlanId; }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public Integer getEmployeeLimit() {
        return employeeLimit;
    }

    public void setEmployeeLimit(Integer employeeLimit) {
        this.employeeLimit = employeeLimit;
    }

    public Integer getDeviceLimit() {
        return deviceLimit;
    }

    public void setDeviceLimit(Integer deviceLimit) {
        this.deviceLimit = deviceLimit;
    }

    public String getBillingCycle() {
        return billingCycle;
    }

    public void setBillingCycle(String billingCycle) {
        this.billingCycle = billingCycle;
    }

    public Integer getBillableEmployeeCount() {
        return billableEmployeeCount;
    }

    public void setBillableEmployeeCount(Integer billableEmployeeCount) {
        this.billableEmployeeCount = billableEmployeeCount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getRenewalDate() {
        return renewalDate;
    }

    public void setRenewalDate(LocalDate renewalDate) {
        this.renewalDate = renewalDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }
}
