package com.haodaone.imports.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.leave.entity.LeaveBalanceAdjustment;
import com.haodaone.leave.entity.LeaveType;
import jakarta.persistence.*;

/**
 * The opening balance imported for one employee / leave type / leave year (V51). At most one live
 * (deleted = false) row per key, which is what makes re-importing the same file idempotent: a second import
 * is rejected unless "replace" is chosen, in which case the previous row and its effect are reversed first.
 *
 * <p>applyMode ADJUSTMENT: a {@link LeaveBalanceAdjustment} of {@code appliedDays} brings the computed balance
 * to {@code openingDays} as of the import date. CARRY_FORWARD: the year's LeaveBalance.carriedForwardDays is
 * set to {@code openingDays} ({@code previousCarriedDays} keeps the old value for a later replace).
 */
@Entity
@Table(name = "opening_leave_balance")
public class OpeningLeaveBalance extends BaseEntity {

    public static final String MODE_ADJUSTMENT = "ADJUSTMENT";
    public static final String MODE_CARRY_FORWARD = "CARRY_FORWARD";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private DataImportBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "leave_year", nullable = false)
    private int leaveYear;

    @Column(name = "opening_days", nullable = false)
    private double openingDays;

    @Column(name = "apply_mode", nullable = false, length = 20)
    private String applyMode;

    @Column(name = "applied_days", nullable = false)
    private double appliedDays;

    @Column(name = "previous_carried_days")
    private Double previousCarriedDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_id")
    private LeaveBalanceAdjustment adjustment;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public DataImportBatch getBatch() { return batch; }
    public void setBatch(DataImportBatch batch) { this.batch = batch; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public LeaveType getLeaveType() { return leaveType; }
    public void setLeaveType(LeaveType leaveType) { this.leaveType = leaveType; }
    public int getLeaveYear() { return leaveYear; }
    public void setLeaveYear(int leaveYear) { this.leaveYear = leaveYear; }
    public double getOpeningDays() { return openingDays; }
    public void setOpeningDays(double openingDays) { this.openingDays = openingDays; }
    public String getApplyMode() { return applyMode; }
    public void setApplyMode(String applyMode) { this.applyMode = applyMode; }
    public double getAppliedDays() { return appliedDays; }
    public void setAppliedDays(double appliedDays) { this.appliedDays = appliedDays; }
    public Double getPreviousCarriedDays() { return previousCarriedDays; }
    public void setPreviousCarriedDays(Double previousCarriedDays) { this.previousCarriedDays = previousCarriedDays; }
    public LeaveBalanceAdjustment getAdjustment() { return adjustment; }
    public void setAdjustment(LeaveBalanceAdjustment adjustment) { this.adjustment = adjustment; }
}
