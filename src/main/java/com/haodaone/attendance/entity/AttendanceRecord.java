package com.haodaone.attendance.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A single fingerprint punch pushed by a biometric device in ADMS mode.
 *
 * employee is nullable: a device can push a punch for a PIN that hasn't
 * been mapped to an Employee yet (device enrollment and HR onboarding
 * aren't guaranteed to happen in the same order). Unmapped punches are
 * still captured - see AttendanceController's /unmapped endpoint - so
 * nothing is silently dropped while HR catches up on mapping.
 *
 * (device_serial_number, device_user_id, punch_time) is unique because
 * ADMS devices resend buffered logs until acknowledged, so the same punch
 * can legitimately arrive more than once.
 */
@Entity
@Table(name = "attendance_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_dedupe",
                columnNames = {"device_serial_number", "device_user_id", "punch_time"}))
public class AttendanceRecord extends BaseEntity {

    /**
     * The shared attendance_record.employee_id column is HaodaAsset's
     * human-readable employee code (varchar), not this app's Employee
     * surrogate bigint id - joining on Employee.id here would fail
     * Hibernate schema validation (column type mismatch: varchar vs
     * bigint). referencedColumnName points the join at Employee.employeeCode
     * (see Employee.java, also mapped to a column named "employee_id")
     * instead of the default target of the association's @Id.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", referencedColumnName = "employee_id")
    private Employee employee;

    /** Tenant/company this attendance record belongs to (nullable for legacy rows until migration is applied). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private com.haodaone.company.entity.Company company;

    /** Raw PIN as sent by the device - kept even when resolved to an Employee, for audit/debug. */
    @Column(name = "device_user_id", nullable = false, length = 30)
    private String deviceUserId;

    /**
     * The shared attendance_record.employee_name column is NOT NULL (a
     * HaodaAsset-era requirement) but has no DB default, so every punch
     * this app writes must populate it explicitly - either the resolved
     * Employee's full name, or an "Unmapped (PIN ...)" placeholder,
     * mirroring AttendanceRecordDTO's own display fallback.
     */
    @Column(name = "employee_name", nullable = false, length = 255)
    private String employeeName;

    @Column(name = "punch_time", nullable = false)
    private LocalDateTime punchTime;

    /** IN, OUT, or UNKNOWN */
    @Column(name = "punch_type", nullable = false, length = 10)
    private String punchType;

    @Column(name = "verify_mode", length = 30)
    private String verifyMode;

    @Column(name = "device_serial_number", nullable = false, length = 50)
    private String deviceSerialNumber;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    /** RECEIVED, CORRECTED (Phase 2 keeps this simple; correction workflow is a later refinement) */
    @Column(nullable = false, length = 20)
    private String status = "RECEIVED";

    @Column(name = "raw_line", length = 500)
    private String rawLine;

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public com.haodaone.company.entity.Company getCompany() {
        return company;
    }

    public void setCompany(com.haodaone.company.entity.Company company) {
        this.company = company;
    }

    public String getDeviceUserId() {
        return deviceUserId;
    }

    public void setDeviceUserId(String deviceUserId) {
        this.deviceUserId = deviceUserId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public LocalDateTime getPunchTime() {
        return punchTime;
    }

    public void setPunchTime(LocalDateTime punchTime) {
        this.punchTime = punchTime;
    }

    public String getPunchType() {
        return punchType;
    }

    public void setPunchType(String punchType) {
        this.punchType = punchType;
    }

    public String getVerifyMode() {
        return verifyMode;
    }

    public void setVerifyMode(String verifyMode) {
        this.verifyMode = verifyMode;
    }

    public String getDeviceSerialNumber() {
        return deviceSerialNumber;
    }

    public void setDeviceSerialNumber(String deviceSerialNumber) {
        this.deviceSerialNumber = deviceSerialNumber;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }
}
