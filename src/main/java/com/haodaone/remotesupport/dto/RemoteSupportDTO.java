package com.haodaone.remotesupport.dto;
import com.haodaone.remotesupport.entity.*;
import java.time.LocalDateTime;
public final class RemoteSupportDTO {
    private RemoteSupportDTO() { }
    public record Response(Long jobId, Long deviceId, String operation, String status, String provisioningStage, String version, String ultraViewerId, String rustDeskId, String rustDeskPassword, String executablePath, Boolean running, Boolean unattendedEnabled, String errorCode, String errorMessage, String correlationId, String attemptId, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime startedAt, LocalDateTime completedAt) {
        public static Response from(RemoteSupportJob j) {
            return from(j, null);
        }

        public static Response from(RemoteSupportJob j, String rustDeskPassword) {
            String stage = j.getStatus() == null ? null : j.getStatus().name();
            return new Response(j.getId(), j.getDevice().getId(), j.getOperation().name(), stage, stage, j.getUltraViewerVersion(), j.getUltraViewerId(), j.getUltraViewerId(), rustDeskPassword, j.getExecutablePath(), j.getRunning(), j.getUnattendedEnabled(), j.getErrorCode(), j.getErrorMessage(), j.getCorrelationId(), j.getCorrelationId(), j.getCreatedAt(), j.getUpdatedAt(), j.getStartedAt(), j.getCompletedAt());
        }
    }
    public record AgentJob(Long id, String operation, String correlationId, String password, String rustDeskConfig, String installerPath, String installerSha256) { }
    public record AgentResult(String status, String version, String ultraViewerId, String rustDeskId, String executablePath, Boolean running, Boolean unattendedEnabled, String errorCode, String errorMessage, String provisioningState) { }
}