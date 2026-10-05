package com.haodaone.attendance.dto;

import com.haodaone.attendance.entity.AttendanceRegularization;

import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceRegularizationDTO(
        Long id,
        Long employeeId,
        String employeeName,
        LocalDate attendanceDate,
        LocalTime requestedCheckIn,
        LocalTime requestedCheckOut,
        String reason,
        String status,
        String reviewNote) {

    public static AttendanceRegularizationDTO from(AttendanceRegularization request) {
        return new AttendanceRegularizationDTO(
                request.getId(),
                request.getEmployee().getId(),
                request.getEmployee().getFullName(),
                request.getAttendanceDate(),
                request.getRequestedCheckIn(),
                request.getRequestedCheckOut(),
                request.getReason(),
                request.getStatus(),
                request.getReviewNote());
    }
}
