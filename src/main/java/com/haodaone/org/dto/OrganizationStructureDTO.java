package com.haodaone.org.dto;

import java.util.List;

public record OrganizationStructureDTO(
        Long companyId,
        String companyName,
        long employeeCount,
        long departmentCount,
        long teamCount,
        List<DepartmentNode> departments,
        List<EmployeeNode> employees) {

    public record DepartmentNode(
            Long id,
            String name,
            long employeeCount,
            long teamCount,
            List<TeamNode> teams) {
    }

    public record TeamNode(
            Long id,
            String name,
            long employeeCount,
            Long leadEmployeeId,
            String leadEmployeeName,
            List<EmployeeNode> employees) {
    }

    public record EmployeeNode(
            Long id,
            String employeeCode,
            String fullName,
            String profilePhotoUrl,
            String designation,
            String status,
            Long departmentId,
            String departmentName,
            Long teamId,
            String teamName,
            Long reportingManagerId,
            String reportingManagerName) {
    }
}
