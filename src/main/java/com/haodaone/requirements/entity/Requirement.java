package com.haodaone.requirements.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "requirement")
public class Requirement extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id", nullable = false) private Company company;
    @Column(nullable = false, length = 255) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RequirementStatus status = RequirementStatus.OPEN;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_to_user_id") private User assignedTo;
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "close_reason", length = 1000) private String closeReason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "closed_by") private User closedBy;
    @Column(name = "closed_at") private LocalDateTime closedAt;

    public Company getCompany() { return company; } public void setCompany(Company value) { company = value; }
    public String getTitle() { return title; } public void setTitle(String value) { title = value; }
    public String getDescription() { return description; } public void setDescription(String value) { description = value; }
    public RequirementStatus getStatus() { return status; } public void setStatus(RequirementStatus value) { status = value; }
    public User getAssignedTo() { return assignedTo; } public void setAssignedTo(User value) { assignedTo = value; }
    public LocalDate getDueDate() { return dueDate; } public void setDueDate(LocalDate value) { dueDate = value; }
    public String getCloseReason() { return closeReason; } public void setCloseReason(String value) { closeReason = value; }
    public User getClosedBy() { return closedBy; } public void setClosedBy(User value) { closedBy = value; }
    public LocalDateTime getClosedAt() { return closedAt; } public void setClosedAt(LocalDateTime value) { closedAt = value; }
}