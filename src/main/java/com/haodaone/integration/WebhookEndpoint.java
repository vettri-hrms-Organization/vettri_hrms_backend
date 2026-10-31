package com.haodaone.integration;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** An outbound webhook subscription (V68). Payloads are signed with HMAC-SHA256 using the endpoint's secret (stored encrypted). */
@Entity
@Table(name = "webhook_endpoint")
public class WebhookEndpoint extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "secret_enc", nullable = false, columnDefinition = "TEXT")
    private String secretEnc;

    @Column(name = "events_json", nullable = false, columnDefinition = "TEXT")
    private String eventsJson;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_status")
    private Integer lastStatus;

    @Column(name = "last_error", length = 300)
    private String lastError;

    @Column(name = "last_delivery_at")
    private LocalDateTime lastDeliveryAt;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSecretEnc() { return secretEnc; }
    public void setSecretEnc(String secretEnc) { this.secretEnc = secretEnc; }
    public String getEventsJson() { return eventsJson; }
    public void setEventsJson(String eventsJson) { this.eventsJson = eventsJson; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Integer getLastStatus() { return lastStatus; }
    public void setLastStatus(Integer lastStatus) { this.lastStatus = lastStatus; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getLastDeliveryAt() { return lastDeliveryAt; }
    public void setLastDeliveryAt(LocalDateTime lastDeliveryAt) { this.lastDeliveryAt = lastDeliveryAt; }
    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
}
