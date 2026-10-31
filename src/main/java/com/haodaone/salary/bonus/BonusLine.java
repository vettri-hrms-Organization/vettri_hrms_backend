package com.haodaone.salary.bonus;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

/** One employee's bonus in a batch (V64); settled when the payroll that pays it is processed. */
@Entity
@Table(name = "bonus_line")
public class BonusLine extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** What the amount was calculated from (monthly basic/gross, or the year's eligible wages). */
    @Column(name = "basis_amount", precision = 14, scale = 2)
    private BigDecimal basisAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(length = 300)
    private String note;

    @Column(nullable = false)
    private boolean settled;

    @Column(name = "payroll_item_id")
    private Long payrollItemId;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public BigDecimal getBasisAmount() { return basisAmount; }
    public void setBasisAmount(BigDecimal basisAmount) { this.basisAmount = basisAmount; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isSettled() { return settled; }
    public void setSettled(boolean settled) { this.settled = settled; }
    public Long getPayrollItemId() { return payrollItemId; }
    public void setPayrollItemId(Long payrollItemId) { this.payrollItemId = payrollItemId; }
}
