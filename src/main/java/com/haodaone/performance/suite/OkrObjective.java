package com.haodaone.performance.suite;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** An objective (V66): company, department or personal level; progress = average of its key results. */
@Entity
@Table(name = "okr_objective")
public class OkrObjective extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "level", nullable = false, length = 12)
    private String level;

    @Column(name = "owner_employee_id")
    private Long ownerEmployeeId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "period", nullable = false, length = 10)
    private String period;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "progress", nullable = false, precision = 5, scale = 1)
    private BigDecimal progress = BigDecimal.ZERO;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public Long getOwnerEmployeeId() { return ownerEmployeeId; }
    public void setOwnerEmployeeId(Long ownerEmployeeId) { this.ownerEmployeeId = ownerEmployeeId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getProgress() { return progress; }
    public void setProgress(BigDecimal progress) { this.progress = progress; }
}
