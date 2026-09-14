package com.haodaone.org.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/** A working group within a department, e.g. "Payments Squad" inside "Engineering". */
@Entity
@Table(name = "team")
public class Team extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Plain id, same reasoning as Department.headEmployeeId - avoids a circular entity reference with Employee. */
    @Column(name = "lead_employee_id")
    private Long leadEmployeeId;

    @Column(nullable = false)
    private boolean active = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Long getLeadEmployeeId() {
        return leadEmployeeId;
    }

    public void setLeadEmployeeId(Long leadEmployeeId) {
        this.leadEmployeeId = leadEmployeeId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
