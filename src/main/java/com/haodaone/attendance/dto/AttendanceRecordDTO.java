package com.haodaone.attendance.dto;

import com.haodaone.attendance.entity.AttendanceRecord;

import java.time.LocalDateTime;

public class AttendanceRecordDTO {
    private Long id;
    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private String departmentName;
    private LocalDateTime punchTime;
    private String punchType;
    private String verifyMode;
    private String deviceName;
    private String status;
    private boolean mapped;

    public static AttendanceRecordDTO from(AttendanceRecord record) {
        AttendanceRecordDTO dto = new AttendanceRecordDTO();
        dto.id = record.getId();
        dto.punchTime = record.getPunchTime();
        dto.punchType = record.getPunchType();
        dto.verifyMode = record.getVerifyMode();
        dto.deviceName = record.getDeviceName();
        dto.status = record.getStatus();
        dto.mapped = record.getEmployee() != null;

        if (record.getEmployee() != null) {
            dto.employeeId = record.getEmployee().getId();
            dto.employeeCode = record.getEmployee().getEmployeeCode();
            dto.employeeName = record.getEmployee().getFullName();
            dto.departmentName = record.getEmployee().getDepartment() != null
                    ? record.getEmployee().getDepartment().getName() : null;
        } else {
            dto.employeeCode = record.getDeviceUserId();
            dto.employeeName = "Unmapped (PIN " + record.getDeviceUserId() + ")";
        }
        return dto;
    }

    public Long getId() {
        return id;
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

    public LocalDateTime getPunchTime() {
        return punchTime;
    }

    public String getPunchType() {
        return punchType;
    }

    public String getVerifyMode() {
        return verifyMode;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getStatus() {
        return status;
    }

    public boolean isMapped() {
        return mapped;
    }
}
