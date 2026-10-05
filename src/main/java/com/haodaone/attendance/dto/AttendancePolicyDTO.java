package com.haodaone.attendance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AttendancePolicyDTO(
        @NotBlank String attendanceMethod,
        boolean manualRegularizationEnabled,
        boolean regularizationApprovalRequired,
        @Min(0) @Max(60) int gracePeriodMinutes,
        boolean biometricEnabled,
        boolean remoteCheckInEnabled) {
}
