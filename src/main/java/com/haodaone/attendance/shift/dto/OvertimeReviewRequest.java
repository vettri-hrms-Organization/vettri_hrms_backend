package com.haodaone.attendance.shift.dto;

import jakarta.validation.constraints.Size;

/** approvedMinutes is optional: it defaults to the computed minutes and may only reduce them. */
public record OvertimeReviewRequest(boolean approved, Integer approvedMinutes, @Size(max = 500) String note) {}
