package com.haodaone.performance.suite;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** A proposed raise for one employee in a compensation round (V66). */
@Entity
@Table(name = "comp_recommendation")
public class CompRecommendation extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "comp_cycle_id", nullable = false)
    private Long compCycleId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "current_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentGross;

    @Column(name = "rating", precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(name = "proposed_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal proposedPercent;

    @Column(name = "new_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal newGross;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "revision_id")
    private Long revisionId;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getCompCycleId() { return compCycleId; }
    public void setCompCycleId(Long compCycleId) { this.compCycleId = compCycleId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public BigDecimal getCurrentGross() { return currentGross; }
    public void setCurrentGross(BigDecimal currentGross) { this.currentGross = currentGross; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public BigDecimal getProposedPercent() { return proposedPercent; }
    public void setProposedPercent(BigDecimal proposedPercent) { this.proposedPercent = proposedPercent; }
    public BigDecimal getNewGross() { return newGross; }
    public void setNewGross(BigDecimal newGross) { this.newGross = newGross; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long revisionId) { this.revisionId = revisionId; }
}
