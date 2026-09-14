package com.haodaone.monitoring.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A Windows workstation running the HaodaOne.Agent service (see
 * HaodaOne.Agent/Services/ApiClientService.cs). Unlike attendance.entity.
 * Device (a biometric device that self-registers on first ADMS handshake),
 * a monitored device must be enrolled by an admin first - see
 * DeviceEnrollmentService#enroll - because enrollment is what mints the
 * per-device bearer token the agent's installer bakes into
 * appsettings.Production.json (DPAPI-encrypted, see Installer/
 * provision-config.ps1). The agent never sees agentTokenHash; only the raw
 * token, once, at enrollment time - same pattern as auth.entity.RefreshToken.
 */
@Entity
@Table(name = "monitored_device", uniqueConstraints = {
        @UniqueConstraint(columnNames = "device_id"),
        @UniqueConstraint(columnNames = "agent_token_hash")
})
public class MonitoredDevice extends BaseEntity {

    /** Stable hardware-derived id the agent generates locally (DeviceInfoService.GetOrCreateDeviceId) - not our PK, but what the agent identifies itself by on every call. */
    @Column(name = "device_id", nullable = false, unique = true, length = 100)
    private String deviceId;

    @Column(name = "device_name", nullable = false, length = 150)
    private String deviceName;

    @Column(name = "windows_username", length = 150)
    private String windowsUsername;

    @Column(name = "domain_name", length = 150)
    private String domainName;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "mac_address", length = 50)
    private String macAddress;

    /** Windows hostname reported by the agent/admin - distinct from deviceName (an admin-chosen label) since the two can legitimately differ. */
    @Column(name = "hostname", length = 150)
    private String hostname;

    /** Hardware serial number, entered at assignment time or reported by the agent if it can read it - required by the Device Assignment module. */
    @Column(name = "serial_number", length = 150)
    private String serialNumber;

    /** Date this device was assigned to its current employee - set on enroll/assign, not touched by heartbeats. */
    @Column(name = "assigned_date")
    private java.time.LocalDate assignedDate;

    /** Agent-generated stable per-install GUID (HaodaOne.Agent's MachineGuidProvider) - sent on every heartbeat/activity batch, used as a secondary identity check alongside deviceId. */
    @Column(name = "machine_guid", length = 100)
    private String machineGuid;

    @Column(name = "operating_system", length = 100)
    private String operatingSystem;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "agent_version", length = 50)
    private String agentVersion;

    /** SHA-256 hash of the enrollment token; see AuthService#hash for the same pattern applied to refresh tokens. */
    @Column(name = "agent_token_hash", nullable = false, unique = true, length = 200)
    private String agentTokenHash;

    /** HRMS assignment selected by an administrator; heartbeat telemetry never changes this link. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /** Tenant/company this device belongs to (nullable for legacy rows until migration is applied). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private com.haodaone.company.entity.Company company;

    /** ONLINE / IDLE / LOCKED / OFFLINE - OFFLINE is derived (see isOnline), the rest come straight from the agent's HeartbeatRequest.Status. */
    @Column(nullable = false, length = 20)
    private String status = "OFFLINE";

    @Column(name = "current_application", length = 255)
    private String currentApplication;

    @Column(name = "current_window_title", length = 500)
    private String currentWindowTitle;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "last_ip_address", length = 50)
    private String lastIpAddress;

    /** Enrolled but not yet actively monitored, or an admin has paused/decommissioned it - toggled independently of BaseEntity.deleted, which is reserved for records removed entirely. */
    @Column(nullable = false)
    private boolean active = true;

    /** Server-driven override echoed back in the next HeartbeatResponse.Directive - see RemoteAgentDirective in the agent. Null means "use the agent's local default (60s)". */
    @Column(name = "heartbeat_interval_seconds")
    private Integer heartbeatIntervalSeconds;

    /** Mirrors RemoteAgentDirective.PauseMonitoring - lets an admin pause activity tracking on a device without uninstalling the agent (e.g. a shared kiosk machine). */
    @Column(name = "monitoring_paused", nullable = false)
    private boolean monitoringPaused = false;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getWindowsUsername() {
        return windowsUsername;
    }

    public void setWindowsUsername(String windowsUsername) {
        this.windowsUsername = windowsUsername;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public java.time.LocalDate getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(java.time.LocalDate assignedDate) {
        this.assignedDate = assignedDate;
    }

    public String getMachineGuid() {
        return machineGuid;
    }

    public void setMachineGuid(String machineGuid) {
        this.machineGuid = machineGuid;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public void setOperatingSystem(String operatingSystem) {
        this.operatingSystem = operatingSystem;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getAgentTokenHash() {
        return agentTokenHash;
    }

    public void setAgentTokenHash(String agentTokenHash) {
        this.agentTokenHash = agentTokenHash;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentApplication() {
        return currentApplication;
    }

    public void setCurrentApplication(String currentApplication) {
        this.currentApplication = currentApplication;
    }

    public String getCurrentWindowTitle() {
        return currentWindowTitle;
    }

    public void setCurrentWindowTitle(String currentWindowTitle) {
        this.currentWindowTitle = currentWindowTitle;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getLastIpAddress() {
        return lastIpAddress;
    }

    public void setLastIpAddress(String lastIpAddress) {
        this.lastIpAddress = lastIpAddress;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public void setHeartbeatIntervalSeconds(Integer heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    public boolean isMonitoringPaused() {
        return monitoringPaused;
    }

    public void setMonitoringPaused(boolean monitoringPaused) {
        this.monitoringPaused = monitoringPaused;
    }

    /** A device that hasn't heartbeat in 3+ missed intervals (default 60s each => 3 min) is shown offline in the dashboard, same threshold style as attendance.entity.Device#isOnline. */
    public boolean isOnline() {
        return active && lastSeenAt != null && lastSeenAt.isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(3));
    }
}
