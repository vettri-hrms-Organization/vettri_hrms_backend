package com.haodaone.leave.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Leave encashment request and, once APPROVED, the payable record (V45). Approved/paid days count as
 * consumed in the balance. amount = days x perDayRate; perDayRate = monthly BASIC or GROSS of the active
 * salary structure / 30. Payroll picks APPROVED rows up via LeaveEncashmentService.approvedUnpaidEncashments
 * and flips them to PAID with markPaid.
 */
@Entity
@Table(name = "leave_encashment")
public class LeaveEncashment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "leave_year", nullable = false)
    private int leaveYear;

    @Column(nullable = false)
    private double days;

    @Column(length = 500)
    private String reason;

    /** PENDING, APPROVED, REJECTED, CANCELLED, PAID */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(length = 10)
    private String basis;

    @Column(name = "per_day_rate", precision = 15, scale = 2)
    private BigDecimal perDayRate;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_employee_id")
    private Employee decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "payroll_item_id")
    private Long payrollItemId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public LeaveType getLeaveType() { return leaveType; }
    public void setLeaveType(LeaveType leaveType) { this.leaveType = leaveType; }
    public int getLeaveYear() { return leaveYear; }
    public void setLeaveYear(int leaveYear) { this.leaveYear = leaveYear; }
    public double getDays() { return days; }
    public void setDays(double days) { this.days = days; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBasis() { return basis; }
    public void setBasis(String basis) { this.basis = basis; }
    public BigDecimal getPerDayRate() { return perDayRate; }
    public void setPerDayRate(BigDecimal perDayRate) { this.perDayRate = perDayRate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Employee getDecidedBy() { return decidedBy; }
    public void setDecidedBy(Employee decidedBy) { this.decidedBy = decidedBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
    public Long getPayrollItemId() { return payrollItemId; }
    public void setPayrollItemId(Long payrollItemId) { this.payrollItemId = payrollItemId; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
