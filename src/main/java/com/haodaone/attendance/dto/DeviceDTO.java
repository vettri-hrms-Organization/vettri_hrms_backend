package com.haodaone.attendance.dto;

import com.haodaone.attendance.entity.Device;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class DeviceDTO {
    public record RegisterRequest(
            @NotBlank @Size(max = 50) String serialNumber,
            @NotBlank @Size(max = 100) String deviceName) { }

    private Long id;
    private String serialNumber;
    private String deviceName;
    private String lastIpAddress;
    private LocalDateTime lastSeenAt;
    private boolean online;

    public static DeviceDTO from(Device device) {
        DeviceDTO dto = new DeviceDTO();
        dto.id = device.getId();
        dto.serialNumber = device.getSerialNumber();
        dto.deviceName = device.getDeviceName();
        dto.lastIpAddress = device.getLastIpAddress();
        dto.lastSeenAt = device.getLastSeenAt();
        dto.online = device.isOnline();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getLastIpAddress() {
        return lastIpAddress;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public boolean isOnline() {
        return online;
    }
}
