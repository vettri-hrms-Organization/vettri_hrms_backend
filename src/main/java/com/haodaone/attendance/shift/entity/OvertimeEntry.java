package com.haodaone.attendance.shift.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Overtime for one employee on one day. Lifecycle: PENDING -> APPROVED | REJECTED; APPROVED -> PAID
 * once payroll has included it (payrollItemId set). The amount is priced at approval time.
 */
@Entity
@Table(name = "overtime_entry",
        uniqueConstraints = @UniqueConstraint(name = "uk_overtime_employee_date", columnNames = {"employee_id", "work_date"}),
        indexes = @Index(name = "idx_overtime_company_status", columnList = "company_id, status, work_date"))
public class OvertimeEntry extends BaseEntity {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String PAID = "PAID";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "shift_id")
    private Long shiftId;

    @Column(name = "shift_name", length = 100)
    private String shiftName;

    @Column(name = "computed_minutes", nullable = false)
    private int computedMinutes;

    @Column(name = "approved_minutes")
    private Integer approvedMinutes;

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    /** SYSTEM (nightly job) or EMPLOYEE (self claim). */
    @Column(nullable = false, length = 20)
    private String source = "SYSTEM";

    @Column(name = "employee_note", length = 500)
    private String employeeNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_employee_id")
    private Employee reviewedByEmployee;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "hourly_rate", precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "rate_multiplier", precision = 5, scale = 2)
    private BigDecimal rateMultiplier;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payroll_item_id")
    private Long payrollItemId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public String getShiftName() { return shiftName; }
    public void setShiftName(String shiftName) { this.shiftName = shiftName; }
    public int getComputedMinutes() { return computedMinutes; }
    public void setComputedMinutes(int computedMinutes) { this.computedMinutes = computedMinutes; }
    public Integer getApprovedMinutes() { return approvedMinutes; }
    public void setApprovedMinutes(Integer approvedMinutes) { this.approvedMinutes = approvedMinutes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getEmployeeNote() { return employeeNote; }
    public void setEmployeeNote(String employeeNote) { this.employeeNote = employeeNote; }
    public Employee getReviewedByEmployee() { return reviewedByEmployee; }
    public void setReviewedByEmployee(Employee reviewedByEmployee) { this.reviewedByEmployee = reviewedByEmployee; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
    public BigDecimal getRateMultiplier() { return rateMultiplier; }
    public void setRateMultiplier(BigDecimal rateMultiplier) { this.rateMultiplier = rateMultiplier; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Long getPayrollItemId() { return payrollItemId; }
    public void setPayrollItemId(Long payrollItemId) { this.payrollItemId = payrollItemId; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
