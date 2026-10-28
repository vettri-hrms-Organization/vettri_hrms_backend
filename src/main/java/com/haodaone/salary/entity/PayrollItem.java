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

    /** Approved unpaid-leave days inside the period that earnings were docked for (see PayrollProration). */
    @Column(name = "lop_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal lopDays = BigDecimal.ZERO;

    /** Days the earnings were paid for, out of the calendar days in the period. Null on pre-V40 items (always full month). */
    @Column(name = "paid_days", precision = 5, scale = 1)
    private BigDecimal paidDays;

    /** PF wages (prorated basic, capped at the ceiling unless "contribute on full basic"); zero when PF wasn't auto-calculated. */
    @Column(name = "pf_wages", nullable = false, precision = 14, scale = 2)
    private BigDecimal pfWages = BigDecimal.ZERO;

    @Column(name = "eps_wages", nullable = false, precision = 14, scale = 2)
    private BigDecimal epsWages = BigDecimal.ZERO;

    @Column(name = "edli_wages", nullable = false, precision = 14, scale = 2)
    private BigDecimal edliWages = BigDecimal.ZERO;

    /** Employer EPF share (employer PF minus EPS) - a cost to the company, never deducted from the employee. */
    @Column(name = "employer_epf", nullable = false, precision = 14, scale = 2)
    private BigDecimal employerEpf = BigDecimal.ZERO;

    @Column(name = "employer_eps", nullable = false, precision = 14, scale = 2)
    private BigDecimal employerEps = BigDecimal.ZERO;

    @Column(name = "employer_esi", nullable = false, precision = 14, scale = 2)
    private BigDecimal employerEsi = BigDecimal.ZERO;

    /** Sum of {@link #adjustments}; part of gross. */
    @Column(name = "adjustments_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal adjustmentsTotal = BigDecimal.ZERO;

    /** Gross minus non-taxable adjustments, for TDS/Form 16. Null on items created before V43 (= gross). */
    @Column(name = "taxable_gross", precision = 14, scale = 2)
    private BigDecimal taxableGross;

    /** ProfessionalTaxSlabs state code the PT was computed for, when auto-calculated. */
    @Column(name = "pt_state", length = 10)
    private String ptState;

    /** True when PF/ESI/PT/TDS on this item were auto-calculated from the company's statutory settings. */
    @Column(name = "statutory_auto", nullable = false)
    private boolean statutoryAuto = false;

    @OneToMany(mappedBy = "payrollItem", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    @org.hibernate.annotations.BatchSize(size = 100)
    private java.util.List<PayrollItemAdjustment> adjustments = new java.util.ArrayList<>();

    public BigDecimal getPfWages() { return pfWages; }
    public void setPfWages(BigDecimal pfWages) { this.pfWages = nz(pfWages); }
    public BigDecimal getEpsWages() { return epsWages; }
    public void setEpsWages(BigDecimal epsWages) { this.epsWages = nz(epsWages); }
    public BigDecimal getEdliWages() { return edliWages; }
    public void setEdliWages(BigDecimal edliWages) { this.edliWages = nz(edliWages); }
    public BigDecimal getEmployerEpf() { return employerEpf; }
    public void setEmployerEpf(BigDecimal employerEpf) { this.employerEpf = nz(employerEpf); }
    public BigDecimal getEmployerEps() { return employerEps; }
    public void setEmployerEps(BigDecimal employerEps) { this.employerEps = nz(employerEps); }
    public BigDecimal getEmployerEsi() { return employerEsi; }
    public void setEmployerEsi(BigDecimal employerEsi) { this.employerEsi = nz(employerEsi); }
    public BigDecimal getAdjustmentsTotal() { return adjustmentsTotal; }
    public BigDecimal getTaxableGross() { return taxableGross; }
    public void setTaxableGross(BigDecimal taxableGross) { this.taxableGross = taxableGross; }
    public String getPtState() { return ptState; }
    public void setPtState(String ptState) { this.ptState = ptState; }
    public boolean isStatutoryAuto() { return statutoryAuto; }
    public void setStatutoryAuto(boolean statutoryAuto) { this.statutoryAuto = statutoryAuto; }
    public java.util.List<PayrollItemAdjustment> getAdjustments() { return adjustments; }

    /** Attaches an adjustment line to this item (cascade-saved with it). Call {@link #recalculate()} afterwards. */
    public void addAdjustment(PayrollItemAdjustment adjustment) {
        adjustment.setPayrollItem(this);
        adjustments.add(adjustment);
    }

    /** Employer EPF + EPS + employer ESI: the statutory cost on top of gross, never deducted from pay. */
    public BigDecimal employerContributionsTotal() {
        return nz(employerEpf).add(nz(employerEps)).add(nz(employerEsi));
    }

    /** Gross used for income tax: gross minus non-taxable adjustments (gross itself for pre-V43 rows). */
    public BigDecimal effectiveTaxableGross() {
        return taxableGross != null ? taxableGross : nz(grossSalary);
    }

    /** Sum of the attached adjustment amounts (taxable ones only when {@code taxableOnly}). */
    public BigDecimal sumAdjustments(boolean taxableOnly) {
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollItemAdjustment adjustment : adjustments) {
            if (adjustment.isDeleted() || (taxableOnly && !adjustment.isTaxable())) continue;
            total = total.add(nz(adjustment.getAmount()));
        }
        return total;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public BigDecimal getLopDays() { return lopDays; }
    public void setLopDays(BigDecimal lopDays) { this.lopDays = lopDays != null ? lopDays : BigDecimal.ZERO; }
    public BigDecimal getPaidDays() { return paidDays; }
    public void setPaidDays(BigDecimal paidDays) { this.paidDays = paidDays; }

    /** Recomputes gross/deductions/net from {@link #components} plus any adjustment lines. */
    public void recalculate() {
        BigDecimal extra = sumAdjustments(false);
        this.adjustmentsTotal = extra;
        this.grossSalary = components.grossSalary().add(extra);
        this.taxableGross = this.grossSalary.subtract(extra.subtract(sumAdjustments(true)));
        this.totalDeductions = components.totalDeductions();
        this.netSalary = this.grossSalary.subtract(this.totalDeductions);
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
