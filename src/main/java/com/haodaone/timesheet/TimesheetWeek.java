package com.haodaone.timesheet;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One employee's Monday-to-Sunday timesheet (V58). Entries can be edited while DRAFT or REJECTED;
 * SUBMITTED waits for the approver and APPROVED is locked.
 */
@Entity
@Table(name = "timesheet_week", uniqueConstraints = @UniqueConstraint(name = "uk_timesheet_week_employee", columnNames = {"employee_id", "week_start"}))
public class TimesheetWeek extends BaseEntity {

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false, length = 20)
    private String status = DRAFT;

    @Column(name = "total_minutes", nullable = false)
    private int totalMinutes;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_by_name", length = 150)
    private String reviewedByName;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public boolean isEditable() {
        return DRAFT.equals(status) || REJECTED.equals(status);
    }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getTotalMinutes() { return totalMinutes; }
    public void setTotalMinutes(int totalMinutes) { this.totalMinutes = totalMinutes; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Long getReviewedByUserId() { return reviewedByUserId; }
    public void setReviewedByUserId(Long reviewedByUserId) { this.reviewedByUserId = reviewedByUserId; }
    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
