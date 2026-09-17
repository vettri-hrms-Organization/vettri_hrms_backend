package com.haodaone.attendance.dto;

public class GeocodingResultDTO {
    private String displayName;
    private String address;
    private String city;
    private String state;
    private String country;
    private Double latitude;
    private Double longitude;

    public GeocodingResultDTO() {
    }

    public GeocodingResultDTO(String displayName, String address, String city, String state, String country, Double latitude, Double longitude) {
        this.displayName = displayName;
        this.address = address;
        this.city = city;
        this.state = state;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getDisplayName() { return displayName; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getCountry() { return country; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
}
