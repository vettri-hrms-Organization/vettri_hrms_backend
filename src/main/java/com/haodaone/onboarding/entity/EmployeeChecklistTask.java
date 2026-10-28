package com.haodaone.onboarding.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One task on a running checklist, with its resolved owner and due date. */
@Entity
@Table(name = "employee_checklist_task")
public class EmployeeChecklistTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checklist_id", nullable = false)
    private EmployeeChecklist checklist;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "owner_type", nullable = false, length = 20)
    private String ownerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User ownerUser;

    /** The new hire (NEW_HIRE) or their reporting manager (MANAGER) at start time. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_employee_id")
    private Employee ownerEmployee;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false, length = 20)
    private String status = ChecklistConstants.TASK_PENDING;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by", length = 150)
    private String completedBy;

    @Column(length = 1000)
    private String remarks;

    @Column(name = "requires_document", nullable = false)
    private boolean requiresDocument;

    @Column(name = "document_type", length = 40)
    private String documentType;

    /** EmployeeDocument uploaded for this task. */
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "link_key", length = 40)
    private String linkKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Last day an overdue reminder went out - keeps the daily job idempotent. */
    @Column(name = "last_reminder_on")
    private LocalDate lastReminderOn;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public EmployeeChecklist getChecklist() { return checklist; }
    public void setChecklist(EmployeeChecklist checklist) { this.checklist = checklist; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
    public User getOwnerUser() { return ownerUser; }
    public void setOwnerUser(User ownerUser) { this.ownerUser = ownerUser; }
    public Employee getOwnerEmployee() { return ownerEmployee; }
    public void setOwnerEmployee(Employee ownerEmployee) { this.ownerEmployee = ownerEmployee; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public boolean isRequiresDocument() { return requiresDocument; }
    public void setRequiresDocument(boolean requiresDocument) { this.requiresDocument = requiresDocument; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getLinkKey() { return linkKey; }
    public void setLinkKey(String linkKey) { this.linkKey = linkKey; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public LocalDate getLastReminderOn() { return lastReminderOn; }
    public void setLastReminderOn(LocalDate lastReminderOn) { this.lastReminderOn = lastReminderOn; }
}
