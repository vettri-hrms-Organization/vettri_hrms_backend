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
