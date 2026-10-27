package com.haodaone.monitoring.dto;

public record EmployeeTrustedDeviceDTO(
        Long deviceId,
        String deviceName,
        boolean online,
        String inventoryStatus) {}
