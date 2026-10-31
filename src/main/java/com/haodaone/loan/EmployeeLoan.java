package com.haodaone.loan;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A loan or salary advance (V60). REQUESTED -> APPROVED (terms set, schedule generated) -> ACTIVE (money paid
 * out) -> CLOSED (fully recovered); or REJECTED / CANCELLED. Recovery runs through payroll as post-tax EMIs.
 */
@Entity
@Table(name = "employee_loan")
public class EmployeeLoan extends BaseEntity {

    public static final String LOAN = "LOAN";
    public static final String ADVANCE = "ADVANCE";

    public static final String REQUESTED = "REQUESTED";
    public static final String APPROVED = "APPROVED";
    public static final String ACTIVE = "ACTIVE";
    public static final String CLOSED = "CLOSED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "loan_type", nullable = false, length = 20)
    private String loanType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "requested_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "requested_months", nullable = false)
    private int requestedMonths;

    @Column(length = 1000)
    private String reason;

    @Column(name = "approved_amount", precision = 14, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "tenure_months")
    private Integer tenureMonths;

    @Column(name = "annual_interest_rate", precision = 6, scale = 3)
    private BigDecimal annualInterestRate;

    @Column(name = "emi_amount", precision = 14, scale = 2)
    private BigDecimal emiAmount;

    /** YYYY-MM of the first payroll that recovers an installment. */
    @Column(name = "first_deduction_month", length = 7)
    private String firstDeductionMonth;

    @Column(name = "disburse_via_payroll", nullable = false)
    private boolean disburseViaPayroll;

    @Column(name = "disbursed_on")
    private LocalDate disbursedOn;

    @Column(name = "disbursement_reference", length = 100)
    private String disbursementReference;

    @Column(name = "disbursal_settled", nullable = false)
    private boolean disbursalSettled;

    @Column(name = "reviewed_by", length = 150)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getLoanType() { return loanType; }
    public void setLoanType(String loanType) { this.loanType = loanType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
    public int getRequestedMonths() { return requestedMonths; }
    public void setRequestedMonths(int requestedMonths) { this.requestedMonths = requestedMonths; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }
    public Integer getTenureMonths() { return tenureMonths; }
    public void setTenureMonths(Integer tenureMonths) { this.tenureMonths = tenureMonths; }
    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public void setAnnualInterestRate(BigDecimal annualInterestRate) { this.annualInterestRate = annualInterestRate; }
    public BigDecimal getEmiAmount() { return emiAmount; }
    public void setEmiAmount(BigDecimal emiAmount) { this.emiAmount = emiAmount; }
    public String getFirstDeductionMonth() { return firstDeductionMonth; }
    public void setFirstDeductionMonth(String firstDeductionMonth) { this.firstDeductionMonth = firstDeductionMonth; }
    public boolean isDisburseViaPayroll() { return disburseViaPayroll; }
    public void setDisburseViaPayroll(boolean disburseViaPayroll) { this.disburseViaPayroll = disburseViaPayroll; }
    public LocalDate getDisbursedOn() { return disbursedOn; }
    public void setDisbursedOn(LocalDate disbursedOn) { this.disbursedOn = disbursedOn; }
    public String getDisbursementReference() { return disbursementReference; }
    public void setDisbursementReference(String disbursementReference) { this.disbursementReference = disbursementReference; }
    public boolean isDisbursalSettled() { return disbursalSettled; }
    public void setDisbursalSettled(boolean disbursalSettled) { this.disbursalSettled = disbursalSettled; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public LocalDate getClosedOn() { return closedOn; }
    public void setClosedOn(LocalDate closedOn) { this.closedOn = closedOn; }
}
