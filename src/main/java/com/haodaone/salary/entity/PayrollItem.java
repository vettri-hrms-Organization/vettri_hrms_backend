package com.haodaone.salary.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.employee.entity.Employee;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One employee's payslip line within a {@link PayrollRun}. {@link #components}
 * is a frozen copy of the employee's {@link SalaryStructure} at the moment
 * the run was generated/processed - later edits to that structure never
 * change a past run, exactly as real payroll history must behave.
 */
@Entity
@Table(name = "payroll_item")
public class PayrollItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** The structure this item was generated from - nullable only because a structure could theoretically be hard-purged later; never null in normal operation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salary_structure_id")
    private SalaryStructure salaryStructure;

    @Embedded
    private SalaryComponents components = new SalaryComponents();

    @Column(name = "gross_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossSalary = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "net_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = PayrollItemStatus.PENDING;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(length = 500)
    private String remarks;

    /** Recomputes gross/deductions/net from {@link #components}. */
    public void recalculate() {
        this.grossSalary = components.grossSalary();
        this.totalDeductions = components.totalDeductions();
        this.netSalary = components.netSalary();
    }

    public PayrollRun getPayrollRun() {
        return payrollRun;
    }

    public void setPayrollRun(PayrollRun payrollRun) {
        this.payrollRun = payrollRun;
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public SalaryStructure getSalaryStructure() {
        return salaryStructure;
    }

    public void setSalaryStructure(SalaryStructure salaryStructure) {
        this.salaryStructure = salaryStructure;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
