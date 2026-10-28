package com.haodaone.attendance.shift.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record RosterUpdateRequest(@NotNull @Size(max = 5000) List<@Valid CellUpdate> cells) {

    /** clear=true removes the roster entry; otherwise weekOff=true or a shiftId is required. */
    public record CellUpdate(@NotNull Long employeeId, @NotNull LocalDate date, Long shiftId,
                             boolean weekOff, boolean clear) {}
}
