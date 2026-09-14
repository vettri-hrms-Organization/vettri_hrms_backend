package com.haodaone.employee.dto;

import java.util.List;

public record EmployeeImportRowDTO(int row, String employeeCode, String firstName, String lastName,
                                   String email, String department, String designation, String status,
                                   List<String> errors) {
    public boolean valid() { return errors == null || errors.isEmpty(); }
}