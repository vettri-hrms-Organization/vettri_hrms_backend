package com.haodaone.customfield.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/** A company-defined extra attribute on the employee record (e.g. "Blood group", "T-shirt size"). */
@Entity
@Table(name = "custom_field_definition")
public class CustomFieldDefinition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Stable machine key (lowercase snake case); used by import/export and the API. Immutable once created. */
    @Column(name = "field_key", nullable = false, length = 60)
    private String fieldKey;

    @Column(nullable = false, length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    private CustomFieldType fieldType = CustomFieldType.TEXT;

    /** JSON array of option strings for SELECT / MULTISELECT. */
    @Column(columnDefinition = "TEXT")
    private String options;

    @Column(nullable = false)
    private boolean required = false;

    @Column(length = 80)
    private String section;

    @Column(name = "visible_to_employee", nullable = false)
    private boolean visibleToEmployee = true;

    @Column(name = "editable_by_employee", nullable = false)
    private boolean editableByEmployee = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public CustomFieldType getFieldType() { return fieldType; }
    public void setFieldType(CustomFieldType fieldType) { this.fieldType = fieldType; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public boolean isVisibleToEmployee() { return visibleToEmployee; }
    public void setVisibleToEmployee(boolean visibleToEmployee) { this.visibleToEmployee = visibleToEmployee; }
    public boolean isEditableByEmployee() { return editableByEmployee; }
    public void setEditableByEmployee(boolean editableByEmployee) { this.editableByEmployee = editableByEmployee; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
