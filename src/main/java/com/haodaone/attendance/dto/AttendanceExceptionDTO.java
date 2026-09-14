package com.haodaone.attendance.dto;

import com.haodaone.employee.dto.EmployeeSummaryDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * "Exception" here means one specific, unambiguous thing: an active
 * employee with zero punches on a day that isn't a weekend, isn't a
 * company holiday, and isn't covered by approved leave. It deliberately
 * does NOT mean "arrived late" or "left early" - there's no shift/
 * scheduled-hours concept anywhere in the data model to compare a punch
 * time against, so flagging lateness would mean inventing a threshold
 * with no real basis. Missing-entirely is the one exception type the
 * existing data can actually support without guessing.
 */
public class AttendanceExceptionDTO {
    private final LocalDate date;
    private final boolean workingDay;
    private final List<EmployeeSummaryDTO> missingPunch;

    public AttendanceExceptionDTO(LocalDate date, boolean workingDay, List<EmployeeSummaryDTO> missingPunch) {
        this.date = date;
        this.workingDay = workingDay;
        this.missingPunch = missingPunch;
    }

    public LocalDate getDate() {
        return date;
    }

    public boolean isWorkingDay() {
        return workingDay;
    }

    public List<EmployeeSummaryDTO> getMissingPunch() {
        return missingPunch;
    }
}
