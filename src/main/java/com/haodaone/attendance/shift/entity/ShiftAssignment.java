package com.haodaone.attendance.shift.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.org.entity.Department;
import jakarta.persistence.*;

import java.time.LocalDate;

/** Default shift for a department or an employee from {@code effectiveFrom} onwards (exactly one target is set). */
@Entity
@Table(name = "shift_assignment", indexes = @Index(name = "idx_shift_assignment_company", columnList = "company_id, deleted"))
public class ShiftAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private WorkShift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public WorkShift getShift() { return shift; }
    public void setShift(WorkShift shift) { this.shift = shift; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
}
