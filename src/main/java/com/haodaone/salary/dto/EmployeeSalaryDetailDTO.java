package com.haodaone.salary.dto;

import com.haodaone.employee.entity.Employee;

import java.util.List;

/** Everything the Salary Details page needs for one employee: profile header + current structure + full history. */
public class EmployeeSalaryDetailDTO {

    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private String email;
    private String profilePhotoUrl;
    private String departmentName;
    private String designationTitle;
    private String employmentStatus;
    private String dateOfJoining;

    private SalaryStructureDTO currentStructure;
    private List<SalaryStructureDTO> structureHistory;
    private List<PayrollItemDTO> payrollHistory;

    public static EmployeeSalaryDetailDTO build(Employee e, SalaryStructureDTO currentStructure,
                                                 List<SalaryStructureDTO> structureHistory, List<PayrollItemDTO> payrollHistory) {
        EmployeeSalaryDetailDTO dto = new EmployeeSalaryDetailDTO();
        dto.employeeId = e.getId();
        dto.employeeCode = e.getEmployeeCode();
        dto.employeeName = e.getFullName();
        dto.email = e.getEmail();
        dto.profilePhotoUrl = e.getProfilePhotoUrl();
        dto.departmentName = e.getDepartment() != null ? e.getDepartment().getName() : null;
        dto.designationTitle = e.getDesignation() != null ? e.getDesignation().getTitle() : null;
        dto.employmentStatus = e.getStatus();
        dto.dateOfJoining = e.getDateOfJoining() != null ? e.getDateOfJoining().toString() : null;
        dto.currentStructure = currentStructure;
        dto.structureHistory = structureHistory;
        dto.payrollHistory = payrollHistory;
        return dto;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getEmail() {
        return email;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getDesignationTitle() {
        return designationTitle;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public String getDateOfJoining() {
        return dateOfJoining;
    }

    public SalaryStructureDTO getCurrentStructure() {
        return currentStructure;
    }

    public List<SalaryStructureDTO> getStructureHistory() {
        return structureHistory;
    }

    public List<PayrollItemDTO> getPayrollHistory() {
        return payrollHistory;
    }
}
