package com.haodaone.attendance.shift.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LateEarlyReportDTO(LocalDate from, LocalDate to, boolean latePenaltyEnabled,
                                 int lateMarksPerPenalty, BigDecimal latePenaltyDays,
                                 List<DailyAttendanceDTO> rows, List<Summary> summary) {

    /** penaltyDays is only filled when the range lies within a single calendar month and the policy is on. */
    public record Summary(Long employeeId, String employeeCode, String employeeName, String departmentName,
                          int lateCount, int lateMinutes, int earlyCount, int earlyMinutes,
                          BigDecimal penaltyDays) {}
}
