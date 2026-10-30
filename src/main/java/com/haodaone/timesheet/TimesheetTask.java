package com.haodaone.timesheet;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** A task inside a project (V58), e.g. "Design", "Development". Billable null = inherit the project's. */
@Entity
@Table(name = "timesheet_task")
public class TimesheetTask extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column
    private Boolean billable;

    @Column(nullable = false)
    private boolean active = true;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getBillable() { return billable; }
    public void setBillable(Boolean billable) { this.billable = billable; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
