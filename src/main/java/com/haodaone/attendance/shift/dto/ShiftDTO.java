package com.haodaone.attendance.shift.dto;

import com.haodaone.attendance.shift.entity.WorkShift;
import com.haodaone.attendance.shift.service.ShiftRule;

import java.math.BigDecimal;

public record ShiftDTO(Long id, String name, String code, String startTime, String endTime,
                       int breakMinutes, int graceInMinutes, int graceOutMinutes,
                       BigDecimal halfDayHours, BigDecimal fullDayHours,
                       boolean overtimeEnabled, int minOvertimeMinutes, String color,
                       boolean companyDefault, boolean active, boolean overnight, int scheduledMinutes) {

    public static ShiftDTO from(WorkShift shift) {
        ShiftRule rule = ShiftRule.from(shift);
        return new ShiftDTO(shift.getId(), shift.getName(), shift.getCode(),
                ShiftTimes.format(shift.getStartTime()), ShiftTimes.format(shift.getEndTime()),
                shift.getBreakMinutes(), shift.getGraceInMinutes(), shift.getGraceOutMinutes(),
                shift.getHalfDayHours(), shift.getFullDayHours(), shift.isOvertimeEnabled(),
                shift.getMinOvertimeMinutes(), shift.getColor(), shift.isCompanyDefault(), shift.isActive(),
                rule.overnight(), rule.scheduledMinutes());
    }
}
