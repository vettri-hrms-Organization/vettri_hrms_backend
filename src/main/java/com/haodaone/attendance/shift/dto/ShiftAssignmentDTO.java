package com.haodaone.attendance.shift.dto;

import com.haodaone.attendance.shift.entity.ShiftAssignment;

import java.time.LocalDate;

public record ShiftAssignmentDTO(Long id, Long shiftId, String shiftName, String shiftCode, String targetType,
                                 Long departmentId, String departmentName,
                                 Long employeeId, String employeeName, String employeeCode,
                                 LocalDate effectiveFrom) {

    public static ShiftAssignmentDTO from(ShiftAssignment a) {
        boolean employee = a.getEmployee() != null;
        return new ShiftAssignmentDTO(a.getId(), a.getShift().getId(), a.getShift().getName(), a.getShift().getCode(),
                employee ? "EMPLOYEE" : "DEPARTMENT",
                a.getDepartment() == null ? null : a.getDepartment().getId(),
                a.getDepartment() == null ? null : a.getDepartment().getName(),
                employee ? a.getEmployee().getId() : null,
                employee ? a.getEmployee().getFullName() : null,
                employee ? a.getEmployee().getEmployeeCode() : null,
                a.getEffectiveFrom());
    }
}
