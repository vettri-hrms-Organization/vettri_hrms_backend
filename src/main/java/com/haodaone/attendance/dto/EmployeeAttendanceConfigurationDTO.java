package com.haodaone.attendance.dto;

public record EmployeeAttendanceConfigurationDTO(
        String attendanceMethodOverride,
        String effectiveAttendanceMethod,
        Long biometricDeviceId,
        String biometricDeviceName,
        String biometricDeviceUserId,
        boolean biometricEnrolled) {
}
