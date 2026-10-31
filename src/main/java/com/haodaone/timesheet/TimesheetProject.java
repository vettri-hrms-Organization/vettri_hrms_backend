package com.haodaone.timesheet;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Something employees log time against (V58): a client project, an internal product, or a bucket such
 * as "Training". Open to every employee when {@code allEmployees}, otherwise only to its members.
 */
@Entity
@Table(name = "timesheet_project")
public class TimesheetProject extends BaseEntity {

    public static final String ACTIVE = "ACTIVE";
    public static final String ARCHIVED = "ARCHIVED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 30)
    private String code;

    @Column(name = "client_name", length = 150)
    private String clientName;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean billable;

    @Column(nullable = false, length = 20)
    private String status = ACTIVE;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "budget_minutes")
    private Integer budgetMinutes;

    @Column(name = "all_employees", nullable = false)
    private boolean allEmployees;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "timesheet_project_member", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "employee_id", nullable = false)
    private Set<Long> memberEmployeeIds = new HashSet<>();

    /** Hourly rate billed to the client (V68); null = not billed. Per-member overrides live in memberRatesJson. */
    @Column(name = "billing_rate", precision = 12, scale = 2)
    private java.math.BigDecimal billingRate;

    @Column(name = "member_rates_json", columnDefinition = "TEXT")
    private String memberRatesJson;

    public java.math.BigDecimal getBillingRate() { return billingRate; }
    public void setBillingRate(java.math.BigDecimal billingRate) { this.billingRate = billingRate; }
    public String getMemberRatesJson() { return memberRatesJson; }
    public void setMemberRatesJson(String memberRatesJson) { this.memberRatesJson = memberRatesJson; }

    public boolean isOpenTo(Long employeeId) {
        return allEmployees || memberEmployeeIds.contains(employeeId);
    }

    public boolean isActiveOn(LocalDate date) {
        return ACTIVE.equals(status) && !isDeleted()
                && (startDate == null || !date.isBefore(startDate))
                && (endDate == null || !date.isAfter(endDate));
    }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isBillable() { return billable; }
    public void setBillable(boolean billable) { this.billable = billable; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getBudgetMinutes() { return budgetMinutes; }
    public void setBudgetMinutes(Integer budgetMinutes) { this.budgetMinutes = budgetMinutes; }
    public boolean isAllEmployees() { return allEmployees; }
    public void setAllEmployees(boolean allEmployees) { this.allEmployees = allEmployees; }
    public Set<Long> getMemberEmployeeIds() { return memberEmployeeIds; }
    public void setMemberEmployeeIds(Set<Long> memberEmployeeIds) { this.memberEmployeeIds = memberEmployeeIds; }
}
