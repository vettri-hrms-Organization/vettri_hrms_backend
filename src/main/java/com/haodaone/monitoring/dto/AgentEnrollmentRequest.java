package com.haodaone.monitoring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AgentEnrollmentRequest {
    @NotBlank
    private String enrollmentToken;

    @Valid
    @NotNull
    private DeviceInfoPayload device;

    public String getEnrollmentToken() { return enrollmentToken; }
    public void setEnrollmentToken(String enrollmentToken) { this.enrollmentToken = enrollmentToken; }
    public DeviceInfoPayload getDevice() { return device; }
    public void setDevice(DeviceInfoPayload device) { this.device = device; }
}
