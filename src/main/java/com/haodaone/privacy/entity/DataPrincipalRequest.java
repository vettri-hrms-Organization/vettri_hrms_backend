package com.haodaone.privacy.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A data principal (employee) rights request under the DPDP Act 2023:
 * ACCESS (copy of personal data), CORRECTION (free text, handled by HR) or
 * ERASURE (anonymisation, only after exit + retention period).
 */
@Entity
@Table(name = "data_principal_request")
public class DataPrincipalRequest extends BaseEntity {

    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_CORRECTION = "CORRECTION";
    public static final String TYPE_ERASURE = "ERASURE";

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_REJECTED = "REJECTED";

    /** Response SLA in days. */
    public static final int SLA_DAYS = 30;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType;

    @Column(nullable = false, length = 20)
    private String status = STATUS_SUBMITTED;

    @Column(length = 4000)
    private String details;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "resolution_note", length = 4000)
    private String resolutionNote;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "download_count", nullable = false)
    private int downloadCount = 0;

    @Column(name = "last_downloaded_at")
    private LocalDateTime lastDownloadedAt;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(Long requestedByUserId) { this.requestedByUserId = requestedByUserId; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public Long getResolvedByUserId() { return resolvedByUserId; }
    public void setResolvedByUserId(Long resolvedByUserId) { this.resolvedByUserId = resolvedByUserId; }
    public LocalDateTime getReadyAt() { return readyAt; }
    public void setReadyAt(LocalDateTime readyAt) { this.readyAt = readyAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }
    public LocalDateTime getLastDownloadedAt() { return lastDownloadedAt; }
    public void setLastDownloadedAt(LocalDateTime lastDownloadedAt) { this.lastDownloadedAt = lastDownloadedAt; }
}
