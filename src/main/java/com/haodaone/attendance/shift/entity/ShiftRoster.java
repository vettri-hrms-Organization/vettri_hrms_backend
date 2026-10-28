package com.haodaone.attendance.shift.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Roster cell: the employee works {@code shift} on {@code rosterDate}, or has a week off.
 * One row per employee and date; a cleared cell is soft-deleted and reused when set again.
 */
@Entity
@Table(name = "shift_roster",
        uniqueConstraints = @UniqueConstraint(name = "uk_shift_roster_employee_date", columnNames = {"employee_id", "roster_date"}),
        indexes = @Index(name = "idx_shift_roster_company_date", columnList = "company_id, roster_date"))
public class ShiftRoster extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "roster_date", nullable = false)
    private LocalDate rosterDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private WorkShift shift;

    @Column(name = "week_off", nullable = false)
    private boolean weekOff = false;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public LocalDate getRosterDate() { return rosterDate; }
    public void setRosterDate(LocalDate rosterDate) { this.rosterDate = rosterDate; }
    public WorkShift getShift() { return shift; }
    public void setShift(WorkShift shift) { this.shift = shift; }
    public boolean isWeekOff() { return weekOff; }
    public void setWeekOff(boolean weekOff) { this.weekOff = weekOff; }
}
