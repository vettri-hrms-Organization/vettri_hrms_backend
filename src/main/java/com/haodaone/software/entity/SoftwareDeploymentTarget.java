package com.haodaone.software.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.employee.entity.Employee;
import com.haodaone.monitoring.entity.MonitoredDevice;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "software_deployment_target", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"deployment_id", "device_id"})
})
public class SoftwareDeploymentTarget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false)
    private SoftwareDeployment deployment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private MonitoredDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SoftwareDeploymentStatus status = SoftwareDeploymentStatus.PENDING;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "installed_version", length = 50)
    private String installedVersion;

    public SoftwareDeployment getDeployment() {
        return deployment;
    }

    public void setDeployment(SoftwareDeployment deployment) {
        this.deployment = deployment;
    }

    public MonitoredDevice getDevice() {
        return device;
    }

    public void setDevice(MonitoredDevice device) {
        this.device = device;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public SoftwareDeploymentStatus getStatus() {
        return status;
    }

    public void setStatus(SoftwareDeploymentStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public String getInstalledVersion() {
        return installedVersion;
    }

    public void setInstalledVersion(String installedVersion) {
        this.installedVersion = installedVersion;
    }
}
