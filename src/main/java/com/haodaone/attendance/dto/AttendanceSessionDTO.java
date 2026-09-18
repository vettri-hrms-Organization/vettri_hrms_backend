package com.haodaone.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AttendanceSessionDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String status;
    private LocalDate attendanceDate;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private String source;
    private String locationType;
    private String locationValidationStatus;
    private Double distanceFromOfficeMeters;
    private String officeLocationName;
    private Long durationMinutes;
    private boolean wfh;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public void setAttendanceDate(LocalDate attendanceDate) { this.attendanceDate = attendanceDate; }
    public LocalDateTime getCheckInTime() { return checkInTime; }
    public void setCheckInTime(LocalDateTime checkInTime) { this.checkInTime = checkInTime; }
    public LocalDateTime getCheckOutTime() { return checkOutTime; }
    public void setCheckOutTime(LocalDateTime checkOutTime) { this.checkOutTime = checkOutTime; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getLocationType() { return locationType; }
    public void setLocationType(String locationType) { this.locationType = locationType; }
    public String getLocationValidationStatus() { return locationValidationStatus; }
    public void setLocationValidationStatus(String locationValidationStatus) { this.locationValidationStatus = locationValidationStatus; }
    public Double getDistanceFromOfficeMeters() { return distanceFromOfficeMeters; }
    public void setDistanceFromOfficeMeters(Double distanceFromOfficeMeters) { this.distanceFromOfficeMeters = distanceFromOfficeMeters; }
    public String getOfficeLocationName() { return officeLocationName; }
    public void setOfficeLocationName(String officeLocationName) { this.officeLocationName = officeLocationName; }
    public Long getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Long durationMinutes) { this.durationMinutes = durationMinutes; }
    public boolean isWfh() { return wfh; }
    public void setWfh(boolean wfh) { this.wfh = wfh; }
}
