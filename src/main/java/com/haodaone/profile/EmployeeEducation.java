package com.haodaone.profile;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** A qualification on an employee's self-service profile (V57). */
@Entity
@Table(name = "employee_education")
public class EmployeeEducation extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 120)
    private String qualification;

    @Column(nullable = false, length = 200)
    private String institution;

    @Column(length = 150)
    private String specialization;

    @Column(name = "start_year")
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;

    @Column(length = 30)
    private String grade;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }
    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public Integer getStartYear() { return startYear; }
    public void setStartYear(Integer startYear) { this.startYear = startYear; }
    public Integer getEndYear() { return endYear; }
    public void setEndYear(Integer endYear) { this.endYear = endYear; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
}
