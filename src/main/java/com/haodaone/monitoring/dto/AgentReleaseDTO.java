package com.haodaone.monitoring.dto;

import com.haodaone.monitoring.entity.AgentRelease;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AgentReleaseDTO {
    private AgentReleaseDTO() { }

    public record PublishRequest(
            @NotBlank @Pattern(regexp = "\\d{1,4}(\\.\\d{1,5}){1,3}", message = "Version must look like 2.1.0") String version,
            @NotBlank @Size(max = 1000) String downloadUrl,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}", message = "SHA-256 must be 64 hex characters") String sha256,
            @Size(max = 2000) String releaseNotes,
            Boolean activate) { }

    public record Response(Long id, String version, String downloadUrl, String sha256, String releaseNotes,
                           boolean active, LocalDateTime createdAt, String createdBy) {
        public static Response from(AgentRelease release) {
            return new Response(release.getId(), release.getReleaseVersion(), release.getDownloadUrl(), release.getSha256(),
                    release.getReleaseNotes(), release.isActive(), release.getCreatedAt(), release.getCreatedBy());
        }
    }

    /** What GET /api/agent/update returns; {@code updateAvailable=false} means "stay on your version". */
    public record AgentUpdate(boolean updateAvailable, String version, String downloadUrl, String sha256) {
        public static AgentUpdate none() { return new AgentUpdate(false, null, null, null); }
    }
}
