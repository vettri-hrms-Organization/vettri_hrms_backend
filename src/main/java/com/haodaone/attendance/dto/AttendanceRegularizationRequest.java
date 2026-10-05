package com.haodaone.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceRegularizationRequest(
        @NotNull LocalDate attendanceDate,
        @NotNull LocalTime checkInTime,
        LocalTime checkOutTime,
        @NotBlank @Size(max = 500) String reason) {
}
