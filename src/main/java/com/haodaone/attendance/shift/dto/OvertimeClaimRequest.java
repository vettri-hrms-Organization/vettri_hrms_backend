package com.haodaone.attendance.shift.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record OvertimeClaimRequest(@NotNull LocalDate date, @Size(max = 500) String note) {}
