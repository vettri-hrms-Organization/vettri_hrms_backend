package com.haodaone.remotedesktop.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.monitoring.entity.MonitoredDevice;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "remote_desktop_session")
public class RemoteDesktopSession extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "device_id", nullable = false) private MonitoredDevice device;
    @Column(name = "requested_by") private Long requestedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RemoteDesktopStatus status;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "ended_at") private LocalDateTime endedAt;
    @Column(name = "failure_reason", length = 1000) private String failureReason;

    public Company getCompany() { return company; }
    public void setCompany(Company value) { company = value; }
    public MonitoredDevice getDevice() { return device; }
    public void setDevice(MonitoredDevice value) { device = value; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long value) { requestedBy = value; }
    public RemoteDesktopStatus getStatus() { return status; }
    public void setStatus(RemoteDesktopStatus value) { status = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt = value; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime value) { endedAt = value; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String value) { failureReason = value; }
}