package com.haodaone.leave.dto;

import com.haodaone.leave.entity.LeaveRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class LeaveRequestDTO {
    private Long id;
    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private String departmentName;
    private Long leaveTypeId;
    private String leaveTypeName;
    private LocalDate startDate;
    private LocalDate endDate;
    private double days;
    private String reason;
    private String status;
    private String decidedByName;
    private LocalDateTime decidedAt;
    private String decisionNote;

    public static LeaveRequestDTO from(LeaveRequest lr) {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.id = lr.getId();
        dto.employeeId = lr.getEmployee().getId();
        dto.employeeCode = lr.getEmployee().getEmployeeCode();
        dto.employeeName = lr.getEmployee().getFullName();
        dto.departmentName = lr.getEmployee().getDepartment() != null ? lr.getEmployee().getDepartment().getName() : null;
        dto.leaveTypeId = lr.getLeaveType().getId();
        dto.leaveTypeName = lr.getLeaveType().getName();
        dto.startDate = lr.getStartDate();
        dto.endDate = lr.getEndDate();
        dto.days = lr.getDays();
        dto.reason = lr.getReason();
        dto.status = lr.getStatus();
        dto.decidedByName = lr.getDecidedBy() != null ? lr.getDecidedBy().getFullName() : null;
        dto.decidedAt = lr.getDecidedAt();
        dto.decisionNote = lr.getDecisionNote();
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

    public Long getLeaveTypeId() {
        return leaveTypeId;
    }

    public String getLeaveTypeName() {
        return leaveTypeName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public double getDays() {
        return days;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public String getDecidedByName() {
        return decidedByName;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }
}
