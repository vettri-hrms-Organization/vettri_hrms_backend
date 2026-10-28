package com.haodaone.attendance.shift.dto;

import java.time.LocalDate;
import java.util.List;

public record RosterDTO(LocalDate from, LocalDate to, List<ShiftDTO> shifts, List<Row> rows) {

    public record Row(Long employeeId, String employeeCode, String employeeName, String departmentName,
                      List<Cell> cells) {}

    /**
     * One day for one employee. rostered = an explicit roster entry exists for the date;
     * otherwise shiftId/weekOff show what the assignment / company default resolves to.
     */
    public record Cell(LocalDate date, Long shiftId, String shiftCode, String color, boolean weekOff,
                       boolean rostered, String source) {}
}
