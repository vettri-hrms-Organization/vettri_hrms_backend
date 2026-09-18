package com.haodaone.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class AttendanceSessionDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String status;
    private LocalDate attendanceDate;
    private OffsetDateTime checkInTime;
    private OffsetDateTime checkOutTime;
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
    public OffsetDateTime getCheckInTime() { return checkInTime; }
    public void setCheckInTime(LocalDateTime checkInTime) { this.checkInTime = toIstOffset(checkInTime); }
    public OffsetDateTime getCheckOutTime() { return checkOutTime; }
    public void setCheckOutTime(LocalDateTime checkOutTime) { this.checkOutTime = toIstOffset(checkOutTime); }
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

    private static OffsetDateTime toIstOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
    }
}
