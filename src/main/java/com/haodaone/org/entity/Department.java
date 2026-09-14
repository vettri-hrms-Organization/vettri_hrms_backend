package com.haodaone.org.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/**
 * Supports a simple hierarchy (parentDepartment) so orgs can model
 * sub-departments (e.g. "Engineering" > "Platform Engineering") without a
 * separate entity. headEmployeeId is a plain Long rather than a JPA
 * relationship to Employee - Department is defined before Employee exists
 * in the dependency graph and Employee already points back at Department,
 * so this avoids a circular entity reference; EmployeeService resolves the
 * name when building DTOs.
 */
@Entity
@Table(name = "department", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class Department extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_department_id")
    private Department parentDepartment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "head_employee_id")
    private Long headEmployeeId;

    @Column(nullable = false)
    private boolean active = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Department getParentDepartment() {
        return parentDepartment;
    }

    public void setParentDepartment(Department parentDepartment) {
        this.parentDepartment = parentDepartment;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Long getHeadEmployeeId() {
        return headEmployeeId;
    }

    public void setHeadEmployeeId(Long headEmployeeId) {
        this.headEmployeeId = headEmployeeId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
