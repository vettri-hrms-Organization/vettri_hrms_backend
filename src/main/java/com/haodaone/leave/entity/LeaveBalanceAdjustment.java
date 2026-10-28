package com.haodaone.leave.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.LocalDate;

/** A manual +/- correction to an employee's leave balance by HR (V45). Signed days; summed into the balance and shown in the ledger. */
@Entity
@Table(name = "leave_balance_adjustment")
public class LeaveBalanceAdjustment extends BaseEntity {

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

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjusted_by_employee_id")
    private Employee adjustedBy;

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
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Employee getAdjustedBy() { return adjustedBy; }
    public void setAdjustedBy(Employee adjustedBy) { this.adjustedBy = adjustedBy; }
}
