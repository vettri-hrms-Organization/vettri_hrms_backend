package com.haodaone.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Mirrors HaodaOne.Agent.Models.ActivityBatchModels.ActivityBatchRequest.
 *
 * ROOT CAUSE OF THE "200 OK but 0 rows inserted" bug: the agent's C# model
 * is serialized with System.Text.Json's real default (PascalCase -
 * "Device", "Sessions", "BatchSentAtUtc"), not camelCase. Jackson's default
 * property matching is case-sensitive, and Spring Boot leaves
 * FAIL_ON_UNKNOWN_PROPERTIES=false, so a PascalCase "Sessions" array simply
 * never matched the "sessions" field below - it silently deserialized to
 * this field's default (List.of()) instead of throwing, so validation
 * passed, the controller returned 200, and AgentIngestService's loop over
 * an empty list obviously wrote nothing. @JsonAlias below accepts both
 * casings explicitly; see config.JacksonConfig for the belt-and-suspenders
 * global case-insensitive fallback.
 */
public class ActivityBatchRequest {

    @Valid
    @NotNull
    @JsonAlias({"Device", "DEVICE"})
    private DeviceInfoPayload device;

    @Valid
    @JsonAlias({"Sessions", "SESSIONS", "ActivitySessions"})
    private List<ActivitySessionPayload> sessions = List.of();

    @JsonAlias({"BatchSentAtUtc", "BatchSentAtUTC"})
    private OffsetDateTime batchSentAtUtc;

    public DeviceInfoPayload getDevice() {
        return device;
    }

    public void setDevice(DeviceInfoPayload device) {
        this.device = device;
    }

    public List<ActivitySessionPayload> getSessions() {
        return sessions;
    }

    public void setSessions(List<ActivitySessionPayload> sessions) {
        this.sessions = sessions;
    }

    public OffsetDateTime getBatchSentAtUtc() {
        return batchSentAtUtc;
    }

    public void setBatchSentAtUtc(OffsetDateTime batchSentAtUtc) {
        this.batchSentAtUtc = batchSentAtUtc;
    }
}
