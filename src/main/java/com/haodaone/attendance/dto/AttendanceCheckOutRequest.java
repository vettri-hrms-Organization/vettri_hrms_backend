package com.haodaone.attendance.dto;

public class AttendanceCheckOutRequest {
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private String source;
    private String deviceId;

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
}
