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

    // ---- Policy-aware fields (V45, filled by LeaveBalanceService) ----
    private String leaveTypeCode;
    private String accrualMode;
    /** Date the accrual figure is computed for ("as of today" for the current year). */
    private java.time.LocalDate accruedAsOf;
    /** Full-year entitlement for this employee (what allocatedDays reaches by Dec 31). */
    private double annualEntitlementDays;
    private double adjustmentDays;
    private double encashedDays;
    /** Carried-forward days past their expiry, or expired comp-off credits. */
    private double lapsedDays;
    /** How far below zero this type may go when applying (policy negative allowance). */
    private double negativeAllowanceDays;
    private Double maxBalanceDays;
    private boolean encashable;
    private boolean systemManaged;

    /**
     * remaining = allocated + carried + adjustments - used - encashed - lapsed. usedDays stays "approved
     * leave days" so existing consumers keep their meaning; encashed days are reported separately but are
     * subtracted from remaining.
     */
    public LeaveBalanceDTO(Long leaveTypeId, String leaveTypeName, String leaveTypeCode, int year, double allocatedDays,
                           double carriedForwardDays, double adjustmentDays, double usedDays, double encashedDays,
                           double lapsedDays) {
        this(leaveTypeId, leaveTypeName, year, allocatedDays, carriedForwardDays, usedDays);
        this.leaveTypeCode = leaveTypeCode;
        this.adjustmentDays = adjustmentDays;
        this.encashedDays = encashedDays;
        this.lapsedDays = lapsedDays;
        this.remainingDays = round2(allocatedDays + carriedForwardDays + adjustmentDays - usedDays - encashedDays - lapsedDays);
    }

    public LeaveBalanceDTO withPolicy(String accrualMode, java.time.LocalDate accruedAsOf, double annualEntitlementDays,
                                      double negativeAllowanceDays, Double maxBalanceDays, boolean encashable, boolean systemManaged) {
        this.accrualMode = accrualMode;
        this.accruedAsOf = accruedAsOf;
        this.annualEntitlementDays = annualEntitlementDays;
        this.negativeAllowanceDays = negativeAllowanceDays;
        this.maxBalanceDays = maxBalanceDays;
        this.encashable = encashable;
        this.systemManaged = systemManaged;
        return this;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public String getLeaveTypeCode() { return leaveTypeCode; }
    public String getAccrualMode() { return accrualMode; }
    public java.time.LocalDate getAccruedAsOf() { return accruedAsOf; }
    public double getAnnualEntitlementDays() { return annualEntitlementDays; }
    public double getAdjustmentDays() { return adjustmentDays; }
    public double getEncashedDays() { return encashedDays; }
    public double getLapsedDays() { return lapsedDays; }
    public double getNegativeAllowanceDays() { return negativeAllowanceDays; }
    public Double getMaxBalanceDays() { return maxBalanceDays; }
    public boolean isEncashable() { return encashable; }
    public boolean isSystemManaged() { return systemManaged; }

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
