package com.haodaone.attendance.dto;

import java.time.LocalDateTime;

/** Tray attendance for the employee assigned to an enrolled laptop (GET/POST /api/agent/attendance). */
public final class AgentAttendanceDTO {
    private AgentAttendanceDTO() { }

    /**
     * @param state        NOT_ASSIGNED, NOT_CHECKED_IN, CHECKED_IN, ON_BREAK or CHECKED_OUT
     * @param ok           false when the requested action was refused; {@code message} then says why in plain words
     * @param canClockIn   false on office days without WFH approval (office punches need location or biometric)
     */
    public record Status(
            boolean ok,
            String state,
            String message,
            String employeeName,
            String companyName,
            LocalDateTime checkInTime,
            LocalDateTime checkOutTime,
            LocalDateTime breakStartedAt,
            long workedMinutes,
            long breakMinutes,
            boolean canClockIn,
            String portalUrl) { }
}
