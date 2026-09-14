package com.haodaone.monitoring.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Server-side mirror of HaodaOne.Agent.Models.ActivitySession - one "app in
 * focus" session (e.g. chrome.exe from 09:00 to 09:30). Persisted exactly
 * once per agent-generated sessionId; ActivitySessionRepository#
 * existsBySessionId is what lets AgentIngestService safely accept a
 * replayed/retried batch (see ApiClientService's local-cache-on-failure
 * design in the agent) without creating duplicate rows.
 */
@Entity
@Table(name = "activity_session", uniqueConstraints = @UniqueConstraint(columnNames = "session_id"))
public class ActivitySession extends BaseEntity {

    /** Client-generated GUID (Agent's ActivitySession.SessionId) - the de-duplication key. */
    @Column(name = "session_id", nullable = false, unique = true, length = 40)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private MonitoredDevice device;

    /** Same best-effort resolution as MonitoredDevice.employee - denormalized here too so per-employee usage reports don't need a join through device for every row. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /** Tenant/company this activity session belongs to (nullable for legacy rows until migration is applied). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private com.haodaone.company.entity.Company company;

    @Column(name = "process_name", length = 255)
    private String processName;

    @Column(name = "application_name", length = 255)
    private String applicationName;

    @Column(name = "window_title", length = 500)
    private String windowTitle;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "is_idle_session", nullable = false)
    private boolean idleSession;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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

    public com.haodaone.company.entity.Company getCompany() {
        return company;
    }

    public void setCompany(com.haodaone.company.entity.Company company) {
        this.company = company;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getWindowTitle() {
        return windowTitle;
    }

    public void setWindowTitle(String windowTitle) {
        this.windowTitle = windowTitle;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public boolean isIdleSession() {
        return idleSession;
    }

    public void setIdleSession(boolean idleSession) {
        this.idleSession = idleSession;
    }
}
