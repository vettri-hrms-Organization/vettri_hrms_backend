package com.haodaone.attendance.dto;

import com.haodaone.employee.dto.EmployeeSummaryDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * The detailed list categorizes active employees with no punches, including
 * those covered by approved leave. The legacy missingPunch list excludes
 * approved leave. It deliberately does NOT flag lateness or early departure:
 * there is no shift/scheduled-hours concept to compare against.
 */
public class AttendanceExceptionDTO {
    private final LocalDate date;
    private final boolean workingDay;
    private final List<EmployeeSummaryDTO> missingPunch;
    private final List<AttendanceExceptionItemDTO> exceptions;

    public AttendanceExceptionDTO(LocalDate date, boolean workingDay, List<EmployeeSummaryDTO> missingPunch) {
        this(date, workingDay, missingPunch, List.of());
    }

    public AttendanceExceptionDTO(LocalDate date, boolean workingDay, List<EmployeeSummaryDTO> missingPunch,
                                  List<AttendanceExceptionItemDTO> exceptions) {
        this.date = date;
        this.workingDay = workingDay;
        this.missingPunch = missingPunch;
        this.exceptions = exceptions;
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

    public List<AttendanceExceptionItemDTO> getExceptions() {
        return exceptions;
    }
}
