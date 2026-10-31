package com.haodaone.salary.fbp;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** An employee's FBP split for a financial year (V65): SUBMITTED -> APPROVED or REJECTED. */
@Entity
@Table(name = "fbp_declaration")
public class FbpDeclaration extends BaseEntity {

    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "financial_year", nullable = false)
    private Integer financialYear;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "monthly_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyTotal;

    @Column(name = "lines_json", nullable = false, columnDefinition = "TEXT")
    private String linesJson;

    @Column(name = "start_month", length = 7)
    private String startMonth;

    @Column(name = "reviewed_by", length = 150)
    private String reviewedBy;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "year_end_settled", nullable = false)
    private boolean yearEndSettled;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public Integer getFinancialYear() { return financialYear; }
    public void setFinancialYear(Integer financialYear) { this.financialYear = financialYear; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getMonthlyTotal() { return monthlyTotal; }
    public void setMonthlyTotal(BigDecimal monthlyTotal) { this.monthlyTotal = monthlyTotal; }
    public String getLinesJson() { return linesJson; }
    public void setLinesJson(String linesJson) { this.linesJson = linesJson; }
    public String getStartMonth() { return startMonth; }
    public void setStartMonth(String startMonth) { this.startMonth = startMonth; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public boolean isYearEndSettled() { return yearEndSettled; }
    public void setYearEndSettled(boolean yearEndSettled) { this.yearEndSettled = yearEndSettled; }
}
