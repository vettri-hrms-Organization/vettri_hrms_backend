package com.haodaone.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * Mirrors HaodaOne.Agent.Models.HeartbeatModels.HeartbeatRequest
 * field-for-field. This endpoint "worked" in production, but only because
 * its casing happened to line up - it shares the exact same casing risk
 * ActivityBatchRequest was actually hit by (see that class's javadoc), so
 * every field here now carries the same @JsonAlias defense pre-emptively
 * rather than waiting for a future agent build to break this endpoint too.
 */
public class HeartbeatRequest {

    @Valid
    @NotNull
    @JsonAlias({"Device", "DEVICE"})
    private DeviceInfoPayload device;

    /** ONLINE | IDLE | LOCKED - free text on purpose so a future agent version can add a status without a backend release. */
    @JsonAlias({"Status"})
    private String status = "ONLINE";

    @JsonAlias({"CurrentUser"})
    private String currentUser;

    @JsonAlias({"LastSeenUtc", "LastSeenUTC"})
    private OffsetDateTime lastSeenUtc;

    @JsonAlias({"CurrentApplication"})
    private String currentApplication;

    @JsonAlias({"CurrentWindowTitle"})
    private String currentWindowTitle;

    @JsonAlias({"AgentVersion"})
    private String agentVersion;

    public DeviceInfoPayload getDevice() {
        return device;
    }

    public void setDevice(DeviceInfoPayload device) {
        this.device = device;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(String currentUser) {
        this.currentUser = currentUser;
    }

    public OffsetDateTime getLastSeenUtc() {
        return lastSeenUtc;
    }

    public void setLastSeenUtc(OffsetDateTime lastSeenUtc) {
        this.lastSeenUtc = lastSeenUtc;
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

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }
}
