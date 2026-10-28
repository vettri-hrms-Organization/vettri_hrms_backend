package com.haodaone.leave.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/** One row per company: holiday policy. A company without a row uses the defaults below. */
@Entity
@Table(name = "holiday_settings")
public class HolidaySettings extends BaseEntity {

    public static final int DEFAULT_MAX_OPTIONAL = 2;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    /** How many OPTIONAL (restricted) holidays an employee may pick per calendar year. */
    @Column(name = "max_optional_holidays", nullable = false)
    private int maxOptionalHolidays = DEFAULT_MAX_OPTIONAL;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public int getMaxOptionalHolidays() { return maxOptionalHolidays; }
    public void setMaxOptionalHolidays(int maxOptionalHolidays) { this.maxOptionalHolidays = maxOptionalHolidays; }
}
