package com.haodaone.attendance.shift.dto;

import java.math.BigDecimal;

public record ShiftSettingsDTO(String overtimeRateType, BigDecimal overtimeMultiplier,
                               BigDecimal overtimeFlatHourlyRate, boolean latePenaltyEnabled,
                               int lateMarksPerPenalty, BigDecimal latePenaltyDays) {}
