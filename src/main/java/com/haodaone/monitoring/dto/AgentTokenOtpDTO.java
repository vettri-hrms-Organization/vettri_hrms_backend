package com.haodaone.monitoring.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public final class AgentTokenOtpDTO {
    private AgentTokenOtpDTO() { }

    public record RequestResponse(String message, LocalDateTime expiresAt) { }

    public record ConfirmRequest(@NotBlank String otp, String reason) { }

    public record ConfirmResponse(MonitoredDeviceDTO device, String newToken) { }

    public record RotationHistoryResponse(Long id, String reason, String ipAddress, LocalDateTime createdAt) { }
}