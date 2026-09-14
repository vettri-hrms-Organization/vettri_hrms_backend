package com.haodaone.software.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "software_deployment")
public class SoftwareDeployment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "software_version_id", nullable = false)
    private SoftwareVersion softwareVersion;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SoftwareDeploymentStatus status = SoftwareDeploymentStatus.PENDING;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "note", length = 2000)
    private String note;

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public SoftwareVersion getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(SoftwareVersion softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public SoftwareDeploymentStatus getStatus() {
        return status;
    }

    public void setStatus(SoftwareDeploymentStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
