package com.haodaone.expense.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/** One row per company; absent row means defaults (pay separately). */
@Entity
@Table(name = "expense_settings")
public class ExpenseSettings extends BaseEntity {

    /** Approved claims are paid with the next payroll run (non-taxable reimbursement line). */
    public static final String MODE_PAYROLL = "PAYROLL";
    /** Approved claims are paid outside payroll and marked reimbursed manually with a payment reference. */
    public static final String MODE_SEPARATE = "SEPARATE";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "reimbursement_mode", nullable = false, length = 20)
    private String reimbursementMode = MODE_SEPARATE;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getReimbursementMode() { return reimbursementMode; }
    public void setReimbursementMode(String reimbursementMode) { this.reimbursementMode = reimbursementMode; }
}
