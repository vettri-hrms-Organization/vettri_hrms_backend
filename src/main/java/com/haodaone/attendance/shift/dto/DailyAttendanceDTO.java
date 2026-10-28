package com.haodaone.attendance.shift.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DailyAttendanceDTO(Long employeeId, String employeeCode, String employeeName, String departmentName,
                                 LocalDate date, Long shiftId, String shiftName, String shiftCode, String shiftColor,
                                 String shiftStart, String shiftEnd, String shiftSource,
                                 String status, OffsetDateTime firstIn, OffsetDateTime lastOut,
                                 int workedMinutes, int lateInMinutes, int earlyOutMinutes, int overtimeMinutes,
                                 boolean late, boolean earlyOut, boolean missingOut,
                                 boolean holiday, boolean weekOff, String leave) {}
