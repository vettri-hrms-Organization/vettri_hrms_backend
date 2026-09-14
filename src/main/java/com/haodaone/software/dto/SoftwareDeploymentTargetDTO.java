package com.haodaone.software.dto;

import com.haodaone.software.entity.SoftwareDeploymentTarget;
import com.haodaone.software.entity.SoftwareDeploymentStatus;

import java.time.LocalDateTime;
import java.util.List;

public class SoftwareDeploymentTargetDTO {
    private Long id;
    private Long deviceId;
    private String deviceName;
    private String employeeName;
    private SoftwareDeploymentStatus status;
    private String installedVersion;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private List<StageEvent> stages;

    public static SoftwareDeploymentTargetDTO from(SoftwareDeploymentTarget target) {
        SoftwareDeploymentTargetDTO dto = new SoftwareDeploymentTargetDTO();
        dto.id = target.getId();
        dto.deviceId = target.getDevice().getId();
        dto.deviceName = target.getDevice().getDeviceName();
        dto.employeeName = target.getEmployee() == null ? null : target.getEmployee().getFullName();
        dto.status = target.getStatus();
        dto.installedVersion = target.getInstalledVersion();
        dto.errorMessage = target.getErrorMessage();
        dto.startedAt = target.getStartedAt();
        dto.completedAt = target.getCompletedAt();
        return dto;
    }

    public static class StageEvent {
        private final SoftwareDeploymentStatus status;
        private final String errorCode;
        private final String errorMessage;
        private final LocalDateTime occurredAt;

        public StageEvent(SoftwareDeploymentStatus status, String errorCode, String errorMessage, LocalDateTime occurredAt) {
            this.status = status;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.occurredAt = occurredAt;
        }

        public SoftwareDeploymentStatus getStatus() { return status; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public LocalDateTime getOccurredAt() { return occurredAt; }
    }

    public Long getId() { return id; }
    public Long getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getEmployeeName() { return employeeName; }
    public SoftwareDeploymentStatus getStatus() { return status; }
    public String getInstalledVersion() { return installedVersion; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public List<StageEvent> getStages() { return stages; }
    public void setStages(List<StageEvent> stages) { this.stages = stages; }
}