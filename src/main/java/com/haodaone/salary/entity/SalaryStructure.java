package com.haodaone.salary.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.employee.entity.Employee;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An employee's compensation definition, effective from a given date.
 * Deliberately versioned rather than mutated in place: revising a salary
 * creates a new row and flips the previous one's {@code active} flag off,
 * so every past structure - and every payroll run that snapshot it via
 * {@link PayrollItem} - stays exactly as it was when it was in force. Only
 * one row per employee may have {@code active = true}; see
 * V7__salary_management.sql for the partial unique index that guarantees
 * this at the database level, not just in the service layer.
 */
@Entity
@Table(name = "salary_structure")
public class SalaryStructure extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** True for exactly one structure per employee at any time - the one payroll processing currently uses. */
    @Column(nullable = false)
    private boolean active = true;

    @Embedded
    private SalaryComponents components = new SalaryComponents();

    /** Denormalized from {@link SalaryComponents#grossSalary()} at save time, so list/sort/filter queries don't need to recompute it. */
    @Column(name = "gross_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossSalary = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "net_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Column(length = 1000)
    private String notes;

    /** Recomputes {@link #grossSalary}/{@link #totalDeductions}/{@link #netSalary} from {@link #components}. Call after every component change, before persisting. */
    public void recalculate() {
        this.grossSalary = components.grossSalary();
        this.totalDeductions = components.totalDeductions();
        this.netSalary = components.netSalary();
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public SalaryComponents getComponents() {
        return components;
    }

    public void setComponents(SalaryComponents components) {
        this.components = components;
    }

    public BigDecimal getGrossSalary() {
        return grossSalary;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public BigDecimal getNetSalary() {
        return netSalary;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
