package com.haodaone.monitoring.report.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One row of the Productivity Summary / Activity Report - a single
 * employee, on a single device, on a single calendar day. Built by
 * ProductivityReportService by grouping ActivitySession rows; never
 * persisted, always computed on demand from real agent activity data.
 *
 * Idle-vs-break split: an idle session shorter than BREAK_THRESHOLD_SECONDS
 * (see ProductivityReportService) counts as Idle Time (the employee is at
 * their desk but momentarily inactive); an idle session at or above that
 * threshold counts as Break Time (a deliberate away-from-desk period). This
 * is a configurable heuristic, not something the agent reports directly -
 * ActivitySession only records isIdleSession, not "was this a break".
 */
public class ProductivitySummaryDTO {

    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private String departmentName;
    private String designationTitle;
    private Long deviceId;
    private String deviceName;
    private LocalDate date;
    private LocalDateTime loginTime;
    private LocalDateTime logoutTime;
    private long totalLoggedInSeconds;
    private long activeSeconds;
    private long idleSeconds;
    private long breakSeconds;
    private double productivityPercent;
    private String productivityClassification;
    private List<AppUsageDTO> topApplications;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getDesignationTitle() {
        return designationTitle;
    }

    public void setDesignationTitle(String designationTitle) {
        this.designationTitle = designationTitle;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }

    public LocalDateTime getLogoutTime() {
        return logoutTime;
    }

    public void setLogoutTime(LocalDateTime logoutTime) {
        this.logoutTime = logoutTime;
    }

    public long getTotalLoggedInSeconds() {
        return totalLoggedInSeconds;
    }

    public void setTotalLoggedInSeconds(long totalLoggedInSeconds) {
        this.totalLoggedInSeconds = totalLoggedInSeconds;
    }

    public long getActiveSeconds() {
        return activeSeconds;
    }

    public void setActiveSeconds(long activeSeconds) {
        this.activeSeconds = activeSeconds;
    }

    public long getIdleSeconds() {
        return idleSeconds;
    }

    public void setIdleSeconds(long idleSeconds) {
        this.idleSeconds = idleSeconds;
    }

    public long getBreakSeconds() {
        return breakSeconds;
    }

    public void setBreakSeconds(long breakSeconds) {
        this.breakSeconds = breakSeconds;
    }

    public double getProductivityPercent() {
        return productivityPercent;
    }

    public void setProductivityPercent(double productivityPercent) {
        this.productivityPercent = productivityPercent;
    }

    public String getProductivityClassification() { return productivityClassification; }
    public void setProductivityClassification(String productivityClassification) { this.productivityClassification = productivityClassification; }

    public List<AppUsageDTO> getTopApplications() {
        return topApplications;
    }

    public void setTopApplications(List<AppUsageDTO> topApplications) {
        this.topApplications = topApplications;
    }
}
