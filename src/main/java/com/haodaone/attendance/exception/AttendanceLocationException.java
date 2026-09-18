package com.haodaone.attendance.exception;

import com.haodaone.common.exception.BadRequestException;

public class AttendanceLocationException extends BadRequestException {
    private final String code;
    private final Double accuracyMeters;
    private final Double requiredAccuracyMeters;
    private final Double distanceMeters;
    private final Integer allowedRadiusMeters;
    private final String validationStage;
    private final String source;

    public AttendanceLocationException(String code, String message, Double accuracyMeters,
                                       Double requiredAccuracyMeters, Double distanceMeters,
                                       Integer allowedRadiusMeters, String validationStage, String source) {
        super(message);
        this.code = code;
        this.accuracyMeters = accuracyMeters;
        this.requiredAccuracyMeters = requiredAccuracyMeters;
        this.distanceMeters = distanceMeters;
        this.allowedRadiusMeters = allowedRadiusMeters;
        this.validationStage = validationStage;
        this.source = source;
    }

    public String getCode() { return code; }
    public Double getAccuracyMeters() { return accuracyMeters; }
    public Double getRequiredAccuracyMeters() { return requiredAccuracyMeters; }
    public Double getDistanceMeters() { return distanceMeters; }
    public Integer getAllowedRadiusMeters() { return allowedRadiusMeters; }
    public String getValidationStage() { return validationStage; }
    public String getSource() { return source; }
}