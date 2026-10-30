package com.haodaone.profile;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * An employee's request to change identity, bank or statutory details (V57). The values only
 * reach the employee record when HR approves; changesJson keeps {"field": {"from": .., "to": ..}}.
 */
@Entity
@Table(name = "profile_change_request")
public class ProfileChangeRequest extends BaseEntity {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 30)
    private String section;

    @Column(name = "changes_json", nullable = false, columnDefinition = "TEXT")
    private String changesJson;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_by_name", length = 150)
    private String reviewedByName;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getChangesJson() { return changesJson; }
    public void setChangesJson(String changesJson) { this.changesJson = changesJson; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getReviewedByUserId() { return reviewedByUserId; }
    public void setReviewedByUserId(Long reviewedByUserId) { this.reviewedByUserId = reviewedByUserId; }
    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
