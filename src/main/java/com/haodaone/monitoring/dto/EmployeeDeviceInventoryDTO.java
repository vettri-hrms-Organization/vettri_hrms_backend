package com.haodaone.monitoring.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EmployeeDeviceInventoryDTO(
        String enrollmentStatus,
        String scanStatus,
        boolean deviceOnline,
        Long selectedDeviceId,
        List<EmployeeTrustedDeviceDTO> trustedDevices,
        UUID requestId,
        LocalDateTime requestedAt,
        LocalDateTime lastInventoryScanAt,
        String message,
        JsonNode inventory) {}
