package com.haodaone.salary.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * An extra earnings line on a payroll item (overtime, leave encashment,
 * expense reimbursement...), contributed by a
 * {@link com.haodaone.salary.adjustment.PayrollAdjustmentProvider} when the
 * run was generated. Included in the item's gross; {@link #taxable} decides
 * whether it also counts towards TDS. {@link #sourceType}/{@link #sourceId}
 * point back at the record that produced it (e.g. OVERTIME / 42).
 */
@Entity
@Table(name = "payroll_item_adjustment")
public class PayrollItemAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_item_id", nullable = false)
    private PayrollItem payrollItem;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean taxable = true;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    /** Set once the run is processed and the provider's markSettled callback has been invoked. */
    @Column(nullable = false)
    private boolean settled = false;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public PayrollItem getPayrollItem() { return payrollItem; }
    public void setPayrollItem(PayrollItem payrollItem) { this.payrollItem = payrollItem; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount == null ? BigDecimal.ZERO : amount; }
    public boolean isTaxable() { return taxable; }
    public void setTaxable(boolean taxable) { this.taxable = taxable; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public boolean isSettled() { return settled; }
    public void setSettled(boolean settled) { this.settled = settled; }
}
