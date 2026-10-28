package com.haodaone.attendance.shift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ShiftRequest(@NotBlank @Size(max = 100) String name,
                           @NotBlank @Size(max = 30) String code,
                           @NotBlank String startTime,
                           @NotBlank String endTime,
                           Integer breakMinutes,
                           Integer graceInMinutes,
                           Integer graceOutMinutes,
                           BigDecimal halfDayHours,
                           BigDecimal fullDayHours,
                           Boolean overtimeEnabled,
                           Integer minOvertimeMinutes,
                           @Size(max = 20) String color,
                           Boolean active) {}
