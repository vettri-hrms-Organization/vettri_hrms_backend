package com.haodaone.monitoring.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

public class InventoryScanRequestDTO {
    private boolean consent;
    @Positive
    private Long deviceId;

    @AssertTrue(message = "Consent is required before requesting a hardware inventory scan")
    public boolean isConsent() { return consent; }
    public void setConsent(boolean consent) { this.consent = consent; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
}
