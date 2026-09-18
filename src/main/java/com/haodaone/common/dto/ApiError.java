package com.haodaone.common.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Consistent error shape returned by every /api/** endpoint on failure. */
public class ApiError {

    private LocalDateTime timestamp = LocalDateTime.now();
    private int status;
    private String error;
    private String message;
    private String path;
    private List<String> details;
    private String code;
    private Double accuracyMeters;
    private Double requiredAccuracyMeters;
    private Double distanceMeters;
    private Integer allowedRadiusMeters;
    private String validationStage;
    private String source;

    public ApiError() {
    }

    public ApiError(int status, String error, String message, String path) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getDetails() {
        return details;
    }

    public void setDetails(List<String> details) {
        this.details = details;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Double getAccuracyMeters() { return accuracyMeters; }
    public void setAccuracyMeters(Double accuracyMeters) { this.accuracyMeters = accuracyMeters; }
    public Double getRequiredAccuracyMeters() { return requiredAccuracyMeters; }
    public void setRequiredAccuracyMeters(Double requiredAccuracyMeters) { this.requiredAccuracyMeters = requiredAccuracyMeters; }
    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }
    public Integer getAllowedRadiusMeters() { return allowedRadiusMeters; }
    public void setAllowedRadiusMeters(Integer allowedRadiusMeters) { this.allowedRadiusMeters = allowedRadiusMeters; }
    public String getValidationStage() { return validationStage; }
    public void setValidationStage(String validationStage) { this.validationStage = validationStage; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
