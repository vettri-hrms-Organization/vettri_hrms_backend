package com.haodaone.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class WorkSessionDTO {
    public Long id;
    public Long employeeId;
    public String employeeName;
    public LocalDate sessionDate;
    public LocalDateTime loginTime;
    public LocalDateTime logoutTime;
    public String workingMode;
    public String status;
    public Integer totalWorkingMinutes;

    public static WorkSessionDTO from(com.haodaone.attendance.entity.WorkSession ws) {
        WorkSessionDTO d = new WorkSessionDTO();
        d.id = ws.getId();
        d.employeeId = ws.getEmployee() != null ? ws.getEmployee().getId() : null;
        d.employeeName = ws.getEmployee() != null ? ws.getEmployee().getFullName() : null;
        d.sessionDate = ws.getSessionDate();
        d.loginTime = ws.getLoginTime();
        d.logoutTime = ws.getLogoutTime();
        d.workingMode = ws.getWorkingMode();
        d.status = ws.getStatus();
        d.totalWorkingMinutes = ws.getTotalWorkingMinutes();
        return d;
    }
}
