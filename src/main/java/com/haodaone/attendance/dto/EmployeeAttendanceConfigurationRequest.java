package com.haodaone.attendance.dto;

import jakarta.validation.constraints.Size;

public record EmployeeAttendanceConfigurationRequest(
        String attendanceMethodOverride,
        Long biometricDeviceId,
        @Size(max = 30) String biometricDeviceUserId) {
}
