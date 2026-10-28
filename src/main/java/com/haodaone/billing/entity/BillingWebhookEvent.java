package com.haodaone.billing.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Processed Razorpay webhook deliveries; the unique key makes redelivery a no-op. */
@Entity
@Table(name = "billing_webhook_event", uniqueConstraints =
        @UniqueConstraint(name = "uk_billing_webhook_event_key", columnNames = "event_key"))
public class BillingWebhookEvent extends BaseEntity {

    @Column(name = "event_key", nullable = false, length = 150)
    private String eventKey;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
