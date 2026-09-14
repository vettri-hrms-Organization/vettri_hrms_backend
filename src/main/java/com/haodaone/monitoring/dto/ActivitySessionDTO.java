package com.haodaone.monitoring.dto;

import com.haodaone.monitoring.entity.ActivitySession;

import java.time.LocalDateTime;

public class ActivitySessionDTO {

    private Long id;
    private String sessionId;
    private Long deviceId;
    private String deviceName;
    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private String departmentName;
    private String processName;
    private String applicationName;
    private String windowTitle;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationSeconds;
    private boolean idleSession;

    public static ActivitySessionDTO from(ActivitySession s) {
        ActivitySessionDTO dto = new ActivitySessionDTO();
        dto.id = s.getId();
        dto.sessionId = s.getSessionId();
        dto.deviceId = s.getDevice().getId();
        dto.deviceName = s.getDevice().getDeviceName();
        dto.processName = s.getProcessName();
        dto.applicationName = s.getApplicationName();
        dto.windowTitle = s.getWindowTitle();
        dto.startTime = s.getStartTime();
        dto.endTime = s.getEndTime();
        dto.durationSeconds = s.getDurationSeconds();
        dto.idleSession = s.isIdleSession();
        if (s.getEmployee() != null) {
            dto.employeeId = s.getEmployee().getId();
            dto.employeeCode = s.getEmployee().getEmployeeCode();
            dto.employeeName = s.getEmployee().getFullName();
            dto.departmentName = s.getEmployee().getDepartment() != null ? s.getEmployee().getDepartment().getName() : null;
        }
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getProcessName() {
        return processName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getWindowTitle() {
        return windowTitle;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public boolean isIdleSession() {
        return idleSession;
    }
}
