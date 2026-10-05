package com.haodaone.attendance.dto;

import java.time.LocalDate;

public record AttendanceContextDTO(
        LocalDate date,
        String attendanceMethod,
        String workMode,
        String wfhStatus,
        boolean allowedWebCheckIn,
        boolean requiresBiometric,
        boolean biometricEnrolled,
        String attendanceStatus,
        String latestBiometricPunchType,
        String latestBiometricPunchTime) {
}
