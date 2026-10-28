package com.haodaone.onboarding.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A running onboarding / offboarding checklist for one employee, instantiated from a template. */
@Entity
@Table(name = "employee_checklist")
public class EmployeeChecklist extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private ChecklistTemplate template;

    @Column(name = "checklist_type", nullable = false, length = 20)
    private String checklistType;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    private String status = ChecklistConstants.STATUS_IN_PROGRESS;

    /** Joining date (ONBOARDING) or last working date (EXIT); task due dates are offsets from it. */
    @Column(name = "anchor_date", nullable = false)
    private LocalDate anchorDate;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<EmployeeChecklistTask> tasks = new ArrayList<>();

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public ChecklistTemplate getTemplate() { return template; }
    public void setTemplate(ChecklistTemplate template) { this.template = template; }
    public String getChecklistType() { return checklistType; }
    public void setChecklistType(String checklistType) { this.checklistType = checklistType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getAnchorDate() { return anchorDate; }
    public void setAnchorDate(LocalDate anchorDate) { this.anchorDate = anchorDate; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public List<EmployeeChecklistTask> getTasks() { return tasks; }
    public void setTasks(List<EmployeeChecklistTask> tasks) { this.tasks = tasks; }
}
