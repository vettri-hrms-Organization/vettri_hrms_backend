package com.haodaone.monitoring.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.monitoring.dto.AgentReleaseDTO;
import com.haodaone.monitoring.entity.AgentRelease;
import com.haodaone.monitoring.repository.AgentReleaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Publishes Vettri Agent releases (platform super admins only) and answers the agent's
 * "is there a newer version?" poll. Agents verify the SHA-256 themselves before installing,
 * so a tampered or swapped download is never run.
 */
@Service
public class AgentReleaseService {

    private final AgentReleaseRepository repository;
    private final AuditLogService auditLogService;

    public AgentReleaseService(AgentReleaseRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<AgentReleaseDTO.Response> list() {
        return repository.findAllByDeletedFalseOrderByCreatedAtDesc().stream().map(AgentReleaseDTO.Response::from).toList();
    }

    @Transactional
    public AgentReleaseDTO.Response publish(AgentReleaseDTO.PublishRequest request) {
        String version = request.version().trim();
        if (repository.existsByReleaseVersionAndDeletedFalse(version)) {
            throw new BadRequestException("Version " + version + " is already published");
        }
        AgentRelease release = new AgentRelease();
        release.setReleaseVersion(version);
        release.setDownloadUrl(requireHttps(request.downloadUrl()));
        release.setSha256(request.sha256().trim().toLowerCase(Locale.ROOT));
        release.setReleaseNotes(blankToNull(request.releaseNotes()));
        AgentRelease saved = repository.save(release);
        auditLogService.log("AgentRelease", saved.getId(), "PUBLISH", "Published Vettri Agent " + version);
        if (!Boolean.FALSE.equals(request.activate())) {
            return activate(saved.getId());
        }
        return AgentReleaseDTO.Response.from(saved);
    }

    /** Makes one release the one agents upgrade to; also the way to roll back to an older build's record. */
    @Transactional
    public AgentReleaseDTO.Response activate(Long id) {
        AgentRelease target = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent release not found: " + id));
        for (AgentRelease release : repository.findAllByDeletedFalseOrderByCreatedAtDesc()) {
            boolean shouldBeActive = release.getId().equals(target.getId());
            if (release.isActive() != shouldBeActive) {
                release.setActive(shouldBeActive);
                repository.save(release);
            }
        }
        auditLogService.log("AgentRelease", id, "ACTIVATE", "Activated Vettri Agent " + target.getReleaseVersion());
        return AgentReleaseDTO.Response.from(target);
    }

    /** Stops all automatic updates (no active release). */
    @Transactional
    public void deactivateAll() {
        for (AgentRelease release : repository.findAllByDeletedFalseOrderByCreatedAtDesc()) {
            if (release.isActive()) {
                release.setActive(false);
                repository.save(release);
                auditLogService.log("AgentRelease", release.getId(), "DEACTIVATE", "Paused automatic agent updates");
            }
        }
    }

    /** Agents only move forward: an active release older than (or equal to) the agent is ignored. */
    @Transactional(readOnly = true)
    public AgentReleaseDTO.AgentUpdate updateFor(String agentVersion) {
        return repository.findFirstByActiveTrueAndDeletedFalseOrderByCreatedAtDesc()
                .filter(release -> compareVersions(release.getReleaseVersion(), agentVersion) > 0)
                .map(release -> new AgentReleaseDTO.AgentUpdate(true, release.getReleaseVersion(), release.getDownloadUrl(), release.getSha256()))
                .orElse(AgentReleaseDTO.AgentUpdate.none());
    }

    /** Numeric dotted comparison; missing parts count as 0, so "2.1" == "2.1.0.0". Unparseable counts as 0.0. */
    static int compareVersions(String left, String right) {
        int[] a = parse(left);
        int[] b = parse(right);
        for (int i = 0; i < 4; i++) {
            int diff = Integer.compare(a[i], b[i]);
            if (diff != 0) return diff;
        }
        return 0;
    }

    private static int[] parse(String version) {
        int[] parts = new int[4];
        if (version == null) return parts;
        String[] tokens = version.trim().split("\\.");
        for (int i = 0; i < Math.min(4, tokens.length); i++) {
            try {
                parts[i] = Integer.parseInt(tokens[i].replaceAll("[^0-9].*$", ""));
            } catch (NumberFormatException ex) {
                parts[i] = 0;
            }
        }
        return parts;
    }

    private static String requireHttps(String url) {
        String trimmed = url == null ? "" : url.trim();
        try {
            URI uri = URI.create(trimmed);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new BadRequestException("The download URL must be an https:// link");
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("The download URL is not valid");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
