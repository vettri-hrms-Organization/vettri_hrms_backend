package com.haodaone.attendance.shift.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An approved, not-yet-paid overtime entry priced for payroll (amount in INR, never null). */
public record OvertimePayable(Long id, LocalDate workDate, int minutes, BigDecimal amount) {}
