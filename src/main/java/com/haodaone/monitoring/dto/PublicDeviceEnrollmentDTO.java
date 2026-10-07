package com.haodaone.monitoring.dto;

import com.haodaone.monitoring.entity.DeviceEnrollment;

import java.time.LocalDateTime;

public record PublicDeviceEnrollmentDTO(
        String status,
        String companyName,
        String employeeName,
        String deviceType,
        LocalDateTime expiresAt,
        ConnectedDevice device) {

    public static PublicDeviceEnrollmentDTO from(DeviceEnrollment enrollment) {
        ConnectedDevice device = enrollment.getDevice() == null ? null
                : new ConnectedDevice(
                        enrollment.getDevice().getDeviceName(),
                        enrollment.getDevice().getOperatingSystem(),
                        enrollment.getDevice().getAgentVersion(),
                        enrollment.getDevice().isOnline(),
                        enrollment.getDevice().getLastSeenAt());
        return new PublicDeviceEnrollmentDTO(
                enrollment.getStatus().name(),
                enrollment.getCompany().getName(),
                enrollment.getEmployee().getFullName(),
                enrollment.getDeviceType(),
                enrollment.getExpiresAt(),
                device);
    }

    public record ConnectedDevice(
            String deviceName,
            String operatingSystem,
            String agentVersion,
            boolean online,
            LocalDateTime lastSeenAt) {}
}
