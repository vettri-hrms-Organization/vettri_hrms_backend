package com.haodaone.performance.suite;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A 1:1 meeting between a manager and a report (V66); private notes are the manager's only. */
@Entity
@Table(name = "one_on_one")
public class OneOnOne extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "manager_employee_id", nullable = false)
    private Long managerEmployeeId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "agenda", columnDefinition = "TEXT")
    private String agenda;

    @Column(name = "shared_notes", columnDefinition = "TEXT")
    private String sharedNotes;

    @Column(name = "private_notes", columnDefinition = "TEXT")
    private String privateNotes;

    @Column(name = "action_items", columnDefinition = "TEXT")
    private String actionItems;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getManagerEmployeeId() { return managerEmployeeId; }
    public void setManagerEmployeeId(Long managerEmployeeId) { this.managerEmployeeId = managerEmployeeId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAgenda() { return agenda; }
    public void setAgenda(String agenda) { this.agenda = agenda; }
    public String getSharedNotes() { return sharedNotes; }
    public void setSharedNotes(String sharedNotes) { this.sharedNotes = sharedNotes; }
    public String getPrivateNotes() { return privateNotes; }
    public void setPrivateNotes(String privateNotes) { this.privateNotes = privateNotes; }
    public String getActionItems() { return actionItems; }
    public void setActionItems(String actionItems) { this.actionItems = actionItems; }
}
