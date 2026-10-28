package com.haodaone.attendance.shift.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Shift master. A shift whose end time is not after its start time crosses midnight
 * (e.g. 22:00-06:00): it starts on the attendance date and ends the next calendar day.
 */
@Entity
@Table(name = "work_shift", indexes = @Index(name = "idx_work_shift_company", columnList = "company_id, deleted"))
public class WorkShift extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes = 0;

    @Column(name = "grace_in_minutes", nullable = false)
    private int graceInMinutes = 0;

    @Column(name = "grace_out_minutes", nullable = false)
    private int graceOutMinutes = 0;

    @Column(name = "half_day_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal halfDayHours = new BigDecimal("4");

    @Column(name = "full_day_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal fullDayHours = new BigDecimal("8");

    @Column(name = "overtime_enabled", nullable = false)
    private boolean overtimeEnabled = false;

    @Column(name = "min_overtime_minutes", nullable = false)
    private int minOvertimeMinutes = 30;

    @Column(length = 20)
    private String color;

    @Column(name = "company_default", nullable = false)
    private boolean companyDefault = false;

    @Column(nullable = false)
    private boolean active = true;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public int getBreakMinutes() { return breakMinutes; }
    public void setBreakMinutes(int breakMinutes) { this.breakMinutes = breakMinutes; }
    public int getGraceInMinutes() { return graceInMinutes; }
    public void setGraceInMinutes(int graceInMinutes) { this.graceInMinutes = graceInMinutes; }
    public int getGraceOutMinutes() { return graceOutMinutes; }
    public void setGraceOutMinutes(int graceOutMinutes) { this.graceOutMinutes = graceOutMinutes; }
    public BigDecimal getHalfDayHours() { return halfDayHours; }
    public void setHalfDayHours(BigDecimal halfDayHours) { this.halfDayHours = halfDayHours; }
    public BigDecimal getFullDayHours() { return fullDayHours; }
    public void setFullDayHours(BigDecimal fullDayHours) { this.fullDayHours = fullDayHours; }
    public boolean isOvertimeEnabled() { return overtimeEnabled; }
    public void setOvertimeEnabled(boolean overtimeEnabled) { this.overtimeEnabled = overtimeEnabled; }
    public int getMinOvertimeMinutes() { return minOvertimeMinutes; }
    public void setMinOvertimeMinutes(int minOvertimeMinutes) { this.minOvertimeMinutes = minOvertimeMinutes; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isCompanyDefault() { return companyDefault; }
    public void setCompanyDefault(boolean companyDefault) { this.companyDefault = companyDefault; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
