package com.haodaone.attendance.dto;

public class StartWorkRequest {
    private String workingMode; // OFFICE or WFH
    private String deviceId; // optional for OFFICE, required for WFH

    public String getWorkingMode() { return workingMode; }
    public void setWorkingMode(String workingMode) { this.workingMode = workingMode; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
}
