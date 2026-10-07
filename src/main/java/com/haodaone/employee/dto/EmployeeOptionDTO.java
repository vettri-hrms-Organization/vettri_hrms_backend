package com.haodaone.employee.dto;

import com.haodaone.employee.entity.Employee;

public record EmployeeOptionDTO(Long id, String fullName) {
    public static EmployeeOptionDTO from(Employee employee) {
        return new EmployeeOptionDTO(employee.getId(), employee.getFullName());
    }
}
