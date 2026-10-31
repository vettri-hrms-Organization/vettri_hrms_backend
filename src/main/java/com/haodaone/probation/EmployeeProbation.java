package com.haodaone.probation;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** An employee's probation and confirmation review (V67): ON_PROBATION -> MANAGER_REVIEW -> HR_REVIEW -> CONFIRMED / NOT_CONFIRMED (EXTENDED goes back to ON_PROBATION). */
@Entity
@Table(name = "employee_probation")
public class EmployeeProbation extends BaseEntity {

    public static final String ON_PROBATION = "ON_PROBATION";
    public static final String MANAGER_REVIEW = "MANAGER_REVIEW";
    public static final String HR_REVIEW = "HR_REVIEW";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String NOT_CONFIRMED = "NOT_CONFIRMED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "probation_end", nullable = false)
    private LocalDate probationEnd;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "manager_employee_id")
    private Long managerEmployeeId;

    @Column(name = "manager_recommendation", length = 20)
    private String managerRecommendation;

    @Column(name = "manager_feedback", columnDefinition = "TEXT")
    private String managerFeedback;

    @Column(name = "manager_submitted_at")
    private LocalDateTime managerSubmittedAt;

    @Column(name = "hr_note", length = 1000)
    private String hrNote;

    @Column(name = "decided_by_name", length = 150)
    private String decidedByName;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "extensions", nullable = false)
    private int extensions;

    @Column(name = "history_json", columnDefinition = "TEXT")
    private String historyJson;

    @Column(name = "letter_id")
    private Long letterId;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public LocalDate getProbationEnd() { return probationEnd; }
    public void setProbationEnd(LocalDate probationEnd) { this.probationEnd = probationEnd; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getManagerEmployeeId() { return managerEmployeeId; }
    public void setManagerEmployeeId(Long managerEmployeeId) { this.managerEmployeeId = managerEmployeeId; }
    public String getManagerRecommendation() { return managerRecommendation; }
    public void setManagerRecommendation(String managerRecommendation) { this.managerRecommendation = managerRecommendation; }
    public String getManagerFeedback() { return managerFeedback; }
    public void setManagerFeedback(String managerFeedback) { this.managerFeedback = managerFeedback; }
    public LocalDateTime getManagerSubmittedAt() { return managerSubmittedAt; }
    public void setManagerSubmittedAt(LocalDateTime managerSubmittedAt) { this.managerSubmittedAt = managerSubmittedAt; }
    public String getHrNote() { return hrNote; }
    public void setHrNote(String hrNote) { this.hrNote = hrNote; }
    public String getDecidedByName() { return decidedByName; }
    public void setDecidedByName(String decidedByName) { this.decidedByName = decidedByName; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public int getExtensions() { return extensions; }
    public void setExtensions(int extensions) { this.extensions = extensions; }
    public String getHistoryJson() { return historyJson; }
    public void setHistoryJson(String historyJson) { this.historyJson = historyJson; }
    public Long getLetterId() { return letterId; }
    public void setLetterId(Long letterId) { this.letterId = letterId; }
}
