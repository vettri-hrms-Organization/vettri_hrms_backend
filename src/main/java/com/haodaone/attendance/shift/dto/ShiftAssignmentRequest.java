package com.haodaone.attendance.shift.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Exactly one of departmentId / employeeId must be set. */
public record ShiftAssignmentRequest(@NotNull Long shiftId, Long departmentId, Long employeeId,
                                     @NotNull LocalDate effectiveFrom) {}
