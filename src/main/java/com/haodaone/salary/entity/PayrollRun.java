package com.haodaone.salary.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single payroll processing batch for one calendar month, e.g. "August
 * 2026". Created in DRAFT with one {@link PayrollItem} per active employee
 * who has an active {@link SalaryStructure}, reviewed/held as needed, then
 * processed (totals locked, items snapshot their structure) and finally
 * marked paid. See PayrollService for the full state machine.
 */
@Entity
@Table(name = "payroll_run")
public class PayrollRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(nullable = false, length = 20)
    private String status = PayrollRunStatus.DRAFT;

    @Column(name = "pay_date")
    private LocalDate payDate;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "total_employees", nullable = false)
    private int totalEmployees;

    @Column(name = "total_gross", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(length = 1000)
    private String remarks;

    public int getPeriodMonth() {
        return periodMonth;
    }

    public void setPeriodMonth(int periodMonth) {
        this.periodMonth = periodMonth;
    }

    public int getPeriodYear() {
        return periodYear;
    }

    public void setPeriodYear(int periodYear) {
        this.periodYear = periodYear;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public void setPayDate(LocalDate payDate) {
        this.payDate = payDate;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public int getTotalEmployees() {
        return totalEmployees;
    }

    public void setTotalEmployees(int totalEmployees) {
        this.totalEmployees = totalEmployees;
    }

    public BigDecimal getTotalGross() {
        return totalGross;
    }

    public void setTotalGross(BigDecimal totalGross) {
        this.totalGross = totalGross;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public void setTotalDeductions(BigDecimal totalDeductions) {
        this.totalDeductions = totalDeductions;
    }

    public BigDecimal getTotalNet() {
        return totalNet;
    }

    public void setTotalNet(BigDecimal totalNet) {
        this.totalNet = totalNet;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
}
