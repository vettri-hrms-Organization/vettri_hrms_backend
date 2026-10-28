package com.haodaone.attendance.shift.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;

/** Per-company overtime pricing and late-mark penalty policy (one row per company, created on first use). */
@Entity
@Table(name = "attendance_shift_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_shift_settings_company", columnNames = "company_id"))
public class AttendanceShiftSettings extends BaseEntity {

    public static final String RATE_MULTIPLIER = "MULTIPLIER";
    public static final String RATE_FLAT = "FLAT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** MULTIPLIER: multiplier x hourly rate (monthly gross / (working days x shift hours)); FLAT: flat amount per hour. */
    @Column(name = "overtime_rate_type", nullable = false, length = 20)
    private String overtimeRateType = RATE_MULTIPLIER;

    @Column(name = "overtime_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal overtimeMultiplier = new BigDecimal("1.5");

    @Column(name = "overtime_flat_hourly_rate", precision = 12, scale = 2)
    private BigDecimal overtimeFlatHourlyRate;

    @Column(name = "late_penalty_enabled", nullable = false)
    private boolean latePenaltyEnabled = false;

    /** Every N late marks in a month cost {@link #latePenaltyDays} days of pay. */
    @Column(name = "late_marks_per_penalty", nullable = false)
    private int lateMarksPerPenalty = 3;

    @Column(name = "late_penalty_days", nullable = false, precision = 4, scale = 2)
    private BigDecimal latePenaltyDays = new BigDecimal("0.5");

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getOvertimeRateType() { return overtimeRateType; }
    public void setOvertimeRateType(String overtimeRateType) { this.overtimeRateType = overtimeRateType; }
    public BigDecimal getOvertimeMultiplier() { return overtimeMultiplier; }
    public void setOvertimeMultiplier(BigDecimal overtimeMultiplier) { this.overtimeMultiplier = overtimeMultiplier; }
    public BigDecimal getOvertimeFlatHourlyRate() { return overtimeFlatHourlyRate; }
    public void setOvertimeFlatHourlyRate(BigDecimal overtimeFlatHourlyRate) { this.overtimeFlatHourlyRate = overtimeFlatHourlyRate; }
    public boolean isLatePenaltyEnabled() { return latePenaltyEnabled; }
    public void setLatePenaltyEnabled(boolean latePenaltyEnabled) { this.latePenaltyEnabled = latePenaltyEnabled; }
    public int getLateMarksPerPenalty() { return lateMarksPerPenalty; }
    public void setLateMarksPerPenalty(int lateMarksPerPenalty) { this.lateMarksPerPenalty = lateMarksPerPenalty; }
    public BigDecimal getLatePenaltyDays() { return latePenaltyDays; }
    public void setLatePenaltyDays(BigDecimal latePenaltyDays) { this.latePenaltyDays = latePenaltyDays; }
}
