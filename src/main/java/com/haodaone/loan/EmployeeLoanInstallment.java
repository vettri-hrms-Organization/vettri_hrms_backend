package com.haodaone.loan;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One monthly installment of a loan (V60): PENDING until payroll recovers it, or paid/deferred by HR. */
@Entity
@Table(name = "employee_loan_installment")
public class EmployeeLoanInstallment extends BaseEntity {

    public static final String PENDING = "PENDING";
    public static final String DEDUCTED = "DEDUCTED";
    public static final String PAID_MANUALLY = "PAID_MANUALLY";
    public static final String DEFERRED = "DEFERRED";
    public static final String WAIVED = "WAIVED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "due_month", nullable = false, length = 7)
    private String dueMonth;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal principal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal interest;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "payroll_item_id")
    private Long payrollItemId;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(length = 300)
    private String note;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getLoanId() { return loanId; }
    public void setLoanId(Long loanId) { this.loanId = loanId; }
    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }
    public String getDueMonth() { return dueMonth; }
    public void setDueMonth(String dueMonth) { this.dueMonth = dueMonth; }
    public BigDecimal getPrincipal() { return principal; }
    public void setPrincipal(BigDecimal principal) { this.principal = principal; }
    public BigDecimal getInterest() { return interest; }
    public void setInterest(BigDecimal interest) { this.interest = interest; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPayrollItemId() { return payrollItemId; }
    public void setPayrollItemId(Long payrollItemId) { this.payrollItemId = payrollItemId; }
    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
