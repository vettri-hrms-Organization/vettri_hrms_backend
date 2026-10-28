package com.haodaone.customfield.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

/**
 * One employee's value for one custom field, stored as the normalised string
 * produced by CustomFieldValidator (ISO date, plain decimal, true/false, JSON
 * array for MULTISELECT).
 */
@Entity
@Table(name = "employee_custom_field_value")
public class EmployeeCustomFieldValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private CustomFieldDefinition definition;

    @Column(name = "value_text", columnDefinition = "TEXT")
    private String valueText;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public CustomFieldDefinition getDefinition() { return definition; }
    public void setDefinition(CustomFieldDefinition definition) { this.definition = definition; }
    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }
}
