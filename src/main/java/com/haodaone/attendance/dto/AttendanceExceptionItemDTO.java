package com.haodaone.attendance.dto;

import com.haodaone.employee.entity.Employee;

public record AttendanceExceptionItemDTO(
        Long employeeId,
        String employeeName,
        String category,
        String message,
        String workMode,
        String attendanceMethod) {

    public static AttendanceExceptionItemDTO from(
            Employee employee, String category, String message, String workMode, String attendanceMethod) {
        return new AttendanceExceptionItemDTO(
                employee.getId(), employee.getFullName(), category, message, workMode, attendanceMethod);
    }
}
