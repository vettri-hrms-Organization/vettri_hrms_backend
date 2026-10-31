package com.haodaone.salary.revision;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

/** One month's arrear for a back-dated revision (V61): what was paid vs what the new salary would have paid. */
@Entity
@Table(name = "salary_revision_arrear")
public class SalaryRevisionArrear extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "revision_id", nullable = false)
    private Long revisionId;

    /** YYYY-MM of the payroll month being corrected. */
    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "paid_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidGross;

    @Column(name = "revised_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal revisedGross;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** The original payroll item for that month. */
    @Column(name = "payroll_item_id")
    private Long payrollItemId;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long revisionId) { this.revisionId = revisionId; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public BigDecimal getPaidGross() { return paidGross; }
    public void setPaidGross(BigDecimal paidGross) { this.paidGross = paidGross; }
    public BigDecimal getRevisedGross() { return revisedGross; }
    public void setRevisedGross(BigDecimal revisedGross) { this.revisedGross = revisedGross; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Long getPayrollItemId() { return payrollItemId; }
    public void setPayrollItemId(Long payrollItemId) { this.payrollItemId = payrollItemId; }
}
