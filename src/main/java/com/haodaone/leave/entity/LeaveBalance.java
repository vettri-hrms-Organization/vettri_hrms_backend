package com.haodaone.leave.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.employee.entity.Employee;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/**
 * One row per (employee, leaveType, year) - holds the allocation, not the
 * usage. Usage is computed on read by summing APPROVED LeaveRequest.days
 * within the year (see LeaveRequestRepository.sumApprovedDays) rather than
 * stored here, so approving/rejecting/cancelling a request can never leave
 * this row's "used" figure stale - there isn't one to go stale.
 */
@Entity
@Table(name = "leave_balance", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "leave_type_id", "year"}))
public class LeaveBalance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "\"year\"", nullable = false)
    private int year;

    @Column(name = "allocated_days", nullable = false)
    private double allocatedDays;

    @Column(name = "carried_forward_days", nullable = false)
    private double carriedForwardDays = 0;

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public double getAllocatedDays() {
        return allocatedDays;
    }

    public void setAllocatedDays(double allocatedDays) {
        this.allocatedDays = allocatedDays;
    }

    public double getCarriedForwardDays() {
        return carriedForwardDays;
    }

    public void setCarriedForwardDays(double carriedForwardDays) {
        this.carriedForwardDays = carriedForwardDays;
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
}