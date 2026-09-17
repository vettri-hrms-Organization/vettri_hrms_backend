package com.haodaone.attendance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class OfficeLocationRequest {
    @NotBlank
    private String name;
    private String address;
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double latitude;
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double longitude;
    @NotNull @Min(10) @Max(5000)
    private Integer allowedRadiusMeters = 150;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Integer getAllowedRadiusMeters() { return allowedRadiusMeters; }
    public void setAllowedRadiusMeters(Integer allowedRadiusMeters) { this.allowedRadiusMeters = allowedRadiusMeters; }
}
