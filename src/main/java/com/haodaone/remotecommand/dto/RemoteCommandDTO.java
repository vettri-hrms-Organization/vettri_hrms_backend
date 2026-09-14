package com.haodaone.remotecommand.dto;

import com.haodaone.remotecommand.entity.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class RemoteCommandDTO {
    private RemoteCommandDTO() { }
    public record CreateRequest(@NotBlank @Size(max = 10000) String command, @NotBlank String shellType) { }
    public record AgentJob(Long id, String command, String shellType, Integer timeoutSeconds, String correlationId) {
        public static AgentJob from(RemoteCommandJob job) { return new AgentJob(job.getId(), job.getCommand(), job.getShellType(), job.getTimeoutSeconds(), job.getCorrelationId()); }
    }
    public record AgentResult(String status, String stdout, String stderr, Integer exitCode, String errorMessage) { }
    public record Response(Long id, Long deviceId, String deviceName, String command, String shellType, String status,
                           String stdout, String stderr, Integer exitCode, LocalDateTime createdAt, LocalDateTime startedAt,
                           LocalDateTime completedAt, String errorMessage, String correlationId) {
        public static Response from(RemoteCommandJob job) {
            return new Response(job.getId(), job.getDevice().getId(), job.getDevice().getDeviceName(), job.getCommand(), job.getShellType(),
                    job.getStatus().name(), job.getStdout(), job.getStderr(), job.getExitCode(), job.getCreatedAt(), job.getStartedAt(),
                    job.getCompletedAt(), job.getErrorMessage(), job.getCorrelationId());
        }
    }
}