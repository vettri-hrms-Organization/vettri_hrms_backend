package com.haodaone.helpdesk;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** An employee's HR helpdesk ticket (V67): OPEN -> IN_PROGRESS -> WAITING_ON_EMPLOYEE -> RESOLVED -> CLOSED. */
@Entity
@Table(name = "helpdesk_ticket")
public class HelpdeskTicket extends BaseEntity {

    public static final String OPEN = "OPEN";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String WAITING_ON_EMPLOYEE = "WAITING_ON_EMPLOYEE";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "ticket_number", nullable = false, length = 20)
    private String ticketNumber;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "priority", nullable = false, length = 10)
    private String priority;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "assignee_employee_id")
    private Long assigneeEmployeeId;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "rating_comment", length = 500)
    private String ratingComment;

    @Column(name = "confidential", nullable = false)
    private boolean confidential;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getAssigneeEmployeeId() { return assigneeEmployeeId; }
    public void setAssigneeEmployeeId(Long assigneeEmployeeId) { this.assigneeEmployeeId = assigneeEmployeeId; }
    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
    public LocalDateTime getFirstResponseAt() { return firstResponseAt; }
    public void setFirstResponseAt(LocalDateTime firstResponseAt) { this.firstResponseAt = firstResponseAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getRatingComment() { return ratingComment; }
    public void setRatingComment(String ratingComment) { this.ratingComment = ratingComment; }
    public boolean isConfidential() { return confidential; }
    public void setConfidential(boolean confidential) { this.confidential = confidential; }
}
