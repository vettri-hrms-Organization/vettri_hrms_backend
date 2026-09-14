package com.haodaone.leave.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.time.LocalDate;

/** A company holiday - excluded from leave-day counting (see LeaveRequestService.countBusinessDays). */
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
