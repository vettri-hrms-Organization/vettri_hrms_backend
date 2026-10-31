package com.haodaone.performance.suite;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A performance improvement plan (V66): ACTIVE -> SUCCESSFUL / EXTENDED / UNSUCCESSFUL, or CANCELLED. */
@Entity
@Table(name = "improvement_plan")
public class ImprovementPlan extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "owner_employee_id")
    private Long ownerEmployeeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", nullable = false, length = 2000)
    private String reason;

    @Column(name = "expectations_json", nullable = false, columnDefinition = "TEXT")
    private String expectationsJson;

    @Column(name = "checkins_json", columnDefinition = "TEXT")
    private String checkinsJson;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "outcome_note", length = 1000)
    private String outcomeNote;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public Long getOwnerEmployeeId() { return ownerEmployeeId; }
    public void setOwnerEmployeeId(Long ownerEmployeeId) { this.ownerEmployeeId = ownerEmployeeId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getExpectationsJson() { return expectationsJson; }
    public void setExpectationsJson(String expectationsJson) { this.expectationsJson = expectationsJson; }
    public String getCheckinsJson() { return checkinsJson; }
    public void setCheckinsJson(String checkinsJson) { this.checkinsJson = checkinsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOutcomeNote() { return outcomeNote; }
    public void setOutcomeNote(String outcomeNote) { this.outcomeNote = outcomeNote; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
}
