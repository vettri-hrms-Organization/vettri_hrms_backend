package com.haodaone.leave.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * A company holiday - excluded from leave-day counting (see LeaveRequestService.countBusinessDays
 * and HolidayCalendarService). Optionally limited to office locations and/or OPTIONAL (restricted).
 */
@Entity
@Table(name = "holiday")
public class Holiday extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private LocalDate date;

    public static final String MANDATORY = "MANDATORY";
    public static final String OPTIONAL = "OPTIONAL";

    /** MANDATORY (a holiday for everyone it applies to) or OPTIONAL (restricted - only for employees who pick it). */
    @Column(name = "holiday_type", nullable = false, length = 20)
    private String holidayType = MANDATORY;

    /** Office locations this holiday applies to; empty = every location (company-wide). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "holiday_location", joinColumns = @JoinColumn(name = "holiday_id"))
    @Column(name = "office_location_id", nullable = false)
    private java.util.Set<Long> locationIds = new java.util.HashSet<>();

    public String getHolidayType() {
        return holidayType == null ? MANDATORY : holidayType;
    }

    public void setHolidayType(String holidayType) {
        this.holidayType = holidayType == null ? MANDATORY : holidayType;
    }

    public boolean isOptional() {
        return OPTIONAL.equals(getHolidayType());
    }

    public java.util.Set<Long> getLocationIds() {
        return locationIds;
    }

    public void setLocationIds(java.util.Set<Long> locationIds) {
        this.locationIds = locationIds == null ? new java.util.HashSet<>() : new java.util.HashSet<>(locationIds);
    }

    /** True when the holiday applies to an employee at {@code officeLocationId} (null = employee has no location). */
    public boolean appliesToLocation(Long officeLocationId) {
        if (locationIds == null || locationIds.isEmpty()) return true;
        return officeLocationId != null && locationIds.contains(officeLocationId);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
}
