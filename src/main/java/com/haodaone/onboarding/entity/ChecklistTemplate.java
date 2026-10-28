package com.haodaone.onboarding.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/** A reusable onboarding (ONBOARDING) or offboarding (EXIT) checklist definition for a company. */
@Entity
@Table(name = "checklist_template")
public class ChecklistTemplate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "template_type", nullable = false, length = 20)
    private String templateType = ChecklistConstants.TYPE_ONBOARDING;

    @Column(length = 1000)
    private String description;

    /** The template auto-started for its type (one per company and type). */
    @Column(name = "default_template", nullable = false)
    private boolean defaultTemplate;

    @Column(name = "auto_start", nullable = false)
    private boolean autoStart = true;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ChecklistTemplateTask> tasks = new ArrayList<>();

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isDefaultTemplate() { return defaultTemplate; }
    public void setDefaultTemplate(boolean defaultTemplate) { this.defaultTemplate = defaultTemplate; }
    public boolean isAutoStart() { return autoStart; }
    public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<ChecklistTemplateTask> getTasks() { return tasks; }
    public void setTasks(List<ChecklistTemplateTask> tasks) { this.tasks = tasks; }
}
