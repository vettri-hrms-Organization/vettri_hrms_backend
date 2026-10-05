package com.haodaone.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttendanceRegularizationReviewRequest(
        @NotNull Boolean approved,
        @Size(max = 500) String note) {
}
