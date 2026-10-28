package com.haodaone.billing.repository;

import com.haodaone.billing.entity.BillingWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingWebhookEventRepository extends JpaRepository<BillingWebhookEvent, Long> {
    boolean existsByEventKey(String eventKey);
}
