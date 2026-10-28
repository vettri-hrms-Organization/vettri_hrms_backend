package com.haodaone.attendance.shift.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Copies the 7 roster days starting at sourceWeekStart onto targetWeekStart (and the following
 * weeks when repeatWeeks > 1). employeeIds limits the copy; empty = everyone visible to the caller.
 */
public record RosterCopyWeekRequest(@NotNull LocalDate sourceWeekStart, @NotNull LocalDate targetWeekStart,
                                    Integer repeatWeeks, List<Long> employeeIds) {}
