package com.haodaone.attendance.dto;

import com.haodaone.attendance.entity.Device;

public record AttendanceBiometricDeviceDTO(Long id, String name, String serialNumber, boolean online) {
    public static AttendanceBiometricDeviceDTO from(Device device) {
        return new AttendanceBiometricDeviceDTO(
                device.getId(), device.getDeviceName(), device.getSerialNumber(), device.isOnline());
    }
}
