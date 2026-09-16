package com.haodaone.attendance.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_session", indexes = {
        @Index(name = "idx_attendance_session_company_date", columnList = "company_id, attendance_date"),
        @Index(name = "idx_attendance_session_employee_date", columnList = "employee_id, attendance_date"),
        @Index(name = "idx_attendance_session_status", columnList = "status")
})
public class AttendanceSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "CHECKED_OUT";

    @Column(name = "source", length = 30)
    private String source;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "location_type", length = 30)
    private String locationType;

    @Column(name = "location_validation_status", length = 40)
    private String locationValidationStatus;

    @Column(name = "distance_from_office_meters")
    private Double distanceFromOfficeMeters;

    @Column(name = "office_location_id")
    private Long officeLocationId;

    @Column(name = "office_location_name", length = 150)
    private String officeLocationName;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(name = "check_in_source_details", length = 500)
    private String checkInSourceDetails;

    @Column(name = "check_out_source_details", length = 500)
    private String checkOutSourceDetails;

    @Column(name = "duration_minutes")
    private Long durationMinutes;

    @Column(name = "is_wfh", nullable = false)
    private boolean wfh = false;

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
    }

    public LocalDateTime getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(LocalDateTime checkInTime) {
        this.checkInTime = checkInTime;
    }

    public LocalDateTime getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(LocalDateTime checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getLocationType() {
        return locationType;
    }

    public void setLocationType(String locationType) {
        this.locationType = locationType;
    }

    public String getLocationValidationStatus() {
        return locationValidationStatus;
    }

    public void setLocationValidationStatus(String locationValidationStatus) {
        this.locationValidationStatus = locationValidationStatus;
    }

    public Double getDistanceFromOfficeMeters() {
        return distanceFromOfficeMeters;
    }

    public void setDistanceFromOfficeMeters(Double distanceFromOfficeMeters) {
        this.distanceFromOfficeMeters = distanceFromOfficeMeters;
    }

    public Long getOfficeLocationId() {
        return officeLocationId;
    }

    public void setOfficeLocationId(Long officeLocationId) {
        this.officeLocationId = officeLocationId;
    }

    public String getOfficeLocationName() {
        return officeLocationName;
    }

    public void setOfficeLocationName(String officeLocationName) {
        this.officeLocationName = officeLocationName;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public void setAccuracyMeters(Double accuracyMeters) {
        this.accuracyMeters = accuracyMeters;
    }

    public String getCheckInSourceDetails() {
        return checkInSourceDetails;
    }

    public void setCheckInSourceDetails(String checkInSourceDetails) {
        this.checkInSourceDetails = checkInSourceDetails;
    }

    public String getCheckOutSourceDetails() {
        return checkOutSourceDetails;
    }

    public void setCheckOutSourceDetails(String checkOutSourceDetails) {
        this.checkOutSourceDetails = checkOutSourceDetails;
    }

    public Long getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Long durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public boolean isWfh() {
        return wfh;
    }

    public void setWfh(boolean wfh) {
        this.wfh = wfh;
    }
}
