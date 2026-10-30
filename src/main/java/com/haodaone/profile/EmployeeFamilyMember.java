package com.haodaone.profile;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;

/** A family member on an employee's self-service profile (V57). */
@Entity
@Table(name = "employee_family_member")
public class EmployeeFamilyMember extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 40)
    private String relation;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private boolean dependent;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isDependent() { return dependent; }
    public void setDependent(boolean dependent) { this.dependent = dependent; }
}
