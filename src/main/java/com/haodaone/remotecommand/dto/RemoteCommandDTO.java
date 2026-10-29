package com.haodaone.remotecommand.dto;

import com.haodaone.remotecommand.entity.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class RemoteCommandDTO {
    private RemoteCommandDTO() { }
    /**
     * shellType "ACTION" with an approved {@link RemoteAction} key in {@code command} (the normal case),
     * or "POWERSHELL"/"CMD" with a raw script - which needs REMOTE_SCRIPT_RUN and {@code confirmPassword}.
     */
    public record CreateRequest(@NotBlank @Size(max = 10000) String command, @NotBlank String shellType,
                                @Size(max = 200) String confirmPassword) {
        public CreateRequest(String command, String shellType) { this(command, shellType, null); }
    }
    public record AgentJob(Long id, String command, String shellType, Integer timeoutSeconds, String correlationId) {
        public static AgentJob from(RemoteCommandJob job) { return new AgentJob(job.getId(), job.getCommand(), job.getShellType(), job.getTimeoutSeconds(), job.getCorrelationId()); }
    }
    public record AgentResult(String status, String stdout, String stderr, Integer exitCode, String errorMessage) { }
    public record ActionInfo(String key, String label, String description, String category) {
        public static ActionInfo from(RemoteAction action) { return new ActionInfo(action.name(), action.label(), action.description(), action.category()); }
    }
    public record Response(Long id, Long deviceId, String deviceName, String command, String shellType, String status,
                           String stdout, String stderr, Integer exitCode, LocalDateTime createdAt, LocalDateTime startedAt,
                           LocalDateTime completedAt, String errorMessage, String correlationId, String actionLabel) {
        public static Response from(RemoteCommandJob job) {
            String actionLabel = "ACTION".equals(job.getShellType())
                    ? RemoteAction.fromKey(job.getCommand()).map(RemoteAction::label).orElse(job.getCommand())
                    : null;
            return new Response(job.getId(), job.getDevice().getId(), job.getDevice().getDeviceName(), job.getCommand(), job.getShellType(),
                    job.getStatus().name(), job.getStdout(), job.getStderr(), job.getExitCode(), job.getCreatedAt(), job.getStartedAt(),
                    job.getCompletedAt(), job.getErrorMessage(), job.getCorrelationId(), actionLabel);
        }
    }
}
