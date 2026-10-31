package com.haodaone.performance.suite;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

/** A review cycle (V66): DRAFT -> SELF_REVIEW -> MANAGER_REVIEW -> CALIBRATION -> RELEASED. */
@Entity
@Table(name = "performance_cycle")
public class PerformanceCycle extends BaseEntity {

    public static final String DRAFT = "DRAFT";
    public static final String SELF_REVIEW = "SELF_REVIEW";
    public static final String MANAGER_REVIEW = "MANAGER_REVIEW";
    public static final String CALIBRATION = "CALIBRATION";
    public static final String RELEASED = "RELEASED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "self_due")
    private LocalDate selfDue;

    @Column(name = "manager_due")
    private LocalDate managerDue;

    @Column(name = "peer_reviews", nullable = false)
    private boolean peerReviews;

    @Column(name = "created_by_name", length = 150)
    private String createdByName;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getSelfDue() { return selfDue; }
    public void setSelfDue(LocalDate selfDue) { this.selfDue = selfDue; }
    public LocalDate getManagerDue() { return managerDue; }
    public void setManagerDue(LocalDate managerDue) { this.managerDue = managerDue; }
    public boolean isPeerReviews() { return peerReviews; }
    public void setPeerReviews(boolean peerReviews) { this.peerReviews = peerReviews; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
}
