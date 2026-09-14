package com.haodaone.employee.dto;

import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;

import java.time.LocalDate;
import java.util.List;

public class EmployeeDetailDTO {
    private Long id;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private String gender;
    private LocalDate dateOfJoining;
    private String employmentType;
    private String status;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String profilePhotoUrl;

    private Long departmentId;
    private String departmentName;
    private Long designationId;
    private String designationTitle;
    private Long teamId;
    private String teamName;

    private Long reportingManagerId;
    private String reportingManagerName;
    private String reportingManagerDesignation;

    private Long linkedUserId;
    private String linkedUsername;
    private String accountStatus;
    private String biometricDeviceUserId;
    private Long companyId;

    /** Populated only by getById, not in list views - who reports to this employee. */
    private List<EmployeeSummaryDTO> directReports;

    public static EmployeeDetailDTO from(Employee e) {
        EmployeeDetailDTO dto = new EmployeeDetailDTO();
        dto.id = e.getId();
        dto.employeeCode = e.getEmployeeCode();
        dto.firstName = e.getFirstName();
        dto.lastName = e.getLastName();
        dto.fullName = e.getFullName();
        dto.email = e.getEmail();
        dto.phone = e.getPhone();
        dto.dateOfBirth = e.getDateOfBirth();
        dto.gender = e.getGender();
        dto.dateOfJoining = e.getDateOfJoining();
        dto.employmentType = e.getEmploymentType();
        dto.status = EmploymentStatus.normalize(e.getStatus());
        dto.address = e.getAddress();
        dto.emergencyContactName = e.getEmergencyContactName();
        dto.emergencyContactPhone = e.getEmergencyContactPhone();
        dto.profilePhotoUrl = e.getProfilePhotoUrl();

        if (e.getDepartment() != null) {
            dto.departmentId = e.getDepartment().getId();
            dto.departmentName = e.getDepartment().getName();
        }
        if (e.getDesignation() != null) {
            dto.designationId = e.getDesignation().getId();
            dto.designationTitle = e.getDesignation().getTitle();
        }
        if (e.getTeam() != null) {
            dto.teamId = e.getTeam().getId();
            dto.teamName = e.getTeam().getName();
        }
        if (e.getReportingManager() != null) {
            dto.reportingManagerId = e.getReportingManager().getId();
            dto.reportingManagerName = e.getReportingManager().getFullName();
            dto.reportingManagerDesignation = e.getReportingManager().getDesignation() != null
                    ? e.getReportingManager().getDesignation().getTitle() : null;
        }
        if (e.getUser() != null) {
            dto.linkedUserId = e.getUser().getId();
            dto.linkedUsername = e.getUser().getUsername();
            dto.accountStatus = e.getUser().getAccountStatus();
        } else {
            dto.accountStatus = "NOT_INVITED";
        }
        dto.biometricDeviceUserId = e.getBiometricDeviceUserId();
        dto.companyId = e.getCompany() != null ? e.getCompany().getId() : null;
        return dto;
    }

    public void setDirectReports(List<EmployeeSummaryDTO> directReports) {
        this.directReports = directReports;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
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

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public LocalDate getDateOfJoining() {
        return dateOfJoining;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public String getStatus() {
        return status;
    }

    public String getAddress() {
        return address;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public Long getDesignationId() {
        return designationId;
    }

    public String getDesignationTitle() {
        return designationTitle;
    }

    public Long getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public Long getReportingManagerId() {
        return reportingManagerId;
    }

    public String getReportingManagerName() {
        return reportingManagerName;
    }

    public String getReportingManagerDesignation() {
        return reportingManagerDesignation;
    }

    public Long getLinkedUserId() {
        return linkedUserId;
    }

    public String getLinkedUsername() {
        return linkedUsername;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public String getBiometricDeviceUserId() {
        return biometricDeviceUserId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public List<EmployeeSummaryDTO> getDirectReports() {
        return directReports;
    }
}
