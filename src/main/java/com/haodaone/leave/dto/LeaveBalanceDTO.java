package com.haodaone.leave.dto;

public class LeaveBalanceDTO {
    private Long leaveTypeId;
    private String leaveTypeName;
    private int year;
    private double allocatedDays;
    private double carriedForwardDays;
    private double usedDays;
    private double remainingDays;

    public LeaveBalanceDTO(Long leaveTypeId, String leaveTypeName, int year, double allocatedDays,
                            double carriedForwardDays, double usedDays) {
        this.leaveTypeId = leaveTypeId;
        this.leaveTypeName = leaveTypeName;
        this.year = year;
        this.allocatedDays = allocatedDays;
        this.carriedForwardDays = carriedForwardDays;
        this.usedDays = usedDays;
        this.remainingDays = (allocatedDays + carriedForwardDays) - usedDays;
    }

    public Long getLeaveTypeId() {
        return leaveTypeId;
    }

    public String getLeaveTypeName() {
        return leaveTypeName;
    }

    public int getYear() {
        return year;
    }

    public double getAllocatedDays() {
        return allocatedDays;
    }

    public double getCarriedForwardDays() {
        return carriedForwardDays;
    }

    public double getUsedDays() {
        return usedDays;
    }

    public double getRemainingDays() {
        return remainingDays;
    }
}
