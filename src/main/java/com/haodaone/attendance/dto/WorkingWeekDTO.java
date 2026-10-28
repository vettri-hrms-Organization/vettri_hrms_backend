package com.haodaone.attendance.dto;

import java.util.List;

/**
 * Company weekly-off configuration. Each entry is a weekday ("SUNDAY" = off every week) or a
 * weekday with the week of the month ("SATURDAY#2" = 2nd Saturday off). Empty = no weekly offs.
 */
public record WorkingWeekDTO(List<String> weeklyOffDays) {
}
