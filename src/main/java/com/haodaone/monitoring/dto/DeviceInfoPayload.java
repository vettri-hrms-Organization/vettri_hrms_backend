package com.haodaone.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Field-for-field mirror of HaodaOne.Agent.Models.DeviceInfo - sent as the
 * "device" object inside both HeartbeatRequest and ActivityBatchRequest.
 *
 * CORRECTION (was previously wrong and caused a production bug - see
 * ActivityBatchRequest's javadoc): System.Text.Json's actual default is
 * PropertyNamingPolicy = null, which PRESERVES the C# PascalCase property
 * names ("DeviceId", "Sessions", ...) - it does NOT default to camelCase.
 * The agent only emits camelCase where JsonNamingPolicy.CamelCase was
 * explicitly configured, and that was not applied consistently to every
 * request model. Every field below now carries @JsonAlias for its
 * PascalCase form so binding no longer depends on the agent's casing
 * being consistent, and config.JacksonConfig enables Jackson's
 * case-insensitive property matching globally as a second layer of
 * defense.
 */
public class DeviceInfoPayload {

    @JsonAlias({"DeviceId"})
    private String deviceId;
    @JsonAlias({"DeviceName"})
    private String deviceName;
    @JsonAlias({"Username", "UserName"})
    private String username;
    @JsonAlias({"DomainName"})
    private String domainName;
    @JsonAlias({"IpAddress", "IPAddress"})
    private String ipAddress;
    @JsonAlias({"OperatingSystem"})
    private String operatingSystem;
    @JsonAlias({"OsVersion", "OSVersion"})
    private String osVersion;
    @JsonAlias({"AgentVersion"})
    private String agentVersion;
    @JsonAlias({"MacAddress", "MACAddress"})
    private String macAddress;

    /** Windows hostname - the agent's Environment.MachineName, sent separately from deviceName (an admin-chosen label). */
    @JsonAlias({"Hostname", "HostName"})
    private String hostname;

    /** Agent-generated stable per-install GUID (HaodaOne.Agent's MachineGuidProvider), see MonitoredDevice.machineGuid. */
    @JsonAlias({"MachineGuid", "MachineGUID"})
    private String machineGuid;

    /** Optional agent telemetry. Employee assignment is owned by monitored_device and is not resolved from this field. */
    @JsonAlias({"EmployeeId"})
    private String employeeId;

    /** Sent for display/logging only; the backend never trusts this for employee identity. */
    @JsonAlias({"EmployeeName"})
    private String employeeName;

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public String getMachineGuid() {
        return machineGuid;
    }

    public void setMachineGuid(String machineGuid) {
        this.machineGuid = machineGuid;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }
}
