package com.haodaone.expense.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Per-company expense category with optional policy rules. Seeded with
 * the standard set (Travel, Food, ...) the first time a company uses
 * expenses - see ExpenseCategoryService#ensureDefaults.
 */
@Entity
@Table(name = "expense_category")
public class ExpenseCategory extends BaseEntity {

    public static final String ACTION_WARN = "WARN";
    public static final String ACTION_BLOCK = "BLOCK";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    /** Maximum total for this category on a single claim; null = no limit. */
    @Column(name = "per_claim_limit", precision = 12, scale = 2)
    private BigDecimal perClaimLimit;

    @Column(name = "limit_action", nullable = false, length = 10)
    private String limitAction = ACTION_WARN;

    /** A receipt is expected for any line above this amount; null = never required. */
    @Column(name = "receipt_required_above", precision = 12, scale = 2)
    private BigDecimal receiptRequiredAbove;

    @Column(name = "receipt_action", nullable = false, length = 10)
    private String receiptAction = ACTION_WARN;

    /** Company rate per km for mileage lines; when set it overrides any rate the employee enters. */
    @Column(name = "mileage_rate_per_km", precision = 8, scale = 2)
    private BigDecimal mileageRatePerKm;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public BigDecimal getPerClaimLimit() { return perClaimLimit; }
    public void setPerClaimLimit(BigDecimal perClaimLimit) { this.perClaimLimit = perClaimLimit; }
    public String getLimitAction() { return limitAction; }
    public void setLimitAction(String limitAction) { this.limitAction = limitAction; }
    public BigDecimal getReceiptRequiredAbove() { return receiptRequiredAbove; }
    public void setReceiptRequiredAbove(BigDecimal receiptRequiredAbove) { this.receiptRequiredAbove = receiptRequiredAbove; }
    public String getReceiptAction() { return receiptAction; }
    public void setReceiptAction(String receiptAction) { this.receiptAction = receiptAction; }
    public BigDecimal getMileageRatePerKm() { return mileageRatePerKm; }
    public void setMileageRatePerKm(BigDecimal mileageRatePerKm) { this.mileageRatePerKm = mileageRatePerKm; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
