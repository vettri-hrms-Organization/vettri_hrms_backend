package com.haodaone.expense.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Expense claim header. Workflow:
 * DRAFT -> SUBMITTED -> MANAGER_APPROVED -> APPROVED -> REIMBURSED,
 * with REJECTED reachable from any review stage.
 */
@Entity
@Table(name = "expense_claim")
public class ExpenseClaim extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_MANAGER_APPROVED = "MANAGER_APPROVED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_REIMBURSED = "REIMBURSED";

    public static final String METHOD_MANUAL = "MANUAL";
    public static final String METHOD_PAYROLL = "PAYROLL";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String purpose;

    @Column(nullable = false, length = 30)
    private String status = STATUS_DRAFT;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_approved_by_employee_id")
    private Employee managerApprovedBy;

    @Column(name = "manager_approved_at")
    private LocalDateTime managerApprovedAt;

    @Column(name = "manager_note", length = 500)
    private String managerNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_employee_id")
    private Employee approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approver_note", length = 500)
    private String approverNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by_employee_id")
    private Employee rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** The status the claim was in when it was rejected. */
    @Column(name = "rejected_stage", length = 30)
    private String rejectedStage;

    @Column(name = "reimbursed_at")
    private LocalDateTime reimbursedAt;

    @Column(name = "reimbursement_method", length = 20)
    private String reimbursementMethod;

    @Column(name = "payment_reference", length = 120)
    private String paymentReference;

    @Column(name = "payroll_item_id")
    private Long payrollItemId;

    /** Policy warnings accepted at submission, newline separated. */
    @Column(name = "policy_warnings", length = 2000)
    private String policyWarnings;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Employee getManagerApprovedBy() { return managerApprovedBy; }
    public void setManagerApprovedBy(Employee managerApprovedBy) { this.managerApprovedBy = managerApprovedBy; }
    public LocalDateTime getManagerApprovedAt() { return managerApprovedAt; }
    public void setManagerApprovedAt(LocalDateTime managerApprovedAt) { this.managerApprovedAt = managerApprovedAt; }
    public String getManagerNote() { return managerNote; }
    public void setManagerNote(String managerNote) { this.managerNote = managerNote; }
    public Employee getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Employee approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getApproverNote() { return approverNote; }
    public void setApproverNote(String approverNote) { this.approverNote = approverNote; }
    public Employee getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(Employee rejectedBy) { this.rejectedBy = rejectedBy; }
    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getRejectedStage() { return rejectedStage; }
    public void setRejectedStage(String rejectedStage) { this.rejectedStage = rejectedStage; }
    public LocalDateTime getReimbursedAt() { return reimbursedAt; }
    public void setReimbursedAt(LocalDateTime reimbursedAt) { this.reimbursedAt = reimbursedAt; }
    public String getReimbursementMethod() { return reimbursementMethod; }
    public void setReimbursementMethod(String reimbursementMethod) { this.reimbursementMethod = reimbursementMethod; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public Long getPayrollItemId() { return payrollItemId; }
    public void setPayrollItemId(Long payrollItemId) { this.payrollItemId = payrollItemId; }
    public String getPolicyWarnings() { return policyWarnings; }
    public void setPolicyWarnings(String policyWarnings) { this.policyWarnings = policyWarnings; }
}
