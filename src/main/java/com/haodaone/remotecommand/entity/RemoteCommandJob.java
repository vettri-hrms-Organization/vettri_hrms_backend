package com.haodaone.remotecommand.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.monitoring.entity.MonitoredDevice;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "remote_command_job")
public class RemoteCommandJob extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private MonitoredDevice device;
    @Column(name = "requested_by") private Long requestedBy;
    @Column(nullable = false, columnDefinition = "TEXT") private String command;
    @Column(name = "shell_type", nullable = false, length = 20) private String shellType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RemoteCommandStatus status = RemoteCommandStatus.QUEUED;
    @Column(columnDefinition = "TEXT") private String stdout;
    @Column(columnDefinition = "TEXT") private String stderr;
    @Column(name = "exit_code") private Integer exitCode;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "timeout_seconds", nullable = false) private Integer timeoutSeconds = 30;
    @Column(name = "error_message", length = 2000) private String errorMessage;
    @Column(name = "correlation_id", nullable = false, unique = true, length = 80) private String correlationId;

    public Company getCompany() { return company; }
    public void setCompany(Company value) { company = value; }
    public MonitoredDevice getDevice() { return device; }
    public void setDevice(MonitoredDevice value) { device = value; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long value) { requestedBy = value; }
    public String getCommand() { return command; }
    public void setCommand(String value) { command = value; }
    public String getShellType() { return shellType; }
    public void setShellType(String value) { shellType = value; }
    public RemoteCommandStatus getStatus() { return status; }
    public void setStatus(RemoteCommandStatus value) { status = value; }
    public String getStdout() { return stdout; }
    public void setStdout(String value) { stdout = value; }
    public String getStderr() { return stderr; }
    public void setStderr(String value) { stderr = value; }
    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer value) { exitCode = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { completedAt = value; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer value) { timeoutSeconds = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String value) { correlationId = value; }
}