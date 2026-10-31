package com.haodaone.exit;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * An employee's resignation (V59). PENDING_MANAGER -> PENDING_HR -> ACCEPTED; or RETAINED (declined, the
 * employee stays) / WITHDRAWN (by the employee before acceptance) / CANCELLED (by HR after acceptance).
 */
@Entity
@Table(name = "employee_resignation")
public class EmployeeResignation extends BaseEntity {

    public static final String PENDING_MANAGER = "PENDING_MANAGER";
    public static final String PENDING_HR = "PENDING_HR";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String RETAINED = "RETAINED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String CANCELLED = "CANCELLED";
    public static final Set<String> OPEN = Set.of(PENDING_MANAGER, PENDING_HR, ACCEPTED);

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "reason_category", nullable = false, length = 40)
    private String reasonCategory;

    @Column(length = 2000)
    private String reason;

    @Column(name = "resignation_date", nullable = false)
    private LocalDate resignationDate;

    @Column(name = "notice_period_days", nullable = false)
    private int noticePeriodDays;

    @Column(name = "policy_last_working_date", nullable = false)
    private LocalDate policyLastWorkingDate;

    @Column(name = "requested_last_working_date")
    private LocalDate requestedLastWorkingDate;

    @Column(name = "approved_last_working_date")
    private LocalDate approvedLastWorkingDate;

    @Column(name = "manager_employee_id")
    private Long managerEmployeeId;

    @Column(name = "manager_decision_by", length = 150)
    private String managerDecisionBy;

    @Column(name = "manager_decision_at")
    private LocalDateTime managerDecisionAt;

    @Column(name = "manager_note", length = 1000)
    private String managerNote;

    @Column(name = "manager_recommended_last_working_date")
    private LocalDate managerRecommendedLastWorkingDate;

    @Column(name = "hr_decision_by", length = 150)
    private String hrDecisionBy;

    @Column(name = "hr_decision_at")
    private LocalDateTime hrDecisionAt;

    @Column(name = "hr_note", length = 1000)
    private String hrNote;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "interview_answers", columnDefinition = "TEXT")
    private String interviewAnswers;

    @Column(name = "interview_completed_at")
    private LocalDateTime interviewCompletedAt;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReasonCategory() { return reasonCategory; }
    public void setReasonCategory(String reasonCategory) { this.reasonCategory = reasonCategory; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDate getResignationDate() { return resignationDate; }
    public void setResignationDate(LocalDate resignationDate) { this.resignationDate = resignationDate; }
    public int getNoticePeriodDays() { return noticePeriodDays; }
    public void setNoticePeriodDays(int noticePeriodDays) { this.noticePeriodDays = noticePeriodDays; }
    public LocalDate getPolicyLastWorkingDate() { return policyLastWorkingDate; }
    public void setPolicyLastWorkingDate(LocalDate policyLastWorkingDate) { this.policyLastWorkingDate = policyLastWorkingDate; }
    public LocalDate getRequestedLastWorkingDate() { return requestedLastWorkingDate; }
    public void setRequestedLastWorkingDate(LocalDate requestedLastWorkingDate) { this.requestedLastWorkingDate = requestedLastWorkingDate; }
    public LocalDate getApprovedLastWorkingDate() { return approvedLastWorkingDate; }
    public void setApprovedLastWorkingDate(LocalDate approvedLastWorkingDate) { this.approvedLastWorkingDate = approvedLastWorkingDate; }
    public Long getManagerEmployeeId() { return managerEmployeeId; }
    public void setManagerEmployeeId(Long managerEmployeeId) { this.managerEmployeeId = managerEmployeeId; }
    public String getManagerDecisionBy() { return managerDecisionBy; }
    public void setManagerDecisionBy(String managerDecisionBy) { this.managerDecisionBy = managerDecisionBy; }
    public LocalDateTime getManagerDecisionAt() { return managerDecisionAt; }
    public void setManagerDecisionAt(LocalDateTime managerDecisionAt) { this.managerDecisionAt = managerDecisionAt; }
    public String getManagerNote() { return managerNote; }
    public void setManagerNote(String managerNote) { this.managerNote = managerNote; }
    public LocalDate getManagerRecommendedLastWorkingDate() { return managerRecommendedLastWorkingDate; }
    public void setManagerRecommendedLastWorkingDate(LocalDate d) { this.managerRecommendedLastWorkingDate = d; }
    public String getHrDecisionBy() { return hrDecisionBy; }
    public void setHrDecisionBy(String hrDecisionBy) { this.hrDecisionBy = hrDecisionBy; }
    public LocalDateTime getHrDecisionAt() { return hrDecisionAt; }
    public void setHrDecisionAt(LocalDateTime hrDecisionAt) { this.hrDecisionAt = hrDecisionAt; }
    public String getHrNote() { return hrNote; }
    public void setHrNote(String hrNote) { this.hrNote = hrNote; }
    public LocalDateTime getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(LocalDateTime withdrawnAt) { this.withdrawnAt = withdrawnAt; }
    public String getInterviewAnswers() { return interviewAnswers; }
    public void setInterviewAnswers(String interviewAnswers) { this.interviewAnswers = interviewAnswers; }
    public LocalDateTime getInterviewCompletedAt() { return interviewCompletedAt; }
    public void setInterviewCompletedAt(LocalDateTime interviewCompletedAt) { this.interviewCompletedAt = interviewCompletedAt; }
}
