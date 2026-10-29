package com.haodaone.monitoring.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * A published Vettri Agent setup (V55). Platform-wide, not per company: every enrolled agent
 * polls GET /api/agent/update and installs the single active release when it is newer than its
 * own version. The agent refuses any download whose SHA-256 differs from {@link #sha256}.
 */
@Entity
@Table(name = "agent_release")
public class AgentRelease extends BaseEntity {

    /** Dotted numeric version, e.g. "2.1.0" ("version" itself is BaseEntity's optimistic lock). */
    @Column(name = "release_version", nullable = false, length = 32)
    private String releaseVersion;

    /** HTTPS URL of VettriAgentSetup.exe (e.g. an S3/CloudFront object). */
    @Column(name = "download_url", nullable = false, length = 1000)
    private String downloadUrl;

    /** Lower-case hex SHA-256 of the setup file. */
    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "release_notes", length = 2000)
    private String releaseNotes;

    /** At most one release is active; agents only ever see the active one. */
    @Column(nullable = false)
    private boolean active;

    public String getReleaseVersion() { return releaseVersion; }
    public void setReleaseVersion(String releaseVersion) { this.releaseVersion = releaseVersion; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getReleaseNotes() { return releaseNotes; }
    public void setReleaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
