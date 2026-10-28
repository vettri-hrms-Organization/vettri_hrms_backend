package com.haodaone.leave.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

/** An OPTIONAL holiday an employee picked. Un-picking soft-deletes the row. */
@Entity
@Table(name = "optional_holiday_selection", indexes = {
        @Index(name = "idx_optional_holiday_sel_emp_year", columnList = "company_id, employee_id, holiday_year")
})
public class OptionalHolidaySelection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holiday_id", nullable = false)
    private Holiday holiday;

    @Column(name = "holiday_year", nullable = false)
    private int holidayYear;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public Holiday getHoliday() { return holiday; }
    public void setHoliday(Holiday holiday) { this.holiday = holiday; }
    public int getHolidayYear() { return holidayYear; }
    public void setHolidayYear(int holidayYear) { this.holidayYear = holidayYear; }
}
