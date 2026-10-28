package com.haodaone.attendance.shift.dto;

import com.haodaone.common.exception.BadRequestException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** "HH:mm" formatting/parsing for shift times (kept as strings on the wire). */
public final class ShiftTimes {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private ShiftTimes() {}

    public static String format(LocalTime time) {
        return time == null ? null : time.format(HH_MM);
    }

    /** Accepts "HH:mm" or "HH:mm:ss". */
    public static LocalTime parse(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(label + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 5) trimmed = trimmed.substring(0, 5);
        try {
            return LocalTime.parse(trimmed, HH_MM);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(label + " must be a time like 09:30.");
        }
    }
}
