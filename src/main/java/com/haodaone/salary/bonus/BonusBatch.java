package com.haodaone.salary.bonus;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A bonus run (V64): DRAFT (amounts editable) -> APPROVED (paid with payroll for payoutMonth or later) -> PAID; or CANCELLED. */
@Entity
@Table(name = "bonus_batch")
public class BonusBatch extends BaseEntity {

    public static final String DRAFT = "DRAFT";
    public static final String APPROVED = "APPROVED";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 120)
    private String name;

    /** ONE_TIME, PERFORMANCE, FESTIVAL or STATUTORY. */
    @Column(name = "bonus_type", nullable = false, length = 20)
    private String bonusType;

    /** FIXED, PERCENT_BASIC, PERCENT_GROSS or STATUTORY. */
    @Column(nullable = false, length = 20)
    private String method;

    @Column(name = "method_value", precision = 14, scale = 3)
    private BigDecimal methodValue;

    /** Statutory bonus: the financial year starting in April of this year. */
    @Column(name = "financial_year")
    private Integer financialYear;

    /** Statutory bonus: monthly calculation wage cap (Rs 7,000 or the state minimum wage, whichever is higher). */
    @Column(name = "wage_ceiling", precision = 10, scale = 2)
    private BigDecimal wageCeiling;

    @Column(name = "payout_month", nullable = false, length = 7)
    private String payoutMonth;

    @Column(nullable = false, length = 20)
    private String status = DRAFT;

    @Column(nullable = false)
    private boolean taxable = true;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_by_name", length = 150)
    private String createdByName;

    @Column(name = "approved_by_name", length = 150)
    private String approvedByName;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBonusType() { return bonusType; }
    public void setBonusType(String bonusType) { this.bonusType = bonusType; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public BigDecimal getMethodValue() { return methodValue; }
    public void setMethodValue(BigDecimal methodValue) { this.methodValue = methodValue; }
    public Integer getFinancialYear() { return financialYear; }
    public void setFinancialYear(Integer financialYear) { this.financialYear = financialYear; }
    public BigDecimal getWageCeiling() { return wageCeiling; }
    public void setWageCeiling(BigDecimal wageCeiling) { this.wageCeiling = wageCeiling; }
    public String getPayoutMonth() { return payoutMonth; }
    public void setPayoutMonth(String payoutMonth) { this.payoutMonth = payoutMonth; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isTaxable() { return taxable; }
    public void setTaxable(boolean taxable) { this.taxable = taxable; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public String getApprovedByName() { return approvedByName; }
    public void setApprovedByName(String approvedByName) { this.approvedByName = approvedByName; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
}
