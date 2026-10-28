package com.haodaone.leave.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An employee's claim for compensatory-off credit for working on a week-off or holiday (V45).
 * Once APPROVED it credits {@link #getDays()} to the company's "Compensatory Off" (CO) leave type,
 * usable until {@link #getExpiresOn()}; the unused part lapses after that (computed - see LeaveAccrualCalculator.compOffFifo).
 */
@Entity
@Table(name = "comp_off_request")
public class CompOffRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** FULL_DAY, HALF_DAY or HOURS. */
    @Column(nullable = false, length = 20)
    private String duration;

    @Column
    private Double hours;

    /** Credit in days (1 or 0.5) derived from duration/hours. */
    @Column(nullable = false)
    private double days;

    /** WEEKLY_OFF or HOLIDAY. */
    @Column(name = "day_type", nullable = false, length = 20)
    private String dayType;

    /** True when attendance was found for the day; null when the employee has no attendance data to check against. */
    @Column(name = "attendance_verified")
    private Boolean attendanceVerified;

    @Column(length = 500)
    private String reason;

    /** PENDING, APPROVED, REJECTED, CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_employee_id")
    private Employee decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public Double getHours() { return hours; }
    public void setHours(Double hours) { this.hours = hours; }
    public double getDays() { return days; }
    public void setDays(double days) { this.days = days; }
    public String getDayType() { return dayType; }
    public void setDayType(String dayType) { this.dayType = dayType; }
    public Boolean getAttendanceVerified() { return attendanceVerified; }
    public void setAttendanceVerified(Boolean attendanceVerified) { this.attendanceVerified = attendanceVerified; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }
    public Employee getDecidedBy() { return decidedBy; }
    public void setDecidedBy(Employee decidedBy) { this.decidedBy = decidedBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
}
