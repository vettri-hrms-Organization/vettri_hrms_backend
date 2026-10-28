package com.haodaone.employee.dto;

import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;

import java.time.LocalDate;

/** Lightweight shape for list/table views - EmployeeDetailDTO carries the full profile. */
public class EmployeeSummaryDTO {
    private Long id;
    private String employeeCode;
    private String fullName;
    private String email;
    private String phone;
    private String status;
    private String employmentType;
    private LocalDate dateOfJoining;
    private String departmentName;
    private String designationTitle;
    private String reportingManagerName;
    private String profilePhotoUrl;

    public static EmployeeSummaryDTO from(Employee e) {
        EmployeeSummaryDTO dto = new EmployeeSummaryDTO();
        dto.id = e.getId();
        dto.employeeCode = e.getEmployeeCode();
        dto.fullName = e.getFullName();
        dto.email = e.getEmail();
        dto.phone = e.getPhone();
        dto.status = EmploymentStatus.normalize(e.getStatus());
        dto.employmentType = e.getEmploymentType();
        dto.dateOfJoining = e.getDateOfJoining();
        dto.departmentName = e.getDepartment() != null ? e.getDepartment().getName() : null;
        dto.designationTitle = e.getDesignation() != null ? e.getDesignation().getTitle() : null;
        dto.reportingManagerName = e.getReportingManager() != null ? e.getReportingManager().getFullName() : null;
        dto.profilePhotoUrl = e.getProfilePhotoUrl();
        return dto;
    }

    /** Only populated by the directory listing (EmployeeService#listPaged, inside its transaction) - feeds the employee master export. */
    private String teamName;
    /** The employee import maps "Work Location" onto Employee.address, so that is what is exposed here. */
    private String workLocation;

    public static EmployeeSummaryDTO forDirectory(Employee e) {
        EmployeeSummaryDTO dto = from(e);
        dto.teamName = e.getTeam() != null ? e.getTeam().getName() : null;
        dto.workLocation = e.getAddress();
        return dto;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getWorkLocation() {
        return workLocation;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getStatus() {
        return status;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public LocalDate getDateOfJoining() {
        return dateOfJoining;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getDesignationTitle() {
        return designationTitle;
    }

    public String getReportingManagerName() {
        return reportingManagerName;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }
}
