package com.haodaone.attendance.dto;

import com.haodaone.attendance.entity.OfficeLocation;

public class OfficeLocationDTO {
    private Long id;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private Integer allowedRadiusMeters;
    private boolean active;

    public static OfficeLocationDTO from(OfficeLocation location) {
        OfficeLocationDTO dto = new OfficeLocationDTO();
        dto.id = location.getId();
        dto.name = location.getName();
        dto.address = location.getAddress();
        dto.latitude = location.getLatitude();
        dto.longitude = location.getLongitude();
        dto.allowedRadiusMeters = location.getAllowedRadiusMeters();
        dto.active = location.isActive();
        return dto;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Integer getAllowedRadiusMeters() { return allowedRadiusMeters; }
    public boolean isActive() { return active; }
}
