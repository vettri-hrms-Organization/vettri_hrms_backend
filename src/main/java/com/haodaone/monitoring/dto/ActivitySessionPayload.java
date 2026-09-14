package com.haodaone.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * Field-for-field mirror of HaodaOne.Agent.Models.ActivitySession - one
 * entry in ActivityBatchRequest.sessions. @JsonAlias on every field accepts
 * the agent's actual PascalCase wire format (System.Text.Json's true
 * default) in addition to camelCase - see ActivityBatchRequest's javadoc
 * for the full root-cause writeup.
 */
public class ActivitySessionPayload {

    @NotBlank
    @JsonAlias({"SessionId"})
    private String sessionId;

    @JsonAlias({"DeviceId"})
    private String deviceId;

    @JsonAlias({"Username", "UserName"})
    private String username;

    @JsonAlias({"ProcessName"})
    private String processName;

    @JsonAlias({"ApplicationName"})
    private String applicationName;

    @JsonAlias({"WindowTitle"})
    private String windowTitle;

    @NotNull
    @JsonAlias({"StartTimeUtc", "StartTimeUTC"})
    private OffsetDateTime startTimeUtc;

    @JsonAlias({"EndTimeUtc", "EndTimeUTC"})
    private OffsetDateTime endTimeUtc;

    @JsonAlias({"DurationSeconds"})
    private int durationSeconds;

    @JsonAlias({"IsIdleSession", "IdleSession"})
    private boolean isIdleSession;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public OffsetDateTime getStartTimeUtc() {
        return startTimeUtc;
    }

    public void setStartTimeUtc(OffsetDateTime startTimeUtc) {
        this.startTimeUtc = startTimeUtc;
    }

    public OffsetDateTime getEndTimeUtc() {
        return endTimeUtc;
    }

    public void setEndTimeUtc(OffsetDateTime endTimeUtc) {
        this.endTimeUtc = endTimeUtc;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public boolean isIdleSession() {
        return isIdleSession;
    }

    public void setIdleSession(boolean idleSession) {
        isIdleSession = idleSession;
    }
}
