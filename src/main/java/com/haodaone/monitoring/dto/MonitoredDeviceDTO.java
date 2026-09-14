package com.haodaone.monitoring.dto;

import com.haodaone.monitoring.entity.MonitoredDevice;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class MonitoredDeviceDTO {

    private Long id;
    private String deviceId;
    private String deviceName;
    private String windowsUsername;
    private String domainName;
    private String ipAddress;
    private String macAddress;
    private String hostname;
    private String serialNumber;
    private LocalDate assignedDate;
    private String machineGuid;
    private String operatingSystem;
    private String osVersion;
    private String agentVersion;
    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private String departmentName;
    private String designationTitle;
    private String status;
    private String currentApplication;
    private String currentWindowTitle;
    private LocalDateTime lastSeenAt;
    private boolean online;
    private boolean active;
    private boolean monitoringPaused;

    public static MonitoredDeviceDTO from(MonitoredDevice d) {
        MonitoredDeviceDTO dto = new MonitoredDeviceDTO();
        dto.id = d.getId();
        dto.deviceId = d.getDeviceId();
        dto.deviceName = d.getDeviceName();
        dto.windowsUsername = d.getWindowsUsername();
        dto.domainName = d.getDomainName();
        dto.ipAddress = d.getIpAddress();
        dto.macAddress = d.getMacAddress();
        dto.hostname = d.getHostname();
        dto.serialNumber = d.getSerialNumber();
        dto.assignedDate = d.getAssignedDate();
        dto.machineGuid = d.getMachineGuid();
        dto.operatingSystem = d.getOperatingSystem();
        dto.osVersion = d.getOsVersion();
        dto.agentVersion = d.getAgentVersion();
        dto.status = d.getStatus();
        dto.currentApplication = d.getCurrentApplication();
        dto.currentWindowTitle = d.getCurrentWindowTitle();
        dto.lastSeenAt = d.getLastSeenAt();
        dto.online = d.isOnline();
        dto.active = d.isActive();
        dto.monitoringPaused = d.isMonitoringPaused();
        if (d.getEmployee() != null) {
            dto.employeeId = d.getEmployee().getId();
            dto.employeeCode = d.getEmployee().getEmployeeCode();
            dto.employeeName = d.getEmployee().getFullName();
            dto.departmentName = d.getEmployee().getDepartment() != null ? d.getEmployee().getDepartment().getName() : null;
            dto.designationTitle = d.getEmployee().getDesignation() != null ? d.getEmployee().getDesignation().getTitle() : null;
        }
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getWindowsUsername() {
        return windowsUsername;
    }

    public String getDomainName() {
        return domainName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getHostname() {
        return hostname;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }

    public String getMachineGuid() {
        return machineGuid;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getDesignationTitle() {
        return designationTitle;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getStatus() {
        return status;
    }

    public String getCurrentApplication() {
        return currentApplication;
    }

    public String getCurrentWindowTitle() {
        return currentWindowTitle;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public boolean isOnline() {
        return online;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isMonitoringPaused() {
        return monitoringPaused;
    }

    /** Admin request to enroll a new device ahead of installing the agent on it. */
    public static class EnrollRequest {
        @NotBlank(message = "Device name is required")
        private String deviceName;

        private Long employeeId;
        private String hostname;
        private String serialNumber;
        private String macAddress;
        private LocalDate assignedDate;

        public String getDeviceName() {
            return deviceName;
        }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
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

        public String getMacAddress() {
            return macAddress;
        }

        public void setMacAddress(String macAddress) {
            this.macAddress = macAddress;
        }

        public LocalDate getAssignedDate() {
            return assignedDate;
        }

        public void setAssignedDate(LocalDate assignedDate) {
            this.assignedDate = assignedDate;
        }
    }

    /**
     * Admin request to (re)assign an already-enrolled device to an employee,
     * or edit its Device Assignment fields (hostname/serial/MAC/assigned
     * date/active status) - separate from EnrollRequest since enrollment
     * also mints a token and this never does.
     */
    public static class AssignmentRequest {
        private Long employeeId;
        private String hostname;
        private String serialNumber;
        private String macAddress;
        private LocalDate assignedDate;
        private Boolean active;

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
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

        public String getMacAddress() {
            return macAddress;
        }

        public void setMacAddress(String macAddress) {
            this.macAddress = macAddress;
        }

        public LocalDate getAssignedDate() {
            return assignedDate;
        }

        public void setAssignedDate(LocalDate assignedDate) {
            this.assignedDate = assignedDate;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }
    }

    /**
     * Returned exactly once, at enrollment. rawToken is the value an admin
     * pastes into the MSI installer (provision-config.ps1 -AgentToken) -
     * it is never retrievable again afterwards, same as a refresh token's
     * raw value never being persisted (see auth.entity.RefreshToken).
     */
    public static class EnrollResponse {
        private final MonitoredDeviceDTO device;
        private final String rawToken;

        public EnrollResponse(MonitoredDeviceDTO device, String rawToken) {
            this.device = device;
            this.rawToken = rawToken;
        }

        public MonitoredDeviceDTO getDevice() {
            return device;
        }

        public String getRawToken() {
            return rawToken;
        }
    }

    /** Admin request to push a directive (interval change / pause) applied on the device's next heartbeat. */
    public static class DirectiveRequest {
        private Integer heartbeatIntervalSeconds;
        private Boolean monitoringPaused;

        public Integer getHeartbeatIntervalSeconds() {
            return heartbeatIntervalSeconds;
        }

        public void setHeartbeatIntervalSeconds(Integer heartbeatIntervalSeconds) {
            this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        }

        public Boolean getMonitoringPaused() {
            return monitoringPaused;
        }

        public void setMonitoringPaused(Boolean monitoringPaused) {
            this.monitoringPaused = monitoringPaused;
        }
    }
}
