package com.haodaone.software.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "software_deployment_stage_event")
public class SoftwareDeploymentStageEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private SoftwareDeploymentTarget target;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private SoftwareDeploymentStatus status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public Long getId() { return id; }
    public SoftwareDeploymentTarget getTarget() { return target; }
    public void setTarget(SoftwareDeploymentTarget target) { this.target = target; }
    public SoftwareDeploymentStatus getStatus() { return status; }
    public void setStatus(SoftwareDeploymentStatus status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
