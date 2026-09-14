package com.haodaone.reports.dto;

import java.util.List;

public class LeaveReportDTO {
    private int year;
    private long totalRequests;
    private long approved;
    private long rejected;
    private long pending;
    private long cancelled;
    private double approvalRatePercent;
    private List<LeaveTypeUsage> byLeaveType;
    private List<DepartmentUsage> byDepartment;

    public LeaveReportDTO(int year, long totalRequests, long approved, long rejected, long pending, long cancelled,
                           List<LeaveTypeUsage> byLeaveType, List<DepartmentUsage> byDepartment) {
        this.year = year;
        this.totalRequests = totalRequests;
        this.approved = approved;
        this.rejected = rejected;
        this.pending = pending;
        this.cancelled = cancelled;
        long decided = approved + rejected;
        this.approvalRatePercent = decided == 0 ? 0 : Math.round((approved * 1000.0) / decided) / 10.0;
        this.byLeaveType = byLeaveType;
        this.byDepartment = byDepartment;
    }

    public int getYear() {
        return year;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public long getApproved() {
        return approved;
    }

    public long getRejected() {
        return rejected;
    }

    public long getPending() {
        return pending;
    }

    public long getCancelled() {
        return cancelled;
    }

    public double getApprovalRatePercent() {
        return approvalRatePercent;
    }

    public List<LeaveTypeUsage> getByLeaveType() {
        return byLeaveType;
    }

    public List<DepartmentUsage> getByDepartment() {
        return byDepartment;
    }

    public static class LeaveTypeUsage {
        private final String leaveTypeName;
        private final double approvedDays;

        public LeaveTypeUsage(String leaveTypeName, double approvedDays) {
            this.leaveTypeName = leaveTypeName;
            this.approvedDays = approvedDays;
        }

        public String getLeaveTypeName() {
            return leaveTypeName;
        }

        public double getApprovedDays() {
            return approvedDays;
        }
    }

    public static class DepartmentUsage {
        private final String departmentName;
        private final double approvedDays;

        public DepartmentUsage(String departmentName, double approvedDays) {
            this.departmentName = departmentName;
            this.approvedDays = approvedDays;
        }

        public String getDepartmentName() {
            return departmentName;
        }

        public double getApprovedDays() {
            return approvedDays;
        }
    }
}
