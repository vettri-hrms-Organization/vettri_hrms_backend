package com.haodaone.performance.suite;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A compensation (appraisal) planning round (V66): DRAFT -> APPLIED. */
@Entity
@Table(name = "comp_cycle")
public class CompCycle extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "budget_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal budgetPercent;

    @Column(name = "performance_cycle_id")
    private Long performanceCycleId;

    @Column(name = "guidelines_json", nullable = false, columnDefinition = "TEXT")
    private String guidelinesJson;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "applied_by_name", length = 150)
    private String appliedByName;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public BigDecimal getBudgetPercent() { return budgetPercent; }
    public void setBudgetPercent(BigDecimal budgetPercent) { this.budgetPercent = budgetPercent; }
    public Long getPerformanceCycleId() { return performanceCycleId; }
    public void setPerformanceCycleId(Long performanceCycleId) { this.performanceCycleId = performanceCycleId; }
    public String getGuidelinesJson() { return guidelinesJson; }
    public void setGuidelinesJson(String guidelinesJson) { this.guidelinesJson = guidelinesJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAppliedByName() { return appliedByName; }
    public void setAppliedByName(String appliedByName) { this.appliedByName = appliedByName; }
}
