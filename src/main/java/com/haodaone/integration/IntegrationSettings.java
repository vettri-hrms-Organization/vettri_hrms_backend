package com.haodaone.integration;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Slack / Microsoft Teams incoming-webhook URLs (encrypted) and which events are posted (V68). */
@Entity
@Table(name = "integration_settings")
public class IntegrationSettings extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "slack_webhook_enc", columnDefinition = "TEXT")
    private String slackWebhookEnc;

    @Column(name = "teams_webhook_enc", columnDefinition = "TEXT")
    private String teamsWebhookEnc;

    @Column(name = "events_json", nullable = false, columnDefinition = "TEXT")
    private String eventsJson;

    @Column(name = "last_error", length = 300)
    private String lastError;

    @Column(name = "last_posted_at")
    private LocalDateTime lastPostedAt;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getSlackWebhookEnc() { return slackWebhookEnc; }
    public void setSlackWebhookEnc(String slackWebhookEnc) { this.slackWebhookEnc = slackWebhookEnc; }
    public String getTeamsWebhookEnc() { return teamsWebhookEnc; }
    public void setTeamsWebhookEnc(String teamsWebhookEnc) { this.teamsWebhookEnc = teamsWebhookEnc; }
    public String getEventsJson() { return eventsJson; }
    public void setEventsJson(String eventsJson) { this.eventsJson = eventsJson; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getLastPostedAt() { return lastPostedAt; }
    public void setLastPostedAt(LocalDateTime lastPostedAt) { this.lastPostedAt = lastPostedAt; }
}
