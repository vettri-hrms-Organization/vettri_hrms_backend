package com.haodaone.monitoring.report.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Every filter from requirement #7 (Report Filters), all optional except the date range. Built once per request by MonitoringReportController and passed through the service layer. */
public class ReportFilter {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final LocalTime fromTime;
    private final LocalTime toTime;
    private final Long employeeId;
    private final String employeeCode;
    private final String employeeName;
    private final Long departmentId;
    private final Long deviceId;
    private final String deviceName;
    private final String applicationName;
    private final String workingMode;

    public ReportFilter(LocalDate startDate, LocalDate endDate, LocalTime fromTime, LocalTime toTime,
                         Long employeeId, String employeeCode, String employeeName, Long departmentId,
                         Long deviceId, String deviceName, String applicationName, String workingMode) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.fromTime = fromTime;
        this.toTime = toTime;
        this.employeeId = employeeId;
        this.employeeCode = blankToNull(employeeCode);
        this.employeeName = blankToNull(employeeName);
        this.departmentId = departmentId;
        this.deviceId = deviceId;
        this.deviceName = blankToNull(deviceName);
        this.applicationName = blankToNull(applicationName);
        this.workingMode = blankToNull(workingMode);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalTime getFromTime() {
        return fromTime;
    }

    public LocalTime getToTime() {
        return toTime;
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

    public Long getDepartmentId() {
        return departmentId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getApplicationName() { return applicationName; }
    public String getWorkingMode() { return workingMode; }
}
