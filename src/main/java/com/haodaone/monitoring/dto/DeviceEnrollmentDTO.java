package com.haodaone.monitoring.dto;

import com.haodaone.monitoring.entity.DeviceEnrollment;

import java.time.LocalDateTime;

public record DeviceEnrollmentDTO(
        Long id,
        String status,
        String deviceType,
        Long employeeId,
        String employeeName,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        String enrollmentUrl,
        ConnectedDevice device) {

    public static DeviceEnrollmentDTO from(DeviceEnrollment enrollment, String enrollmentUrl) {
        ConnectedDevice connectedDevice = enrollment.getDevice() == null ? null
                : new ConnectedDevice(
                        enrollment.getDevice().getDeviceName(),
                        enrollment.getDevice().getOperatingSystem(),
                        enrollment.getDevice().getAgentVersion(),
                        enrollment.getDevice().isOnline(),
                        enrollment.getDevice().getLastSeenAt());
        return new DeviceEnrollmentDTO(
                enrollment.getId(),
                enrollment.getStatus().name(),
                enrollment.getDeviceType(),
                enrollment.getEmployee().getId(),
                enrollment.getEmployee().getFullName(),
                enrollment.getCreatedAt(),
                enrollment.getExpiresAt(),
                enrollmentUrl,
                connectedDevice);
    }

    public record ConnectedDevice(
            String deviceName,
            String operatingSystem,
            String agentVersion,
            boolean online,
            LocalDateTime lastSeenAt) {}
}
