package com.haodaone.helpdesk;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** A helpdesk ticket category (V67) with its default owner and resolution SLA. */
@Entity
@Table(name = "helpdesk_category")
public class HelpdeskCategory extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "assignee_employee_id")
    private Long assigneeEmployeeId;

    @Column(name = "sla_hours", nullable = false)
    private int slaHours = 48;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getAssigneeEmployeeId() { return assigneeEmployeeId; }
    public void setAssigneeEmployeeId(Long assigneeEmployeeId) { this.assigneeEmployeeId = assigneeEmployeeId; }
    public int getSlaHours() { return slaHours; }
    public void setSlaHours(int slaHours) { this.slaHours = slaHours; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
